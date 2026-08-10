package mdtxcompat;

import mindustry.Vars;
import mindustry.mod.Mods;

import java.util.LinkedHashSet;

public final class LegacyMindustryXGuard {
    public static final String MINIMUM_VERSION = "2026.04.03.B439";
    private static final String[] MINDUSTRYX_MOD_NAMES = {"mindustryx", "mdtx"};
    private static final String[] MINDUSTRYX_MARKER_CLASSES = {"mindustryX.VarsX", "mindustryX.loader.Main"};

    private LegacyMindustryXGuard() {
    }

    public static boolean isMindustryXRuntime() {
        if ("1".equals(System.getProperty("mdtx.loader"))) return true;
        if (System.getProperty("MDTX-loaded") != null) return true;
        if (locateMindustryX() != null) return true;

        for (String marker : MINDUSTRYX_MARKER_CLASSES) {
            for (ClassLoader loader : mindustryXRuntimeClassLoaders()) {
                if (classExists(marker, loader)) return true;
            }
        }
        return false;
    }

    public static Class<?> loadMindustryXClass(String name) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        Iterable<ClassLoader> loaders = isMindustryXRuntime()
            ? mindustryXRuntimeClassLoaders()
            : overlayUiClassLoaders();

        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }

        throw last == null ? new ClassNotFoundException(name) : last;
    }

    public static ClassLoader runtimeClassLoader() {
        if (Vars.mods != null) {
            ClassLoader loader = Vars.mods.getClass().getClassLoader();
            if (loader != null) return loader;
        }
        ClassLoader loader = Vars.class.getClassLoader();
        return loader == null ? LegacyMindustryXGuard.class.getClassLoader() : loader;
    }

    public static void rejectLegacyMindustryX(String modName) {
        Mods.LoadedMod mindustryX = locateMindustryX();
        if (mindustryX == null) return;

        throw new IllegalStateException(
            modName
                + " 仅支持 2026 年 4 月 3 日 B439（"
                + MINIMUM_VERSION
                + "）及之后的 MindustryX 版本。\n您需要升级版本或者回退模组版本，新版模组并没有更新任何实质性内容。"
        );
    }

    private static Mods.LoadedMod locateMindustryX() {
        if (Vars.mods == null) return null;
        try {
            for (String name : MINDUSTRYX_MOD_NAMES) {
                Mods.LoadedMod mod = Vars.mods.locateMod(name);
                if (mod != null) return mod;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static LinkedHashSet<ClassLoader> mindustryXRuntimeClassLoaders() {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();
        addLoader(loaders, Thread.currentThread().getContextClassLoader());

        Mods.LoadedMod mindustryX = locateMindustryX();
        if (mindustryX != null) addLoader(loaders, mindustryX.loader);

        addLoader(loaders, runtimeClassLoader());
        addLoader(loaders, Vars.class.getClassLoader());
        addLoader(loaders, ClassLoader.getSystemClassLoader());
        return loaders;
    }

    private static LinkedHashSet<ClassLoader> overlayUiClassLoaders() {
        LinkedHashSet<ClassLoader> loaders = mindustryXRuntimeClassLoaders();
        addLoader(loaders, LegacyMindustryXGuard.class.getClassLoader());
        return loaders;
    }

    private static void addLoader(LinkedHashSet<ClassLoader> loaders, ClassLoader loader) {
        if (loader != null) loaders.add(loader);
    }

    private static boolean classExists(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
