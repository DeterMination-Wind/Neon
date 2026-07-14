package bektools;

import arc.Core;
import arc.Events;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureAtlas;
import arc.graphics.g2d.TextureAtlas.AtlasRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.mod.DataImagePacker;
import mindustry.mod.DataManager;

import java.lang.reflect.Field;

/** Installs a safe v159 data-patch image unloader without modifying the game runtime. */
final class DataImagePackerCompat{
    private static final String safePackerClassName = DataImagePackerCompat.class.getName() + "$SafeDataImagePacker";
    private static final Object lock = new Object();

    private static boolean hooksRegistered;
    private static boolean accessResolved;
    private static boolean accessWarningLogged;
    private static boolean cleanupWarningLogged;
    private static Access access;

    private DataImagePackerCompat(){
    }

    static void installHooks(){
        synchronized(lock){
            if(!hooksRegistered){
                hooksRegistered = true;
                try{
                    Events.on(ClientLoadEvent.class, event -> installCurrent());
                    // Logic.reset fires this before unloading the old GameState.
                    Events.on(ResetEvent.class, event -> installCurrent());
                    // Logic.reset creates the replacement GameState before firing this event.
                    Events.on(StateChangeEvent.class, event -> installCurrent());
                }catch(Throwable failure){
                    warnAccessOnce("event hooks could not be registered", failure);
                }
            }
        }

        // Mod construction normally happens before ClientLoadEvent. Installing here also
        // protects data patches that are loaded unusually early by another client mod.
        installCurrent();
    }

    static boolean installCurrent(){
        try{
            return Vars.state != null && install(Vars.state.data);
        }catch(Throwable failure){
            warnAccessOnce("the current GameState could not be inspected", failure);
            return false;
        }
    }

    static boolean install(DataManager manager){
        if(manager == null) return false;

        synchronized(lock){
            Access fields = access();
            if(fields == null) return false;

            try{
                Object value = fields.managerPacker.get(manager);
                if(value != null && safePackerClassName.equals(value.getClass().getName())) return true;
                if(value != null && !(value instanceof DataImagePacker)){
                    throw new IllegalStateException("DataManager.packer is not a DataImagePacker");
                }

                DataImagePacker previous = (DataImagePacker)value;
                SafeDataImagePacker replacement = new SafeDataImagePacker();
                TextureAtlas transferred = previous == null ? null : (TextureAtlas)fields.patchAtlas.get(previous);

                fields.patchAtlas.set(replacement, transferred);
                if(previous != null) fields.patchAtlas.set(previous, null);

                try{
                    fields.managerPacker.set(manager, replacement);
                }catch(Throwable failure){
                    fields.patchAtlas.set(replacement, null);
                    if(previous != null) fields.patchAtlas.set(previous, transferred);
                    throw failure;
                }
                return true;
            }catch(Throwable failure){
                warnAccessOnce("DataManager.packer could not be replaced", failure);
                return false;
            }
        }
    }

    static boolean isInstalled(DataManager manager){
        if(manager == null) return false;
        synchronized(lock){
            Access fields = access();
            if(fields == null) return false;
            try{
                Object value = fields.managerPacker.get(manager);
                return value != null && safePackerClassName.equals(value.getClass().getName());
            }catch(Throwable failure){
                warnAccessOnce("DataManager.packer could not be inspected", failure);
                return false;
            }
        }
    }

    private static Access access(){
        if(accessResolved) return access;
        accessResolved = true;

        try{
            Field managerPacker = findField(DataManager.class, "packer", DataImagePacker.class);
            Field patchAtlas = findField(DataImagePacker.class, "patchAtlas", TextureAtlas.class);
            managerPacker.setAccessible(true);
            patchAtlas.setAccessible(true);
            access = new Access(managerPacker, patchAtlas);
        }catch(Throwable failure){
            warnAccessOnce("the v159 DataManager/DataImagePacker layout did not match", failure);
        }
        return access;
    }

    private static Field findField(Class<?> owner, String preferredName, Class<?> fieldType) throws NoSuchFieldException{
        try{
            Field preferred = owner.getDeclaredField(preferredName);
            if(fieldType.isAssignableFrom(preferred.getType())) return preferred;
        }catch(NoSuchFieldException ignored){
            // Fall through to the unique compatible field lookup below.
        }

        Field match = null;
        for(Field field : owner.getDeclaredFields()){
            if(!fieldType.isAssignableFrom(field.getType())) continue;
            if(match != null){
                throw new NoSuchFieldException(owner.getName() + " has multiple " + fieldType.getName() + " fields");
            }
            match = field;
        }
        if(match == null){
            throw new NoSuchFieldException(owner.getName() + " has no " + fieldType.getName() + " field");
        }
        return match;
    }

    private static void warnAccessOnce(String message, Throwable failure){
        synchronized(lock){
            if(accessWarningLogged) return;
            accessWarningLogged = true;
        }
        Log.warn("[Neon] DataImagePacker compatibility unavailable: @ (@)", message, failure.toString());
    }

    private static void warnCleanupOnce(Throwable failure){
        synchronized(lock){
            if(cleanupWarningLogged) return;
            cleanupWarningLogged = true;
        }
        Log.warn("[Neon] Safe DataImagePacker cleanup was incomplete: @", failure.toString());
    }

    private static final class Access{
        final Field managerPacker;
        final Field patchAtlas;

        Access(Field managerPacker, Field patchAtlas){
            this.managerPacker = managerPacker;
            this.patchAtlas = patchAtlas;
        }
    }

    private static final class SafeDataImagePacker extends DataImagePacker{
        @Override
        public synchronized void unload(){
            Access fields;
            TextureAtlas patchAtlas;

            synchronized(lock){
                fields = access();
                if(fields == null) return;
                try{
                    patchAtlas = (TextureAtlas)fields.patchAtlas.get(this);
                    if(patchAtlas == null) return;
                    // Detach first so re-entrant or repeated unload calls are no-ops.
                    fields.patchAtlas.set(this, null);
                }catch(Throwable failure){
                    warnAccessOnce("the patch atlas could not be detached", failure);
                    return;
                }
            }

            Throwable cleanupFailure = null;
            try{
                TextureAtlas globalAtlas = Core.atlas;
                if(globalAtlas != null){
                    Seq<Texture> textures = patchAtlas.getTextures().toSeq();
                    for(int i = 0; i < textures.size; i++){
                        globalAtlas.getTextures().remove(textures.get(i));
                    }

                    ObjectMap<String, AtlasRegion> regions = new ObjectMap<>();
                    regions.putAll(patchAtlas.getRegionMap());
                    for(ObjectMap.Entry<String, AtlasRegion> entry : regions){
                        if(globalAtlas.getRegionMap().get(entry.key) == entry.value){
                            globalAtlas.getRegionMap().remove(entry.key);
                        }
                    }
                }
            }catch(Throwable failure){
                cleanupFailure = failure;
            }

            try{
                patchAtlas.dispose();
            }catch(Throwable failure){
                if(cleanupFailure == null) cleanupFailure = failure;
            }

            if(cleanupFailure != null) warnCleanupOnce(cleanupFailure);
        }
    }
}
