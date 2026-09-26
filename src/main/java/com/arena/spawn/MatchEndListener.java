package com.arena.spawn;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Ends the match when a fighter dies or disconnects (a disconnect counts as a loss). */
public class MatchEndListener implements Listener {

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        UUID loserId = loser.getUniqueId();
        if (!MatchManager.isFighting(loserId)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        Player winner = opponent(loserId);
        MatchFlow.finish(winner, loser, "death");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        UUID quitterId = quitter.getUniqueId();
        if (!MatchManager.isFighting(quitterId)) return;

        Player winner = opponent(quitterId);
        if (winner != null) {
            winner.sendMessage("§eYour opponent disconnected and forfeits the match.");
        }
        MatchFlow.finish(winner, quitter, "disconnect");
    }

    private Player opponent(UUID id) {
        UUID p1 = MatchManager.getPlayer1();
        UUID p2 = MatchManager.getPlayer2();
        UUID other = id.equals(p1) ? p2 : p1;
        return other != null ? Bukkit.getPlayer(other) : null;
    }
}
