package com.willfp.eco.core.config.interfaces;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for configs that physically exist as files in plugins.
 */
public interface LoadableConfig extends Config {
    /**
     * Create the file.
     */
    void createFile();

    /**
     * Get resource path as relative to base directory.
     *
     * @return The resource path.
     */
    String getResourcePath();

    /**
     * Save the config.
     * <p>
     * Blocks until the file is written; on the main or a region thread prefer {@link #saveAsync()}.
     *
     * @throws IOException If error in saving.
     */
    void save() throws IOException;

    /**
     * Save the config asynchronously.
     * <p>
     * The call to use from the main or a region thread: the content is captured now and
     * written durably on a background thread, and repeated saves of a file are coalesced.
     */
    default void saveAsync() {
        // This default implementation exists purely for backwards compatibility
        // with legacy Config implementations that don't have saveAsync().
        // Default eco implementations of Config have saveAsync() implemented.
        new Thread(() -> {
            try {
                this.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Get the config file.
     *
     * @return The file.
     */
    File getConfigFile();

    /**
     * Get the config name (including extension).
     *
     * @return The name.
     */
    String getName();

    /**
     * Convert the config to a bukkit {@link YamlConfiguration}.
     */
    @NotNull
    YamlConfiguration toBukkit();
}
