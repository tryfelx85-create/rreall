package com.arena.spawn;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The start button picks the next fight from the queue, but the fight only
 * begins after TryFX confirms it with /agree (see AgreeCommand).
 */
public class StartButtonListener implements Listener {

    // Fighter spawn points for a 1v1 match

    // The one button that triggers a match — placed on the west face of the block at 38, 10, 60

    private final ArenaPlugin plugin;

    /** Fight waiting for TryFX's /agree: nicknames as they are in the queue. */
    private String[] pendingNames;

    public StartButtonListener(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onButtonPress(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!event.getClickedBlock().getType().name().endsWith("_BUTTON")) return;

        // Only react to the designated start button, not every button on the map
        if (event.getClickedBlock().getX() != Settings.buttonX
                || event.getClickedBlock().getY() != Settings.buttonY
                || event.getClickedBlock().getZ() != Settings.buttonZ) {
            return;
        }

        Player presser = event.getPlayer();

        if (MatchManager.isMatchActive()) {
            presser.sendMessage("§cA match is already in progress.");
            return;
        }

        String[] names = TournamentManager.peek();
        if (names == null) {
            presser.sendMessage("§cThe queue is empty. An operator must add a fight with §f/queue add <player1> <player2>§c.");
            return;
        }

        Player[] fighters = resolve(names, presser);
        if (fighters == null) return;

        Player confirmer = Bukkit.getPlayerExact(Settings.ownerNick);
        if (confirmer == null) {
            presser.sendMessage("§c" + Settings.ownerNick + " must be online to confirm the fight.");
            return;
        }

        pendingNames = names;
        presser.sendMessage("§eWaiting for " + Settings.ownerNick + " to confirm the fight...");
        confirmer.sendMessage("§6[Tournament] §eConfirm fight: §f" + fighters[0].getName()
                + " §7[" + TagManager.getTagText(fighters[0].getUniqueId()) + "] §evs §f" + fighters[1].getName()
                + " §7[" + TagManager.getTagText(fighters[1].getUniqueId()) + "]§e. Type §a/agree §eto start.");
    }

    /** Called by /agree. Returns an error message to show, or null if the fight started. */
    public String confirm() {
        if (pendingNames == null) return "There is no fight waiting for confirmation.";
        if (MatchManager.isMatchActive()) return "A match is already in progress.";

        String[] names = pendingNames;
        Player confirmer = Bukkit.getPlayerExact(Settings.ownerNick);
        Player[] fighters = resolve(names, confirmer);
        if (fighters == null) return null; // resolve() already told the confirmer why

        pendingNames = null;
        String[] head = TournamentManager.peek();
        if (head != null && head[0].equalsIgnoreCase(names[0]) && head[1].equalsIgnoreCase(names[1])) {
            TournamentManager.removeFirst();
        }
        startMatch(fighters[0], fighters[1]);
        return null;
    }

    /** Looks up both fighters and checks they can fight; tells `notify` what is wrong otherwise. */
    private Player[] resolve(String[] names, Player notify) {
        Player player1 = Bukkit.getPlayerExact(names[0]);
        Player player2 = Bukkit.getPlayerExact(names[1]);
        if (player1 == null || player2 == null) {
            tell(notify, "§cNext fight can't start, offline: §f"
                    + (player1 == null ? names[0] + " " : "") + (player2 == null ? names[1] : ""));
            return null;
        }
        if (player1.getGameMode() != GameMode.ADVENTURE || player2.getGameMode() != GameMode.ADVENTURE) {
            tell(notify, "§cBoth players of the next fight must be in Adventure mode.");
            return null;
        }
        if (TagManager.isModers(player1.getUniqueId()) || TagManager.isModers(player2.getUniqueId())) {
            tell(notify, "§cModers can't take part in matches.");
            return null;
        }
        if (TagManager.isDefeated(player1.getUniqueId()) || TagManager.isDefeated(player2.getUniqueId())) {
            tell(notify, "§cA defeated player can't be matched into a fight again.");
            return null;
        }
        return new Player[]{player1, player2};
    }

    private void tell(Player p, String msg) {
        if (p != null) p.sendMessage(msg);
    }

    private void startMatch(Player player1, Player player2) {
        World world = player1.getWorld();

        player1.teleport(Settings.at(world, Settings.fighter1));
        player2.teleport(Settings.at(world, Settings.fighter2));

        MatchFlow.resetPlayer(player1);
        MatchFlow.resetPlayer(player2);
        FileLog.write("combat.log", "MATCH START " + player1.getName() + " vs " + player2.getName());

        MatchManager.startMatch(player1.getUniqueId(), player2.getUniqueId());
        VoteManager.reset();
        VoteManager.startTimer(plugin);

        player1.sendMessage("§aMatch started! Choose your kit.");
        player2.sendMessage("§aMatch started! Choose your kit.");

        VoteGUI.open(player1);
        VoteGUI.open(player2);

        for (Player p : world.getPlayers()) {
            if (!p.equals(player1) && !p.equals(player2)) {
                p.sendMessage("§eA match has started between §f" + player1.getName()
                        + "§e and §f" + player2.getName() + "§e.");
            }
        }
    }
}
