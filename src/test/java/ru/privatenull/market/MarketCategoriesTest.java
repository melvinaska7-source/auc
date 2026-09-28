package ru.privatenull.market;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketCategoriesTest {
    @Test
    void preservesConfiguredCategoryOrder() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("categories.blocks.name", "Блоки");
        config.set("categories.blocks.materials", java.util.List.of("STONE"));
        config.set("categories.tools.name", "Инструменты");
        config.set("categories.tools.material-contains", java.util.List.of("PICKAXE"));

        MarketCategories categories = MarketCategories.load(config, Logger.getLogger("test"));

        assertEquals(java.util.List.of("all", "blocks", "tools"), categories.ids());
        assertEquals("tools", categories.next("blocks"));
        assertEquals("blocks", categories.previous("tools"));
    }
}
