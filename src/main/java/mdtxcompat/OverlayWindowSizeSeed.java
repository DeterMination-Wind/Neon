package mdtxcompat;

import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;

/**
 * Gives overlay windows a content-derived size when their persisted state has none.
 *
 * <p>MindustryX's OverlayUI writes {@code WindowData.size} only when the player drags a resize
 * handle, and the preview layout restores a size only when one exists. Resizable windows thus
 * start at zero width/height after every restart until the overlay editor is opened - which packs
 * them once but does not persist the result - so panels collapse, overlap or lose their background
 * again on the next launch. This helper polls the windows registered through Neon's bridges and
 * packs the ones that are still unsized once their content exists. A restored size wins by making
 * the window non-zero first, so persisted geometry is never overwritten. Once packed the window
 * keeps its size for the session; the check runs again after the next registration, so restarts
 * stay correct without writing any settings.</p>
 */
public final class OverlayWindowSizeSeed{
    /** One pending window; {@link #ensure()} returns true once the window no longer needs seeding. */
    public interface Seed{
        boolean ensure();
    }

    private static final Seq<Seed> seeds = new Seq<>();
    private static boolean installed;

    private OverlayWindowSizeSeed(){
    }

    public static void submit(Seed seed){
        if(seed == null || Vars.headless) return;
        seeds.add(seed);
        install();
    }

    /**
     * Whether the window's content has been added and measured. Hidden windows report false until
     * their first rebuild, which is why the seed keeps polling instead of packing once.
     */
    public static boolean contentReady(Element element){
        return element instanceof Group
            && !((Group)element).getChildren().isEmpty()
            && element.getPrefWidth() > 0.5f;
    }

    private static void install(){
        if(installed) return;
        installed = true;
        Events.run(Trigger.update, OverlayWindowSizeSeed::tick);
    }

    private static void tick(){
        if(seeds.isEmpty()) return;
        for(int i = seeds.size - 1; i >= 0; i--){
            Seed seed = seeds.get(i);
            boolean done;
            try{
                done = seed.ensure();
            }catch(Throwable t){
                done = true;
                Log.err("Neon OverlayUI integration: overlay window size seed failed.", t);
            }
            if(done) seeds.remove(i);
        }
    }
}
