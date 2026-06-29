package mdtxcompat;

import mindustry.Vars;
import mindustry.mod.Mods;

public final class LegacyMindustryXGuard {
    public static final String MINIMUM_VERSION = "2026.04.03.B439";

    private LegacyMindustryXGuard() {
    }

    public static boolean isMindustryXRuntime() {
        return locateMindustryX() != null || classExists("mindustryX.VarsX") || classExists("mindustryX.loader.Main");
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

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, LegacyMindustryXGuard.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
