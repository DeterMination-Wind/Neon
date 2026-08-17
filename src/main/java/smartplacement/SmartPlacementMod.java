package smartplacement;


import mindustry.ui.dialogs.SettingsMenuDialog;
import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.input.MobileInput;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.Duct;
import mindustry.world.blocks.distribution.StackConveyor;

public final class SmartPlacementMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;


    private final ObjectMap<Block, PlacementProxy> proxies = new ObjectMap<Block, PlacementProxy>();

    @Override
    public void init(){
        Seq<Block> lineBlocks = Vars.content.blocks().select(SmartPlacementMod::isItemLine);
        for(Block source : lineBlocks){
            PlacementProxy proxy = new PlacementProxy(source);
            Vars.content.remove(proxy);
            proxies.put(source, proxy);
        }

        Events.run(Trigger.update, this::updatePlacementBlock);
    }

    private void updatePlacementBlock(){
        if(Vars.control == null || Vars.control.input == null) return;

        InputHandler input = Vars.control.input;
        if(input.block instanceof PlacementProxy){
            if(!replacementEnabled() || !isDrawingLine(input)){
                input.block = ((PlacementProxy)input.block).source;
            }
            return;
        }

        if(input.block == null) return;

        PlacementProxy proxy = proxies.get(input.block);
        if(proxy != null && replacementEnabled() && isDrawingLine(input)){
            input.block = proxy;
            invalidateLineEnd(input);
        }
    }

    private static boolean isDrawingLine(InputHandler input){
        return !input.linePlans.isEmpty();
    }

    private static void invalidateLineEnd(InputHandler input){
        if(input instanceof DesktopInput){
            DesktopInput desktop = (DesktopInput)input;
            desktop.lastLineX = Integer.MIN_VALUE;
            desktop.lastLineY = Integer.MIN_VALUE;
        }else if(input instanceof MobileInput){
            MobileInput mobile = (MobileInput)input;
            mobile.lastLineX = Integer.MIN_VALUE;
            mobile.lastLineY = Integer.MIN_VALUE;
        }
    }

    private static boolean isItemLine(Block block){
        return block instanceof Conveyor || block instanceof StackConveyor || block instanceof Duct;
    }

    private static boolean replacementEnabled(){
        return Vars.state != null && replacementEnabled(Vars.state.rules.planet == Planets.sun);
    }

    static boolean replacementEnabled(boolean sunPlanet){
        return sunPlanet;
    }

    private static final class PlacementProxy extends Block{
        private final Block source;

        private PlacementProxy(Block source){
            super("smart-placement-proxy-" + source.id);
            this.source = source;

            size = source.size;
            offset = source.offset;
            rotate = source.rotate;
            quickRotate = source.quickRotate;
            conveyorPlacement = source.conveyorPlacement;
            allowDiagonal = source.allowDiagonal;
            swapDiagonalPlacement = source.swapDiagonalPlacement;
            allowRectanglePlacement = source.allowRectanglePlacement;
            ignoreLineRotation = source.ignoreLineRotation;
            group = source.group;
            requirements = source.requirements;
            category = source.category;
            buildVisibility = source.buildVisibility;
            region = source.region;
            fullIcon = source.fullIcon;
            uiIcon = source.uiIcon;
        }

        @Override
        public boolean canReplace(Block other){
            return source.canReplace(other);
        }

        @Override
        public void changePlacementPath(Seq<Point2> points, int rotation, boolean diagonalOn){
            source.changePlacementPath(points, rotation, diagonalOn);
        }

        @Override
        public Block getReplacement(BuildPlan req, Seq<BuildPlan> plans){
            Block fallback = sourceReplacement(req, plans);
            boolean hasFront = containsPlan(plans, req.x + Geometry.d4x(req.rotation), req.y + Geometry.d4y(req.rotation));
            boolean hasBack = containsPlan(plans, req.x + Geometry.d4x(req.rotation + 2), req.y + Geometry.d4y(req.rotation + 2));
            if(!replacementEnabled() || !hasFront || !hasBack || req.tile() == null) return fallback;

            Building existing = req.tile().build;
            return existing == null ? fallback : crossingReplacement(
                source, existing.block, existing.rotation, req.rotation, hasFront, hasBack, true, fallback
            );
        }

        private Block sourceReplacement(BuildPlan req, Seq<BuildPlan> plans){
            Block original = req.block;
            req.block = source;
            try{
                return source.getReplacement(req, plans);
            }finally{
                req.block = original;
            }
        }

        private static boolean containsPlan(Seq<BuildPlan> plans, int x, int y){
            return plans.contains(plan -> plan.x == x && plan.y == y);
        }

        @Override
        public void handlePlacementLine(Seq<BuildPlan> plans){
            source.handlePlacementLine(plans);
        }

        @Override
        public Object nextConfig(){
            return null;
        }
    }

    static Block crossingReplacement(Block source, Block existing, int existingRotation, int planRotation,
                                     boolean hasFront, boolean hasBack, boolean dualTech, Block fallback){
        if(!dualTech || !hasFront || !hasBack || Mathf.mod(existingRotation - planRotation, 2) != 1) return fallback;
        if(isItemLine(source) && isItemLine(existing)){
            return Blocks.invertedSorter;
        }
        return fallback;
    }

    /** This bundled module has no configurable settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
    }
}
