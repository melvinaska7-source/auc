package ru.privatenull.config;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SoundsConfig {
    private final JavaPlugin plugin;
    private final File file;
    private final Set<String> warned = new HashSet<>();
    private YamlConfiguration config;

    public SoundsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sounds.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("sounds.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        warned.clear();
    }

    public void play(Player player, String path) {
        if (player == null || path == null || path.isBlank()) return;
        String type = config.getString(path + ".type", "NONE");
        if (type == null || type.isBlank() || type.equalsIgnoreCase("NONE")) return;
        try {
            Sound sound = Sound.valueOf(type.trim().toUpperCase(Locale.ROOT));
            float volume = clamp(config.getDouble(path + ".volume", 0.2));
            float pitch = clamp(config.getDouble(path + ".pitch", 1.0));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            if (warned.add(path)) {
                plugin.getLogger().warning("Неизвестный звук в sounds.yml, путь " + path + ": " + type);
            }
        }
    }

    public YamlConfiguration configuration() {
        return config;
    }

    private float clamp(double value) {
        return (float) Math.max(0, Math.min(2, value));
    }
}
