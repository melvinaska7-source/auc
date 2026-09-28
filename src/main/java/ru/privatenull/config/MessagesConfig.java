package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class MessagesConfig {
    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        mergeMissingDefaults(file);
    }

    public String message(String key) {
        return message(key, Map.of());
    }

    public String message(String key, Map<String, ?> placeholders) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Отсутствует строка локализации messages.yml: " + key);
            value = "&c[pnMarket] &fMissing message: " + key;
        }
        return ColorUtil.colorize(replace(value, placeholders));
    }

    private void mergeMissingDefaults(File file) {
        var stream = plugin.getResource("messages.yml");
        if (stream == null) return;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            String oldFound = "{prefix} &aПодходящий лот найден: &e{item} &8[&f{time}&8]";
            String oldClick = "&aНажмите, чтобы открыть этот лот";
            if (oldFound.equals(config.getString("notification.favorite-found"))) {
                config.set("notification.favorite-found", defaults.getString("notification.favorite-found"));
                config.set("notification.favorite-found-hover",
                        defaults.getStringList("notification.favorite-found-hover"));
                config.set("notification.favorite-found-more",
                        defaults.getString("notification.favorite-found-more"));
            }
            if (oldClick.equals(config.getString("notification.favorite-found-click"))) {
                config.set("notification.favorite-found-click",
                        defaults.getString("notification.favorite-found-click"));
            }
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(file);
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось дополнить messages.yml новыми строками: " + exception.getMessage());
        }
    }

    public List<String> lines(String key, Map<String, ?> placeholders) {
        return config.getStringList(key).stream()
                .map(value -> replace(value, placeholders))
                .map(ColorUtil::colorize)
                .toList();
    }

    private String replace(String value, Map<String, ?> placeholders) {
        String prefix = config.getString("prefix", "&x&6&8&F&B&3&C[Аукцион] &7»");
        value = value.replace("{prefix}", prefix);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return value;
    }
}
