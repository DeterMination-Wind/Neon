package mdtxcompat;

import mindustry.Vars;
import mindustry.mod.Mods;

public final class LegacyMindustryXGuard {
    public static final String MINIMUM_VERSION = "2026.04.03.B439";

    private LegacyMindustryXGuard() {
    }

    public static boolean isMindustryXRuntime() {
        return locateMindustryX() != null
            || classExists("mindustryX.VarsX", runtimeClassLoader())
            || classExists("mindustryX.loader.Main", runtimeClassLoader())
            || classExists("mindustryX.VarsX", LegacyMindustryXGuard.class.getClassLoader())
            || classExists("mindustryX.loader.Main", LegacyMindustryXGuard.class.getClassLoader());
    }

    public static Class<?> loadMindustryXClass(String name) throws ClassNotFoundException {
        if (isMindustryXRuntime()) {
            try {
                return Class.forName(name, false, runtimeClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(name, false, LegacyMindustryXGuard.class.getClassLoader());
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
        Mods.LoadedMod mod = Vars.mods.locateMod("mindustryx");
        if (mod != null) return mod;
        return Vars.mods.locateMod("mdtx");
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
