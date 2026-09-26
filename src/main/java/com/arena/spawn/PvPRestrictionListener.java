package com.arena.spawn;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PvPRestrictionListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        boolean victimFighting = MatchManager.isFighting(victim.getUniqueId());
        boolean attackerFighting = MatchManager.isFighting(attacker.getUniqueId());

        // Only the two fighters may hurt each other, and only once the fight has really begun
        if (!victimFighting || !attackerFighting
                || !MatchManager.isFightStarted() || MatchManager.isPaused()) {
            event.setCancelled(true);
        }
    }
}
