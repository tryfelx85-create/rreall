package com.arena.spawn;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets fighters with the UHC kit build (blocks, webs, water, lava) but only
 * inside the match: every block they place or spill (including flowing liquid)
 * is remembered, fighters may only break/scoop those, and the arena is
 * restored to how it was when the match ends.
 */
public class ArenaBlockListener implements Listener {

    private static final Map<Location, BlockState> tracked = new LinkedHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!MatchManager.isFighting(e.getPlayer().getUniqueId())) return;
        tracked.putIfAbsent(e.getBlock().getLocation(), e.getBlockReplacedState());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!MatchManager.isFighting(e.getPlayer().getUniqueId())) return;
        tracked.putIfAbsent(e.getBlock().getLocation(), e.getBlock().getState());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        if (MatchManager.isFighting(p.getUniqueId()) && !tracked.containsKey(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (MatchManager.isFighting(p.getUniqueId()) && !tracked.containsKey(e.getBlock().getLocation())) {
            e.setCancelled(true);
            p.sendMessage("§cYou can only break blocks placed during this match.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!MatchManager.isMatchActive()) return;
        if (tracked.containsKey(e.getBlock().getLocation())) {
            tracked.putIfAbsent(e.getToBlock().getLocation(), e.getToBlock().getState());
        }
    }

    /** Puts every touched block back to its original state (newest first). */
    public static void restoreAll() {
        List<BlockState> states = new ArrayList<>(tracked.values());
        tracked.clear();
        for (int i = states.size() - 1; i >= 0; i--) {
            states.get(i).update(true, false);
        }
    }
}
