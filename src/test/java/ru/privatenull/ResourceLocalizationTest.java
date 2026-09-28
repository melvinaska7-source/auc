package ru.privatenull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Sound;
import org.junit.jupiter.api.Test;
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.pnlibrary.localization.MinecraftLocale;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLocalizationTest {
    @Test
    void itemLocalesComeFromPnLibrary() {
        assertTrue(ItemLocalization.load(MinecraftLocale.RU_RU).translations().size() > 5_000);
        assertTrue(ItemLocalization.load(MinecraftLocale.EN_US).translations().size() > 5_000);
    }

    @Test
    void messagesResourceContainsRequiredSections() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("messages.yml");
        assertNotNull(stream);
        var messages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertNotNull(messages.getString("prefix"));
        assertNotNull(messages.getString("error.listing-unavailable"));
        assertTrue(messages.getString("notification.favorite-found", "").contains("{prefix}"));
        assertTrue(messages.getString("notification.favorite-found", "").contains("{item}"));
        assertTrue(messages.getString("notification.favorite-found", "").contains("{time}"));
        assertTrue(!messages.getStringList("notification.favorite-found-hover").isEmpty());
        assertNotNull(messages.getString("notification.auto-buy-purchased"));
        assertNotNull(messages.getString("notification.delivery-waiting"));
        assertNotNull(messages.getString("notification.auto-buy-enter-price"));
        assertNotNull(messages.getString("favorites.filter.material"));
    }

    @Test
    void guiResourceContainsFavoriteSections() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("gui.yml");
        assertNotNull(stream);
        var gui = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertNotNull(gui.getString("favorites.title"));
        assertNotNull(gui.getString("favorites.catalog.title"));
        assertNotNull(gui.getString("delivery.title"));
        assertNotNull(gui.getString("delivery.navigation.previous.base64"));
        assertTrue(!gui.getString("auction-switch.regular.base64", "").isBlank());
        assertTrue(!gui.getString("auction-switch.donate.base64", "").isBlank());
        assertTrue(gui.isConfigurationSection("categories"));
    }

    @Test
    void configContainsVersion102FeatureSettings() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        var config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(config.getInt("sell.kits.max-slots.default") > 0);
        assertTrue(config.getInt("sell.kits.max-slots.vip") > config.getInt("sell.kits.max-slots.default"));
        assertTrue(!config.getStringList("sell.kits.blocked-materials").isEmpty());
        assertTrue(config.getBoolean("price.statistics.enabled"));
        assertTrue(config.getString("localization.locale", "").matches("ru_ru|en_us"));
    }

    @Test
    void soundsResourceContainsEverySoundGroup() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("sounds.yml");
        assertNotNull(stream);
        var sounds = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String path : java.util.List.of(
                "gui.open", "gui.open-low", "gui.close", "gui.click", "gui.click-low", "gui.click-high",
                "gui.page-previous", "gui.page-next",
                "gui.quantity-minus-one", "gui.quantity-minus-ten",
                "gui.quantity-plus-one", "gui.quantity-plus-ten",
                "action.search", "action.listing-created", "action.purchase", "action.seller-sale",
                "action.listing-returned", "action.item-collected", "action.favorite-found",
                "action.favorite-added", "action.favorite-removed", "action.favorite-deleted",
                "error.default", "error.no-money", "machine.open", "machine.select", "machine.error")) {
            assertNotNull(sounds.getString(path + ".type"), path);
            assertTrue(sounds.isDouble(path + ".volume"), path);
            assertTrue(sounds.isDouble(path + ".pitch"), path);
            String type = sounds.getString(path + ".type", "NONE");
            if (!type.equalsIgnoreCase("NONE")) Sound.valueOf(type);
        }
    }
}
