package smartplacement;

import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.input.MobileInput;
import mindustry.input.Placement;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.DirectionBridge;
import mindustry.world.blocks.distribution.Duct;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.StackConveyor;

public final class SmartPlacementMod extends Mod{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;

    /** 传送带类 × 传送带类交叉改放空选反向分类器的开关。 */
    static final String settingCrossConveyorConveyor = "smartplacement-cross-cc";
    /** 传送带类 × 管道类交叉。 */
    static final String settingCrossConveyorDuct = "smartplacement-cross-cd";
    /** 管道类 × 传送带类交叉。 */
    static final String settingCrossDuctConveyor = "smartplacement-cross-dc";
    /** 管道类 × 管道类交叉。 */
    static final String settingCrossDuctDuct = "smartplacement-cross-dd";
    /** 管道交叉的阻挡带宽超过 2 格时改放物品管道桥。 */
    static final String settingDuctBridgeOverWide = "smartplacement-duct-bridge-wide";
    /** 交叉会形成第三个连续反向分类器时，新线改放本科技的桥。 */
    static final String settingSorterLimitBridge = "smartplacement-sorter-limit-bridge";
    /** 只在行星为“任意”（混合科技）时生效。 */
    static final String settingMixedPlanetOnly = "smartplacement-mixed-only";

    /** 宽阻挡带改用桥的阈值：超过该格数才换桥。 */
    private static final int wideBandThreshold = 2;
    /** 反向分类器最多允许连续串联的数量；第 3 个必须改用桥。 */
    private static final int sorterChainLimit = 2;

    private final ObjectMap<Block, PlacementProxy> proxies = new ObjectMap<Block, PlacementProxy>();
    private boolean proxiesBuilt;
    private boolean settingsRegistered;

    @Override
    public void init(){
        // Standalone loads content before mod init(), so this usually builds right away.
        // Bundled inside Neon the module is initialized before content exists; updatePlacementBlock()
        // retries lazily once blocks are loaded, otherwise the proxy table would stay empty forever.
        ensureProxies();

        Events.run(Trigger.update, this::updatePlacementBlock);
        Events.on(ClientLoadEvent.class, e -> registerSettings());
    }

    private void registerSettings(){
        if(settingsRegistered) return;
        settingsRegistered = true;
        if(bekBundled) return;
        if(Vars.ui == null || Vars.ui.settings == null) return;

        Vars.ui.settings.addCategory("@smartplacement.settings", Icon.rightOpen, this::bekBuildSettings);
    }

    /**
     * Populates a {@link mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable} with this mod's settings.
     * Neon calls this from its unified settings page while {@link #bekBundled} is true.
     */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(settingCrossConveyorConveyor, true);
        table.checkPref(settingCrossConveyorDuct, true);
        table.checkPref(settingCrossDuctConveyor, true);
        table.checkPref(settingCrossDuctDuct, true);
        table.checkPref(settingDuctBridgeOverWide, true);
        table.checkPref(settingSorterLimitBridge, true);
        table.checkPref(settingMixedPlanetOnly, true);
    }

    /**
     * Builds the proxy table for every item-line block. Safe to call every frame: it does nothing
     * until content exists and only runs once afterwards.
     */
    private void ensureProxies(){
        if(proxiesBuilt) return;
        if(Vars.content == null) return;

        Seq<Block> blocks = Vars.content.blocks();
        if(blocks == null || blocks.isEmpty()) return;

        proxiesBuilt = true;
        Seq<Block> lineBlocks = blocks.select(SmartPlacementMod::isItemLine);
        for(Block source : lineBlocks){
            PlacementProxy proxy = new PlacementProxy(source);
            Vars.content.remove(proxy);
            proxies.put(source, proxy);
        }
    }

    private void updatePlacementBlock(){
        ensureProxies();
        if(Vars.control == null || Vars.control.input == null) return;

        InputHandler input = Vars.control.input;
        if(input.block instanceof PlacementProxy){
            if(!replacementActive(input)){
                input.block = ((PlacementProxy)input.block).source;
            }
            return;
        }

        if(input.block == null) return;

        PlacementProxy proxy = proxies.get(input.block);
        if(proxy != null && replacementActive(input)){
            input.block = proxy;
            invalidateLineEnd(input);
        }
    }

    private static boolean replacementActive(InputHandler input){
        // getReplacement() only runs while vanilla block replacement is on; swapping the selected
        // block without it would try to place the unregistered proxy itself.
        return replacementEnabled() && lineReplacementEnabled() && isDrawingLine(input);
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

    private static boolean lineReplacementEnabled(){
        return Core.settings == null || Core.settings.getBool("blockreplace", true);
    }

    private static boolean settingEnabled(String key, boolean def){
        return Core.settings == null ? def : Core.settings.getBool(key, def);
    }

    /** @return the line family of a transport block, or null if it is not an item line. */
    static LineKind kindOf(Block block){
        if(block instanceof Duct) return LineKind.duct;
        if(block instanceof Conveyor || block instanceof StackConveyor) return LineKind.conveyor;
        return null;
    }

    private static boolean isItemLine(Block block){
        return kindOf(block) != null;
    }

    static CrossingOptions crossingOptions(){
        return new CrossingOptions(
            settingEnabled(settingCrossConveyorConveyor, true),
            settingEnabled(settingCrossConveyorDuct, true),
            settingEnabled(settingCrossDuctConveyor, true),
            settingEnabled(settingCrossDuctDuct, true)
        );
    }

    private static boolean replacementEnabled(){
        return Vars.state != null && replacementEnabled(
            Vars.state.rules.planet == Planets.sun,
            settingEnabled(settingMixedPlanetOnly, true)
        );
    }

    static boolean replacementEnabled(boolean sunPlanet, boolean mixedPlanetOnly){
        return !mixedPlanetOnly || sunPlanet;
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
            if(!replacementEnabled() || req.tile() == null) return fallback;

            boolean hasFront = containsPlan(plans, req.x + Geometry.d4x(req.rotation), req.y + Geometry.d4y(req.rotation));
            boolean hasBack = containsPlan(plans, req.x + Geometry.d4x(req.rotation + 2), req.y + Geometry.d4y(req.rotation + 2));
            if(!hasFront || !hasBack) return fallback;

            Building existing = req.tile().build;
            if(existing == null) return fallback;

            return crossingReplacement(
                source, existing.block, existing.rotation, req.rotation, hasFront, hasBack, true,
                crossingOptions(), fallback
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
            // Vanilla bridging first, then re-apply crossings it may have flattened, then the
            // bridge rules can turn a crossing run into a single bridge of this line's family.
            if(!handleDuctBridgePass(plans)){
                source.handlePlacementLine(plans);
            }
            restoreCrossings(plans);
            applyCrossingBridges(plans);
        }

        /**
         * Duct has no vanilla junction replacement (the assignment in {@code Duct.init()} is
         * commented out), so {@code Duct.handlePlacementLine} runs the bridge DP with
         * {@code hasJunction = false}. That makes a crossing plan look unplaceable, so the DP drops
         * it and the crossing degrades into a plain duct bridge jump. Conveyor lines pass
         * {@code hasJunction = true} and keep their plans, which is why only conveyor sources used
         * to work. Mirror the conveyor path whenever this batch really contains crossings.
         */
        private boolean handleDuctBridgePass(Seq<BuildPlan> plans){
            if(!(source instanceof Duct)) return false;
            if(!containsCrossing(plans)) return false;

            Block bridge = ((Duct)source).bridgeReplacement == null ? Blocks.ductBridge : ((Duct)source).bridgeReplacement;
            if(bridge == null) return false;

            Boolf<Block> avoid = b -> b instanceof Duct || b instanceof StackConveyor || b instanceof Conveyor;
            if(bridge instanceof DirectionBridge){
                Placement.calculateBridges(plans, (DirectionBridge)bridge, true, avoid);
                return true;
            }
            if(bridge instanceof ItemBridge){
                Placement.calculateBridges(plans, (ItemBridge)bridge, true, avoid);
                return true;
            }
            return false;
        }

        private static boolean containsCrossing(Seq<BuildPlan> plans){
            return plans.contains(plan -> plan.block == Blocks.invertedSorter);
        }

        /**
         * Re-applies crossing replacements on plans that survived bridging. Bridging rebuilds the
         * plan list and can leave a plain line block on a crossing tile; re-deciding here keeps the
         * sorter without touching vanilla-bridged plans (their block is a bridge, not a line).
         */
        private void restoreCrossings(Seq<BuildPlan> plans){
            if(!replacementEnabled() || plans.isEmpty()) return;

            CrossingOptions options = crossingOptions();
            IntSet positions = new IntSet(plans.size);
            for(BuildPlan plan : plans){
                positions.add(Point2.pack(plan.x, plan.y));
            }

            for(BuildPlan plan : plans){
                if(plan.block == Blocks.invertedSorter) continue;
                if(kindOf(plan.block) == null) continue;
                if(!positions.contains(Point2.pack(plan.x + Geometry.d4x(plan.rotation), plan.y + Geometry.d4y(plan.rotation)))) continue;
                if(!positions.contains(Point2.pack(plan.x + Geometry.d4x(plan.rotation + 2), plan.y + Geometry.d4y(plan.rotation + 2)))) continue;

                Building existing = plan.build();
                if(existing == null) continue;

                Block replacement = crossingReplacement(
                    source, existing.block, existing.rotation, plan.rotation, true, true, true, options, plan.block
                );
                if(replacement != plan.block) plan.block = replacement;
            }
        }

        /**
         * Bridges crossing runs that cannot keep using inverted sorters. Two rules feed in:
         * <ul>
         *   <li>the legacy wide-duct rule: a duct line jumping a band wider than
         *       {@link #wideBandThreshold} crossings;</li>
         *   <li>the sorter limit: a conveyor×duct crossing that would become a third consecutive
         *       inverted sorter along the crossed line, or 3+ consecutive crossings along the new
         *       line, must jump that band instead.</li>
         * </ul>
         * The bridge is always placed on the line being drawn, so the existing line keeps its
         * blocks and the two sorters already in place stay intact.
         */
        private void applyCrossingBridges(Seq<BuildPlan> plans){
            boolean wideDuct = source instanceof Duct && settingEnabled(settingDuctBridgeOverWide, true);
            boolean sorterLimit = settingEnabled(settingSorterLimitBridge, true);
            if(!wideDuct && !sorterLimit) return;
            // At minimum: one crossing tile plus a bridge endpoint on each side.
            if(plans.size < 3) return;

            BuildPlan first = plans.first();
            if(first.x != plans.peek().x && first.y != plans.peek().y) return;

            Block bridge = bridgeFor(source);
            if(bridge == null || !bridge.unlockedNow()) return;

            boolean[] blocked = new boolean[plans.size];
            for(int i = 0; i < blocked.length; i++){
                blocked[i] = isCrossingPlan(plans.get(i));
            }

            Seq<int[]> runs = wideBlockedRuns(blocked, 0);
            if(runs.isEmpty()) return;

            boolean[] bridged = new boolean[runs.size];
            for(int r = 0; r < runs.size; r++){
                int[] run = runs.get(r);
                int runLength = run[1] - run[0] + 1;
                boolean mixed = false, chainLimit = false;

                for(int i = run[0]; i <= run[1] && !chainLimit; i++){
                    BuildPlan plan = plans.get(i);
                    if(!isMixedCrossingPlan(plan)) continue;
                    mixed = true;
                    chainLimit = completesSorterChain(plan, plans);
                }

                bridged[r] = shouldBridgeCrossingRun(wideDuct, mixed, runLength, wideBandThreshold, chainLimit);
            }

            // Two bridges cannot share an endpoint block. Merge spans that touch so a one-tile gap
            // between two triggered runs is crossed by one bridge instead of leaving a dead link.
            Seq<int[]> spans = new Seq<int[]>();
            for(int r = 0; r < runs.size; r++){
                if(!bridged[r]) continue;
                int[] run = runs.get(r);
                int before = run[0] - 1, after = run[1] + 1;
                if(before < 0 || after >= plans.size) continue;

                if(!spans.isEmpty()){
                    int[] last = spans.peek();
                    if(before <= last[1]){
                        last[1] = Math.max(last[1], after);
                        continue;
                    }
                }
                spans.add(new int[]{before, after});
            }

            for(int s = spans.size - 1; s >= 0; s--){
                int[] span = spans.get(s);
                placeBridgeSpan(plans, span[0], span[1], bridge);
            }
        }

        /** @return the bridge block of the source line's technology family, or null if unavailable. */
        private static Block bridgeFor(Block source){
            if(source instanceof Conveyor){
                Block bridge = ((Conveyor)source).bridgeReplacement;
                return bridge == null ? Blocks.itemBridge : bridge;
            }
            if(source instanceof Duct){
                Block bridge = ((Duct)source).bridgeReplacement;
                return bridge == null ? Blocks.ductBridge : bridge;
            }
            return null;
        }

        private void placeBridgeSpan(Seq<BuildPlan> plans, int before, int after, Block bridge){
            if(before < 0 || after >= plans.size || before >= after) return;

            BuildPlan start = plans.get(before), end = plans.get(after);
            int maxSpan = bridge instanceof DirectionBridge ? ((DirectionBridge)bridge).range
                : bridge instanceof ItemBridge ? ((ItemBridge)bridge).range : 4;
            int span = Math.max(Math.abs(start.x - end.x), Math.abs(start.y - end.y));
            if(span > maxSpan) return;
            if(Vars.player != null){
                // Check the bridge itself, not the line plan: repeating the same line block and
                // rotation counts as unplaceable, but a bridge over that tile is still valid.
                Team team = Vars.player.team();
                if(!Build.validPlace(bridge, team, start.x, start.y, start.rotation)
                    || !Build.validPlace(bridge, team, end.x, end.y, end.rotation)) return;
            }

            start.block = bridge;
            end.block = bridge;
            if(bridge instanceof ItemBridge){
                start.config = new Point2(end.x - start.x, end.y - start.y);
            }

            for(int i = after - 1; i > before; i--){
                plans.remove(i);
            }
        }

        /** True when this crossing is a conveyor×duct pair, the only family mix this rule targets. */
        private boolean isMixedCrossingPlan(BuildPlan plan){
            if(plan.block != Blocks.invertedSorter) return false;
            Building existing = plan.build();
            if(existing == null) return false;

            LineKind sourceKind = kindOf(source), existingKind = kindOf(existing.block);
            return sourceKind != null && existingKind != null && sourceKind != existingKind;
        }

        /**
         * Checks the crossed line's axis around this crossing. Three consecutive sorters never
         * transfer items (Sorter.getTileTarget refuses 3-chains), so the third one must be a bridge.
         */
        private boolean completesSorterChain(BuildPlan plan, Seq<BuildPlan> plans){
            Building existing = plan.build();
            if(existing == null) return false;

            int rotation = existing.rotation;
            boolean[] window = new boolean[sorterChainLimit * 2 + 1];
            for(int i = 0; i < window.length; i++){
                int offset = i - sorterChainLimit;
                window[i] = offset == 0 || isSorterAt(
                    plan.x + Geometry.d4x(rotation) * offset,
                    plan.y + Geometry.d4y(rotation) * offset,
                    plans
                );
            }

            return sorterRunLength(window, sorterChainLimit) > sorterChainLimit;
        }

        private static boolean isSorterAt(int x, int y, Seq<BuildPlan> plans){
            if(Vars.world != null){
                Tile tile = Vars.world.tile(x, y);
                if(tile != null && tile.build != null && tile.build.block == Blocks.invertedSorter) return true;
            }

            return plans.contains(plan -> plan.x == x && plan.y == y && plan.block == Blocks.invertedSorter);
        }

        private static boolean isCrossingPlan(BuildPlan plan){
            if(plan.block != Blocks.invertedSorter) return false;
            Building existing = plan.build();
            return existing != null
                && kindOf(existing.block) != null
                && Mathf.mod(existing.rotation - plan.rotation, 2) == 1;
        }

        @Override
        public Object nextConfig(){
            return null;
        }
    }

    /**
     * @return runs {@code {start, end}} of consecutive blocked tiles whose length is greater than
     * {@code threshold}. Pure so the wide-band rule stays unit-testable.
     */
    static Seq<int[]> wideBlockedRuns(boolean[] blocked, int threshold){
        Seq<int[]> runs = new Seq<int[]>();
        int start = -1;
        for(int i = 0; i <= blocked.length; i++){
            boolean flag = i < blocked.length && blocked[i];
            if(flag && start == -1){
                start = i;
            }else if(!flag && start != -1){
                if(i - start > threshold){
                    runs.add(new int[]{start, i - 1});
                }
                start = -1;
            }
        }
        return runs;
    }

    /** @return length of the consecutive {@code true} run containing {@code index}, or 0. */
    static int sorterRunLength(boolean[] flags, int index){
        if(flags == null || index < 0 || index >= flags.length || !flags[index]) return 0;

        int left = index, right = index;
        while(left > 0 && flags[left - 1]) left--;
        while(right + 1 < flags.length && flags[right + 1]) right++;
        return right - left + 1;
    }

    /**
     * @param wideDuct the legacy duct-wide bridging rule applies to this run
     * @param mixed the run contains at least one conveyor×duct crossing
     * @param runLength consecutive crossing plans along the line being drawn
     * @param chainLimit one of the mixed crossings would complete a third consecutive sorter
     */
    static boolean shouldBridgeCrossingRun(boolean wideDuct, boolean mixed, int runLength, int wideThreshold, boolean chainLimit){
        if(wideDuct && runLength > wideThreshold) return true;
        return mixed && (runLength > wideThreshold || chainLimit);
    }

    static Block crossingReplacement(Block source, Block existing, int existingRotation, int planRotation,
                                     boolean hasFront, boolean hasBack, boolean dualTech, CrossingOptions options,
                                     Block fallback){
        if(!dualTech || !hasFront || !hasBack || Mathf.mod(existingRotation - planRotation, 2) != 1) return fallback;

        LineKind sourceKind = kindOf(source);
        LineKind existingKind = kindOf(existing);
        if(sourceKind == null || existingKind == null) return fallback;
        if(options == null || !options.allows(sourceKind, existingKind)) return fallback;

        return Blocks.invertedSorter;
    }

    enum LineKind{
        conveyor("conveyor"),
        duct("duct");

        final String id;

        LineKind(String id){
            this.id = id;
        }
    }

    static final class CrossingOptions{
        final boolean conveyorConveyor, conveyorDuct, ductConveyor, ductDuct;

        CrossingOptions(boolean conveyorConveyor, boolean conveyorDuct, boolean ductConveyor, boolean ductDuct){
            this.conveyorConveyor = conveyorConveyor;
            this.conveyorDuct = conveyorDuct;
            this.ductConveyor = ductConveyor;
            this.ductDuct = ductDuct;
        }

        boolean allows(LineKind source, LineKind existing){
            if(source == LineKind.conveyor){
                return existing == LineKind.conveyor ? conveyorConveyor : conveyorDuct;
            }
            return existing == LineKind.conveyor ? ductConveyor : ductDuct;
        }
    }
}
