package patrolcancel;


import mindustry.ui.dialogs.SettingsMenuDialog;
import arc.Core;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ai.UnitStance;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.mod.Mod;

/**
 * Right-click to command selected units also clears their patrol stance.
 *
 * <p>In Mindustry v8 the right-click command chain
 * ({@code DesktopInput.touchDown -> InputHandler.commandTap -> Call.commandUnits}) only clears the
 * command queue and sets a target position; it never touches {@link UnitStance#patrol}. Patrolling
 * units therefore keep re-queuing their old waypoints ({@code CommandAI.finishPath}/{@code updateUnit})
 * and appear to ignore the right-click move command - they get stuck in the patrol loop.
 *
 * <p>This mod disables the patrol stance on the very units the player is about to command, right
 * before the vanilla command is issued. The click is never swallowed, so the actual move/attack
 * command still fires normally.
 *
 * <p>Middle-click (queue/waypoint) commands are intentionally NOT touched, since that is how patrol
 * routes are built.
 */
public class PatrolCancelMod extends Mod implements InputProcessor{
    /** When true, this mod is running as a bundled component inside Neon. */
    public static boolean bekBundled = false;



    @Override
    public void init(){
        // run before every other processor, so the stance is cleared before the vanilla command fires
        Core.input.getInputMultiplexer().addProcessor(0, this);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){
        // Only the plain right-click (move/attack command) cancels patrol;
        // the middle-button queue command keeps patrol so patrol routes can still be built.
        if(button == KeyCode.mouseRight && isCommandClick()){
            cancelPatrolOnSelected();
        }

        // Never swallow the event: the vanilla input handler must still issue the actual command.
        return false;
    }

    /** Mirrors the conditions under which the vanilla desktop input issues a command on right-click. */
    private boolean isCommandClick(){
        if(!Vars.state.isGame()) return false;
        if(Core.scene.hasMouse()) return false;
        if(Vars.player == null || Vars.player.dead()) return false;

        var input = Vars.control.input;
        return input != null && input.commandMode;
    }

    /** Disables the patrol stance on all selected commandable units that are currently patrolling. */
    private void cancelPatrolOnSelected(){
        var input = Vars.control.input;
        var selected = input.selectedUnits;
        if(selected == null || selected.isEmpty()) return;

        Seq<Unit> patrolling = selected.select(u ->
            u != null && u.isValid() && u.isCommandable() && u.command().hasStance(UnitStance.patrol)
        );

        if(patrolling.isEmpty()) return;

        int[] ids = new int[patrolling.size];
        for(int i = 0; i < ids.length; i++){
            ids[i] = patrolling.get(i).id;
        }

        Call.setUnitStance(Vars.player, ids, UnitStance.patrol, false);
    }

    /** This bundled module has no configurable settings. */
    public void bekBuildSettings(SettingsMenuDialog.SettingsTable table){
    }
}