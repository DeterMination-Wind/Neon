package bektools;

import arc.Core;
import arc.Events;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureAtlas;
import arc.graphics.g2d.TextureAtlas.AtlasRegion;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.GameState.State;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.mod.DataImagePacker;
import mindustry.mod.DataManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public final class DataImagePackerCompatTest{
    private static final Field managerPacker = field(DataManager.class, "packer");
    private static final Field patchAtlas = field(DataImagePacker.class, "patchAtlas");

    private DataImagePackerCompatTest(){
    }

    public static void main(String[] args) throws Exception{
        TextureAtlas originalAtlas = Core.atlas;
        GameState originalState = Vars.state;
        try{
            testTransferAndSafeUnload();
            testLifecycleHooks();
            testMultipleClassLoaders();
        }finally{
            Events.clear();
            Core.atlas = originalAtlas;
            Vars.state = originalState;
        }
        System.out.println("DataImagePackerCompatTest passed");
    }

    private static void testTransferAndSafeUnload() throws Exception{
        Core.atlas = new TextureAtlas();
        DataManager manager = new DataManager();
        DataImagePacker original = packer(manager);

        TextureAtlas patch = new TextureAtlas();
        MockTexture firstTexture = new MockTexture();
        MockTexture secondTexture = new MockTexture();
        AtlasRegion firstRegion = patch.addRegion("compat-first", firstTexture, 0, 0, 1, 1);
        AtlasRegion secondRegion = patch.addRegion("compat-second", secondTexture, 0, 0, 1, 1);
        patchAtlas.set(original, patch);

        Core.atlas.getTextures().addAll(patch.getTextures());
        Core.atlas.getRegionMap().putAll(patch.getRegionMap());
        AtlasRegion replacementRegion = new AtlasRegion(secondTexture, 0, 0, 1, 1);
        replacementRegion.name = secondRegion.name;
        Core.atlas.getRegionMap().put(secondRegion.name, replacementRegion);

        check(DataImagePackerCompat.install(manager), "safe packer was not installed");
        DataImagePacker safe = packer(manager);
        check(safe != original, "unsafe packer was retained");
        check(patchAtlas.get(original) == null, "old packer retained the patch atlas");
        check(patchAtlas.get(safe) == patch, "patch atlas was not transferred");

        safe.unload();
        check(!Core.atlas.getTextures().contains(firstTexture), "first texture remained in Core.atlas");
        check(!Core.atlas.getTextures().contains(secondTexture), "second texture remained in Core.atlas");
        check(Core.atlas.getRegionMap().get(firstRegion.name) == null, "owned region mapping remained in Core.atlas");
        check(Core.atlas.getRegionMap().get(secondRegion.name) == replacementRegion,
            "a newer region mapping with the same name was removed");
        check(patchAtlas.get(safe) == null, "safe packer retained the disposed patch atlas");
        check(firstTexture.disposeCount == 1 && secondTexture.disposeCount == 1, "patch textures were not disposed once");

        safe.unload();
        check(firstTexture.disposeCount == 1 && secondTexture.disposeCount == 1, "second unload was not a no-op");
    }

    private static void testLifecycleHooks() throws Exception{
        Events.clear();
        Vars.state = new GameState();
        DataImagePackerCompat.installHooks();
        check(DataImagePackerCompat.isInstalled(Vars.state.data), "initial GameState was not protected");

        DataImagePacker initialSafe = packer(Vars.state.data);
        DataImagePackerCompat.installHooks();
        check(packer(Vars.state.data) == initialSafe, "repeat installation replaced an existing safe packer");

        managerPacker.set(Vars.state.data, new DataImagePacker());
        Events.fire(new ClientLoadEvent());
        check(DataImagePackerCompat.isInstalled(Vars.state.data), "ClientLoadEvent did not repair the packer");

        managerPacker.set(Vars.state.data, new DataImagePacker());
        Events.fire(new ResetEvent());
        check(DataImagePackerCompat.isInstalled(Vars.state.data), "ResetEvent did not protect the old GameState");

        Vars.state = new GameState();
        check(!DataImagePackerCompat.isInstalled(Vars.state.data), "a fresh GameState unexpectedly reused the safe packer");
        Events.fire(new StateChangeEvent(State.playing, State.menu));
        check(DataImagePackerCompat.isInstalled(Vars.state.data), "StateChangeEvent did not protect the new GameState");
    }

    private static void testMultipleClassLoaders() throws Exception{
        DataManager manager = new DataManager();
        check(DataImagePackerCompat.install(manager), "primary class loader did not install the safe packer");
        DataImagePacker primarySafe = packer(manager);

        URL classes = DataImagePackerCompat.class.getProtectionDomain().getCodeSource().getLocation();
        try(CompatClassLoader loader = new CompatClassLoader(classes, DataImagePackerCompatTest.class.getClassLoader())){
            Class<?> compat = Class.forName("bektools.DataImagePackerCompat", true, loader);
            Method install = compat.getDeclaredMethod("install", DataManager.class);
            install.setAccessible(true);

            check(Boolean.TRUE.equals(install.invoke(null, manager)), "second class loader rejected the existing safe packer");
            check(packer(manager) == primarySafe, "second class loader replaced an existing safe packer");

            managerPacker.set(manager, new DataImagePacker());
            check(Boolean.TRUE.equals(install.invoke(null, manager)), "second class loader could not install its safe packer");
            DataImagePacker secondarySafe = packer(manager);
            check(secondarySafe != primarySafe, "second class loader did not install a new packer when required");
            check(DataImagePackerCompat.install(manager), "primary class loader rejected the second safe packer");
            check(packer(manager) == secondarySafe, "primary class loader replaced the second safe packer");
        }
    }

    private static DataImagePacker packer(DataManager manager) throws IllegalAccessException{
        return (DataImagePacker)managerPacker.get(manager);
    }

    private static Field field(Class<?> owner, String name){
        try{
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }catch(ReflectiveOperationException failure){
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }

    private static final class MockTexture extends Texture{
        int disposeCount;

        MockTexture(){
            super();
        }

        @Override
        public void dispose(){
            disposeCount++;
        }
    }

    private static final class CompatClassLoader extends URLClassLoader{
        CompatClassLoader(URL classes, ClassLoader parent){
            super(new URL[]{classes}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
            if(name.equals("bektools.DataImagePackerCompat") || name.startsWith("bektools.DataImagePackerCompat$")){
                synchronized(getClassLoadingLock(name)){
                    Class<?> loaded = findLoadedClass(name);
                    if(loaded == null) loaded = findClass(name);
                    if(resolve) resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
