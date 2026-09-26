package com.arena.spawn;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Right-side sidebar HUD shown to everyone while a match is running:
 * both fighters' names, HP and ping. Uses the main scoreboard so the
 * tag prefixes (which live there too) keep working.
 */
public class FightHud {

    private static final String OBJECTIVE = "arena_hud";

    public static void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, FightHud::update, 0L, 5L);
    }

    private static final java.util.Set<UUID> suffixed = new java.util.HashSet<>();

    public static void clearSuffixes() {
        for (UUID id : suffixed) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) TagManager.setSuffix(p, "");
        }
        suffixed.clear();
    }

    public static void remove() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective old = board.getObjective(OBJECTIVE);
        if (old != null) old.unregister();
    }

    private static void update() {
        UUID id1 = MatchManager.getPlayer1();
        UUID id2 = MatchManager.getPlayer2();
        Player p1 = id1 != null ? Bukkit.getPlayer(id1) : null;
        Player p2 = id2 != null ? Bukkit.getPlayer(id2) : null;

        if (!MatchManager.isMatchActive() || p1 == null || p2 == null) {
            remove();
            clearSuffixes();
            return;
        }

        for (Player p : new Player[]{p1, p2}) {
            TagManager.setSuffix(p, " §c❤" + String.format("%.1f", Math.max(0, p.getHealth())));
            suffixed.add(p.getUniqueId());
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        remove();
        Objective obj = board.registerNewObjective(OBJECTIVE, "dummy", Component.text("§c§l⚔ FIGHT ⚔"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("§e" + p1.getName()).setScore(6);
        obj.getScore(statLine(p1, "§1")).setScore(5);
        obj.getScore("§r").setScore(4);
        obj.getScore("§b" + p2.getName()).setScore(3);
        obj.getScore(statLine(p2, "§2")).setScore(2);

        int left = MatchFlow.getSecondsLeft();
        if (left > 0 && MatchManager.isFightStarted()) {
            obj.getScore(String.format("§8⏱ §7%d:%02d", left / 60, left % 60)).setScore(1);
        }
    }

    private static String statLine(Player p, String uniq) {
        double hp = Math.max(0, p.getHealth());
        return uniq + "§c❤ " + String.format("%.1f", hp) + "§7/" + (int) p.getMaxHealth()
                + " §8| §a" + p.getPing() + "ms";
    }
}
