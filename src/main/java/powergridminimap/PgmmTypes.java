package powergridminimap;

import arc.util.Log;
import arc.math.geom.Rect;
import mindustry.game.Team;
import mindustry.world.blocks.power.PowerGraph;

import java.lang.reflect.Field;

/**
 * PGMM shared data types (extracted from the monolithic mod class).
 *
 * PGMM（电网小地图）的一些通用数据结构/反射小工具。
 * 原本这些类型都写在 {@link PowerGridMinimapMod} 里，导致单文件过长；
 * 拆分到独立文件后更便于维护，但不改变任何逻辑/行为。
 */
final class GridInfo{
    PowerGraph graph;
    Team team;
    int colorKey;
    float centerX, centerY;
    boolean hasCenter;
}

final class MarkerInfo{
    PowerGraph graph;
    float x, y;
}

final class MarkerRectInfo{
    PowerGraph graph;
    int colorKey;
    final Rect worldRect = new Rect();
}

/**
 * Reflects a few private fields from Mindustry's MinimapFragment to align fullscreen overlays.
 *
 * 通过反射读取 MinimapFragment 的一些私有字段，用于让全屏小地图叠加层与缩放/平移保持一致。
 * 反射失败时会降级（使用默认值），不会影响游戏正常运行。
 */
final class FullMinimapAccess{
    private boolean initialized;
    private Field panx, pany, zoom, baseSize;

    private void init(Object frag){
        if(initialized) return;
        initialized = true;
        try{
            Class<?> type = frag.getClass();
            panx = type.getDeclaredField("panx");
            pany = type.getDeclaredField("pany");
            zoom = type.getDeclaredField("zoom");
            baseSize = type.getDeclaredField("baseSize");
            panx.setAccessible(true);
            pany.setAccessible(true);
            zoom.setAccessible(true);
            baseSize.setAccessible(true);
        }catch(Throwable t){
            Log.err("PGMM: failed to reflect MinimapFragment fields, fullscreen overlay may be misaligned.");
        }
    }

    float getPanX(Object frag){
        init(frag);
        try{
            return panx == null ? 0f : panx.getFloat(frag);
        }catch(Throwable ignored){
            return 0f;
        }
    }

    float getPanY(Object frag){
        init(frag);
        try{
            return pany == null ? 0f : pany.getFloat(frag);
        }catch(Throwable ignored){
            return 0f;
        }
    }

    float getZoom(Object frag){
        init(frag);
        try{
            return zoom == null ? 1f : zoom.getFloat(frag);
        }catch(Throwable ignored){
            return 1f;
        }
    }

    float getBaseSize(Object frag, float fallback){
        init(frag);
        try{
            return baseSize == null ? fallback : baseSize.getFloat(frag);
        }catch(Throwable ignored){
            return fallback;
        }
    }
}
