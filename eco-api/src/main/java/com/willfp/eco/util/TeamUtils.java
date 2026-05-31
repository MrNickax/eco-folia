package com.willfp.eco.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

public final class TeamUtils {

    private static final BiMap<ChatColor, Team> CHAT_COLOR_TEAMS = HashBiMap.create();

    private TeamUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void init(@NotNull Plugin plugin) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Scoreboard scoreboard = getMainScoreboard();

            for (ChatColor color : ChatColor.values()) {
                if (!color.isColor()) {
                    continue;
                }

                Team team = getOrCreateTeam(scoreboard, color);
                CHAT_COLOR_TEAMS.forcePut(color, team);
            }
        });
    }

    public static @NotNull Team fromChatColor(@NotNull ChatColor color) {
        Team team = CHAT_COLOR_TEAMS.get(color);

        if (team != null) {
            return team;
        }

        throw new IllegalStateException(
                "TeamUtils has not been initialized yet, or color is not registered: " + color.name()
        );
    }

    public static void addEntry(@NotNull Plugin plugin, @NotNull ChatColor color, @NotNull String entry) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> fromChatColor(color).addEntry(entry));
    }

    public static void removeEntry(@NotNull Plugin plugin, @NotNull ChatColor color, @NotNull String entry) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> fromChatColor(color).removeEntry(entry));
    }

    private static @NotNull Scoreboard getMainScoreboard() {
        ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager());
        return manager.getMainScoreboard();
    }

    private static @NotNull Team getOrCreateTeam(@NotNull Scoreboard scoreboard, @NotNull ChatColor color) {
        String name = "EC-" + color.name();

        Team team = scoreboard.getTeam(name);

        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }

        team.setColor(color);
        return team;
    }
}