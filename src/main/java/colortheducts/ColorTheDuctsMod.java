package colortheducts;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.struct.IntSet;
import arc.util.Tmp;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.mod.Mod;
import mindustry.type.Liquid;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.DirectionBridge;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.DirectionLiquidBridge;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.liquid.LiquidRouter;

import java.util.ArrayDeque;
import java.util.Locale;

import static mindustry.Vars.content;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class ColorTheDuctsMod extends Mod{
    public static boolean bekBundled = false;

    private static final String keyEnabled = "ctd-enabled";
    private static final String keyScaleTenths = "ctd-square-scale";
    private static final String keyHoverOnly = "ctd-hover-only";
    private static final String keyHoverLine = "ctd-hover-line";
    private static final String keyAlphaPercent = "ctd-alpha-percent";

    public ColorTheDuctsMod(){
        Events.on(ClientLoadEvent.class, e -> {
            Core.settings.defaults(keyEnabled, defaultEnabled());
            Core.settings.defaults(keyScaleTenths, 6);
            Core.settings.defaults(keyHoverOnly, false);
            Core.settings.defaults(keyHoverLine, false);
            Core.settings.defaults(keyAlphaPercent, 100);
            if(!bekBundled) registerSettings();
        });

        Events.run(Trigger.draw, this::drawLiquidSquares);
    }

    private void registerSettings(){
        if(ui == null || ui.settings == null) return;

        String category = Core.bundle.get("ctd.category", "Color-the-ducts");
        ui.settings.addCategory(category, Icon.imageSmall, this::bekBuildSettings);
    }

    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, defaultEnabled());
        table.sliderPref(keyScaleTenths, 6, 0, 10, 1, value -> {
            if(value <= 0) return Core.bundle.get("ctd.scale.off", "off");
            return String.format(Locale.ROOT, "%.1f", value / 10f);
        });
        table.checkPref(keyHoverOnly, false);
        table.checkPref(keyHoverLine, false);
        table.sliderPref(keyAlphaPercent, 100, 0, 100, 5, value -> value + "%");
    }

    private void drawLiquidSquares(){
        if(!Core.settings.getBool(keyEnabled, defaultEnabled())) return;
        if(world == null || state == null || !state.isGame() || world.isGenerating()) return;

        float scale = Mathf.clamp(Core.settings.getInt(keyScaleTenths, 6) / 10f, 0f, 1f);
        if(scale <= 0.0001f) return;

        float alpha = Mathf.clamp(Core.settings.getInt(keyAlphaPercent, 100) / 100f, 0f, 1f);
        if(alpha <= 0.0001f) return;

        Draw.z(Layer.blockOver + 0.1f);
        if(Core.settings.getBool(keyHoverOnly, false)){
            Building hovered = getHoveredBuilding();
            if(Core.settings.getBool(keyHoverLine, false)){
                drawConnectedLine(hovered, scale, alpha);
            }else{
                drawForBuilding(hovered, scale, alpha);
            }
        }else{
            Groups.build.each(build -> drawForBuilding(build, scale, alpha));
        }
        Draw.color();
    }

    private void drawConnectedLine(Building start, float scale, float alpha){
        if(start == null || !start.isValid() || start.block == null) return;
        if(!isLiquidDuct(start.block)) return;

        ArrayDeque<Building> queue = new ArrayDeque<>();
        IntSet queued = new IntSet();
        IntSet visited = new IntSet();

        queue.add(start);
        queued.add(start.pos());

        while(!queue.isEmpty()){
            Building current = queue.pollFirst();
            if(current == null || !current.isValid() || current.block == null) continue;

            int pos = current.pos();
            if(visited.contains(pos)) continue;
            visited.add(pos);

            if(!isLiquidDuct(current.block)) continue;
            drawForBuilding(current, scale, alpha);

            collectBridgeLinks(current, queue, queued, visited);

            for(int dir = 0; dir < 4; dir++){
                Building nearby = current.nearby(dir);
                if(nearby == null || !nearby.isValid() || nearby.block == null) continue;
                if(!isLiquidDuct(nearby.block) || nearby.team != current.team) continue;

                enqueueJunctionOpposite(current, nearby, queue, queued, visited);
                enqueueJunctionOpposite(nearby, current, queue, queued, visited);

                if(getTransferDestination(current, nearby) != null || getTransferDestination(nearby, current) != null){
                    enqueueIfNeeded(nearby, queue, queued, visited);
                }

                Building passthrough = getTransferDestination(current, nearby);
                if(passthrough != null && passthrough != nearby){
                    enqueueIfNeeded(passthrough, queue, queued, visited);
                }
            }
        }
    }

    private void enqueueJunctionOpposite(Building from, Building junction, ArrayDeque<Building> queue, IntSet queued, IntSet visited){
        if(from == null || junction == null || from.block == null || junction.block == null) return;
        if(!(junction.block instanceof LiquidJunction) || from.team != junction.team) return;

        int dir = from.relativeTo(junction.tileX(), junction.tileY());
        if(dir < 0 || dir > 3) return;

        enqueueIfNeeded(junction, queue, queued, visited);

        Building opposite = junction.nearby(dir);
        if(opposite == null || !opposite.isValid() || opposite.block == null) return;
        if(opposite.team != junction.team || !isLiquidDuct(opposite.block)) return;

        enqueueIfNeeded(opposite, queue, queued, visited);
    }

    private void collectBridgeLinks(Building current, ArrayDeque<Building> queue, IntSet queued, IntSet visited){
        if(current instanceof ItemBridge.ItemBridgeBuild){
            ItemBridge.ItemBridgeBuild bridge = (ItemBridge.ItemBridgeBuild)current;

            enqueueByPos(bridge.link, queue, queued, visited);

            if(bridge.incoming != null){
                for(int i = 0; i < bridge.incoming.size; i++){
                    enqueueByPos(bridge.incoming.items[i], queue, queued, visited);
                }
            }
        }

        if(current instanceof DirectionBridge.DirectionBridgeBuild){
            DirectionBridge.DirectionBridgeBuild bridge = (DirectionBridge.DirectionBridgeBuild)current;

            enqueueIfNeeded(bridge.findLink(), queue, queued, visited);

            if(bridge.occupied != null){
                for(int i = 0; i < bridge.occupied.length; i++){
                    enqueueIfNeeded(bridge.occupied[i], queue, queued, visited);
                }
            }
        }
    }

    private void enqueueByPos(int pos, ArrayDeque<Building> queue, IntSet queued, IntSet visited){
        if(pos < 0 || world == null) return;

        Tile tile = world.tile(pos);
        if(tile == null) return;
        enqueueIfNeeded(tile.build, queue, queued, visited);
    }

    private void enqueueIfNeeded(Building build, ArrayDeque<Building> queue, IntSet queued, IntSet visited){
        if(build == null || !build.isValid() || build.block == null) return;
        if(!isLiquidDuct(build.block)) return;

        int pos = build.pos();
        if(visited.contains(pos) || queued.contains(pos)) return;

        queued.add(pos);
        queue.addLast(build);
    }

    private Building getTransferDestination(Building from, Building to){
        if(from == null || to == null || !from.isValid() || !to.isValid()) return null;
        if(from.block == null || to.block == null || to.team != from.team) return null;

        Liquid probe = findProbeLiquid(from, to);
        if(probe == null) return null;

        if(!from.canDumpLiquid(to, probe)) return null;
        if(!to.acceptLiquid(from, probe)) return null;

        Building destination = to.getLiquidDestination(from, probe);
        if(destination == null || !destination.isValid() || destination.block == null) return null;
        if(destination.team != from.team) return null;
        if(!isLiquidDuct(destination.block)) return null;
        return destination;
    }

    private Liquid findProbeLiquid(Building from, Building to){
        if(from != null && from.liquids != null){
            Liquid liquid = from.liquids.current();
            if(liquid != null) return liquid;
        }

        if(to != null && to.liquids != null){
            Liquid liquid = to.liquids.current();
            if(liquid != null) return liquid;
        }

        if(content != null && content.liquids() != null && content.liquids().size > 0){
            return content.liquids().get(0);
        }

        return null;
    }

    private Building getHoveredBuilding(){
        if(world == null || Core.camera == null || Core.input == null) return null;
        if(Core.scene != null && Core.scene.hasMouse()) return null;

        Tmp.v1.set(Core.input.mouseX(), Core.input.mouseY());
        Core.camera.unproject(Tmp.v1);
        return world.buildWorld(Tmp.v1.x, Tmp.v1.y);
    }

    private void drawForBuilding(Building build, float scale, float alpha){
        if(build == null || !build.isValid() || build.block == null) return;
        if(!isLiquidDuct(build.block)) return;
        if(build.liquids == null || build.liquids.currentAmount() <= 0.0001f) return;

        Liquid liquid = build.liquids.current();
        if(liquid == null) return;

        float sideLength = build.block.size * tilesize * scale;
        if(sideLength <= 0.0001f) return;

        Draw.color(liquid.color);
        Draw.alpha(alpha);
        Fill.square(build.x, build.y, sideLength / 2f);
    }

    private boolean isLiquidDuct(Block block){
        return block instanceof Conduit
            || block instanceof LiquidJunction
            || block instanceof LiquidRouter
            || block instanceof LiquidBridge
            || block instanceof DirectionLiquidBridge;
    }

    private static boolean defaultEnabled(){
        return !bekBundled;
    }
}
