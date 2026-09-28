package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.privatenull.market.FavoriteFilter;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFavoriteStorageTest {
    @Test
    void storesProfilesAcrossReopenAndTracksMigration(@TempDir Path directory) {
        String url = "jdbc:sqlite:" + directory.resolve("market.db");
        UUID playerId = UUID.randomUUID();
        FavoriteFilter material = new FavoriteFilter(UUID.randomUUID().toString(),
                FavoriteFilter.Type.MATERIAL, "DIAMOND_SWORD");
        FavoriteFilter price = new FavoriteFilter(UUID.randomUUID().toString(),
                FavoriteFilter.Type.PRICE, "NETHERITE_SWORD", 1250, "minecraft:sharpness", 5, true);
        FavoriteFilter secondProfile = new FavoriteFilter(UUID.randomUUID().toString(),
                FavoriteFilter.Type.PRICE, "NETHERITE_SWORD", 900, "minecraft:unbreaking", 3, false);

        try (JdbcMarketRepository storage = open(url)) {
            assertFalse(storage.isLegacyMigrationComplete());
            storage.save(playerId, false, material);
            storage.save(playerId, true, price);
            storage.save(playerId, true, secondProfile);
            storage.markLegacyMigrationComplete();
        }

        try (JdbcMarketRepository storage = open(url)) {
            assertTrue(storage.isLegacyMigrationComplete());
            var values = storage.loadAll();
            assertEquals(1, values.get(playerId).get(false).size());
            assertEquals(material, values.get(playerId).get(false).get(0));
            assertEquals(2, values.get(playerId).get(true).size());
            assertTrue(values.get(playerId).get(true).contains(price));
            assertTrue(values.get(playerId).get(true).contains(secondProfile));

            storage.delete(playerId, false, material.id());
            storage.clear(playerId, true);
            assertTrue(storage.loadAll().isEmpty());
        }
    }

    private JdbcMarketRepository open(String url) {
        return new JdbcMarketRepository("org.sqlite.JDBC", url, null, null,
                86_400_000L, Logger.getLogger("test"), "favorite_test_listings");
    }
}
