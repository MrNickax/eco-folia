package com.willfp.eco.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities / API methods for teams.
 */
public final class TeamUtils {
    /**
     * All chat color teams.
     */
    private static final BiMap<ChatColor, Team> CHAT_COLOR_TEAMS = HashBiMap.create();

    /**
     * Get team from {@link ChatColor}.
     * <p>
     * For {@link org.bukkit.potion.PotionEffectType#GLOWING}. Not supported on Folia.
     *
     * @param color The color to find the team for.
     * @return The team, or null if unavailable.
     */
    @Nullable
    public static Team fromChatColor(@NotNull final ChatColor color) {
        return CHAT_COLOR_TEAMS.get(color);
    }

    private TeamUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
