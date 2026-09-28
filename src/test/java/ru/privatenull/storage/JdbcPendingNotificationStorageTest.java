package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPendingNotificationStorageTest {
    @Test
    void queueIsDurableAndOrdered(@TempDir Path directory) {
        String url = "jdbc:sqlite:" + directory.resolve("market.db");
        UUID playerId = UUID.randomUUID();

        try (JdbcMarketRepository storage = open(url)) {
            storage.queue(playerId, "first");
            storage.queue(playerId, "second");
            storage.queue(playerId, "third");
        }

        try (JdbcMarketRepository storage = open(url)) {
            assertEquals(List.of("first", "second", "third"), storage.takeAll(playerId));
            assertTrue(storage.takeAll(playerId).isEmpty());
        }
    }

    @Test
    void playersHaveIndependentQueues(@TempDir Path directory) {
        String url = "jdbc:sqlite:" + directory.resolve("market.db");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (JdbcMarketRepository storage = open(url)) {
            storage.queue(first, "one");
            storage.queue(second, "two");
            assertEquals(List.of("one"), storage.takeAll(first));
            assertEquals(List.of("two"), storage.takeAll(second));
        }
    }

    private JdbcMarketRepository open(String url) {
        return new JdbcMarketRepository("org.sqlite.JDBC", url, null, null,
                86_400_000L, Logger.getLogger("test"), "notification_test_listings");
    }
}
