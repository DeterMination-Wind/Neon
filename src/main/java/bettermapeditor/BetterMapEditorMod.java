package bettermapeditor;

import bettermapeditor.features.DraggableMirrorAxisFeature;
import mindustry.mod.Mod;

public class BetterMapEditorMod extends Mod {

    @Override
    public void init() {
        DraggableMirrorAxisFeature.init();
    }
}
