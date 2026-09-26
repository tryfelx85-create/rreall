package com.arena.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Everything that happens after the kit vote: the freeze + countdown, the
 * match time limit, and the single place where a match ends (win, loss by
 * disconnect, time-out, draw).
 */
public class MatchFlow {

    private static ArenaPlugin plugin;
    private static BukkitTask countdownTask;
    private static BukkitTask limitTask;
    private static int secondsLeft;

    public static void init(ArenaPlugin instance) {
        plugin = instance;
    }

    public static int getSecondsLeft() {
        return limitTask != null ? secondsLeft : 0;
    }

    /** Full health/food, no fire, no potion effects. */
    public static void resetPlayer(Player p) {
        if (p == null || p.isDead()) return;
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getType());
        }
    }

    /** Freezes both fighters, counts down 3-2-1, then lets the fight begin. */
    public static void beginCountdown() {
        cancelTasks();
        MatchManager.setFrozen(true);
        countdownTask = new BukkitRunnable() {
            int n = Settings.countdownSeconds;

            @Override
            public void run() {
                if (!MatchManager.isMatchActive()) {
                    cancel();
                    return;
                }
                if (n > 0) {
                    titleToFighters("§e" + n);
                    n--;
                    return;
                }
                cancel();
                countdownTask = null;
                MatchManager.setFrozen(false);
                MatchManager.setFightStarted(true);
                for (Player p : fighters()) {
                    if (p == null) continue;
                    p.sendMessage("§c§lFIGHT!");
                }
                titleToFighters("§c§lFIGHT!");
                startLimitTimer();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private static void startLimitTimer() {
        secondsLeft = Settings.matchTimeLimit;
        if (secondsLeft <= 0) return;
        limitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!MatchManager.isMatchActive()) {
                cancelTasks();
                return;
            }
            if (MatchManager.isPaused()) return;
            if (--secondsLeft <= 0) {
                timeUp();
            }
        }, 20L, 20L);
    }

    private static void timeUp() {
        Player[] f = fighters();
        if (f[0] == null || f[1] == null) {
            draw("Time is up.");
            return;
        }
        double h1 = f[0].getHealth();
        double h2 = f[1].getHealth();
        if (Math.abs(h1 - h2) < 0.01) {
            draw("Time is up and both fighters have equal health - it's a draw.");
        } else if (h1 > h2) {
            finish(f[0], f[1], "time limit, more health");
        } else {
            finish(f[1], f[0], "time limit, more health");
        }
    }

    /** Ends the match with a winner. Either player may be null (offline). */
    public static void finish(Player winner, Player loser, String reason) {
        cancelTasks();

        if (winner != null) winner.sendMessage("§a§lYou won the match!");
        if (loser != null) loser.sendMessage("§c§lYou lost the match.");
        if (winner != null) TagManager.recordWin(winner);
        if (loser != null) TagManager.recordLoss(loser);

        FileLog.write("combat.log", "MATCH END winner=" + name(winner) + " loser=" + name(loser) + " reason=" + reason);

        ArenaBlockListener.restoreAll();
        MatchManager.endMatch();
        VoteManager.reset();
        cleanup(winner);
        cleanup(loser);

        if (winner != null) {
            Bukkit.broadcast(Component.text("§6[Tournament] §f" + winner.getName() + " §ewon the match!"));
            Title title = Title.title(
                    Component.text("§6§l" + winner.getName()),
                    Component.text("§ewins the match!"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
            }
        }
        TournamentManager.notifyNext();
    }

    /** Ends the match with no winner and no tag changes. */
    public static void draw(String message) {
        cancelTasks();
        Player[] f = fighters();
        for (Player p : f) {
            if (p != null) p.sendMessage("§e" + message);
        }
        FileLog.write("combat.log", "MATCH END draw: " + message);

        ArenaBlockListener.restoreAll();
        MatchManager.endMatch();
        VoteManager.reset();
        cleanup(f[0]);
        cleanup(f[1]);
        TournamentManager.notifyNext();
    }

    private static void cleanup(Player p) {
        if (p == null || !p.isOnline()) return;
        Kits.clearPlayer(p);
        if (!p.isDead()) {
            resetPlayer(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(p.getWorld().getSpawnLocation());
        }
    }

    public static void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (limitTask != null) {
            limitTask.cancel();
            limitTask = null;
        }
    }

    private static Player[] fighters() {
        java.util.UUID a = MatchManager.getPlayer1();
        java.util.UUID b = MatchManager.getPlayer2();
        return new Player[]{a != null ? Bukkit.getPlayer(a) : null, b != null ? Bukkit.getPlayer(b) : null};
    }

    private static void titleToFighters(String text) {
        Title title = Title.title(Component.text(text), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
        for (Player p : fighters()) {
            if (p != null) p.showTitle(title);
        }
    }

    private static String name(Player p) {
        return p != null ? p.getName() : "none";
    }
}
