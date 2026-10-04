package mdtxcompat;

import mindustry.Vars;

import java.util.LinkedHashSet;

/** MindustryX runtime detection, cross-classloader class loading, and the minimum-version guard that rejects legacy X builds. */
public final class LegacyMindustryXGuard {
    public static final String MINIMUM_VERSION = "2026.04.03.B439";
    private static final String[] MINDUSTRYX_MARKER_CLASSES = {"mindustryX.VarsX", "mindustryX.loader.Main"};

    private LegacyMindustryXGuard() {
    }

    /**
     * Whether a real MindustryX runtime is present. The reliable signal is a marker class on the
     * game core loader: system properties and mod names also match skipped or disabled loader
     * mods, and Neon's own same-FQCN overlay copy lives in a mod loader, never on the core.
     */
    public static boolean isMindustryXRuntime() {
        return hasMindustryXMarker(Vars.class.getClassLoader());
    }

    /**
     * Resolves a MindustryX class. When the core loader really carries MindustryX, only that
     * chain is used, so Neon can never bind a same-named copy from a mod realm; otherwise fall
     * back to this mod's loader (Neon's bundled compatibility copy), the context loader, the
     * shared ModClassLoader (which walks every mod child), and finally the system loader.
     */
    public static Class<?> loadMindustryXClass(String name) throws ClassNotFoundException {
        ClassLoader core = Vars.class.getClassLoader();
        if (hasMindustryXMarker(core)) {
            return Class.forName(name, false, core);
        }

        ClassNotFoundException last = null;
        for (ClassLoader loader : fallbackClassLoaders()) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }

        throw last == null ? new ClassNotFoundException(name) : last;
    }

    public static void rejectLegacyMindustryX(String modName) {
        if (!isMindustryXRuntime()) return;

        throw new IllegalStateException(
            modName
                + " 仅支持 2026 年 4 月 3 日 B439（"
                + MINIMUM_VERSION
                + "）及之后的 MindustryX 版本。\n您需要升级版本或者回退模组版本，新版模组并没有更新任何实质性内容。"
        );
    }

    private static LinkedHashSet<ClassLoader> fallbackClassLoaders() {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();
        addLoader(loaders, LegacyMindustryXGuard.class.getClassLoader());
        addLoader(loaders, Thread.currentThread().getContextClassLoader());
        if (Vars.mods != null) addLoader(loaders, Vars.mods.mainLoader());
        addLoader(loaders, Vars.class.getClassLoader());
        addLoader(loaders, ClassLoader.getSystemClassLoader());
        return loaders;
    }

    private static boolean hasMindustryXMarker(ClassLoader loader) {
        if (loader == null) return false;
        for (String marker : MINDUSTRYX_MARKER_CLASSES) {
            if (classExists(marker, loader)) return true;
        }
        return false;
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
