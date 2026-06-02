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
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public final class TeamUtils {

    private static final BiMap<ChatColor, Team> CHAT_COLOR_TEAMS = HashBiMap.create();
    private static boolean supported = true;

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

                if (team == null) {
                    supported = false;
                    CHAT_COLOR_TEAMS.clear();
                    return;
                }

                CHAT_COLOR_TEAMS.forcePut(color, team);
            }
        });
    }

    public static @Nullable Team fromChatColor(@NotNull ChatColor color) {
        if (supported) {
            return CHAT_COLOR_TEAMS.get(color);
        }

        return null;
    }

    public static void addEntry(@NotNull Plugin plugin, @NotNull ChatColor color, @NotNull String entry) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Team team = fromChatColor(color);

            if (team == null) {
                return;
            }

            team.addEntry(entry);
        });
    }

    public static void removeEntry(@NotNull Plugin plugin, @NotNull ChatColor color, @NotNull String entry) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            Team team = fromChatColor(color);

            if (team == null) {
                return;
            }

            team.removeEntry(entry);
        });
    }

    private static @NotNull Scoreboard getMainScoreboard() {
        ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager());
        return manager.getMainScoreboard();
    }

    private static @Nullable Team getOrCreateTeam(@NotNull Scoreboard scoreboard, @NotNull ChatColor color) {
        String name = "EC-" + color.name();

        Team team = scoreboard.getTeam(name);

        if (team == null) {
            try {
                team = scoreboard.registerNewTeam(name);
            } catch (UnsupportedOperationException exception) {
                return null;
            }
        }

        try {
            team.setColor(color);
        } catch (UnsupportedOperationException exception) {
            return null;
        }

        return team;
    }
}