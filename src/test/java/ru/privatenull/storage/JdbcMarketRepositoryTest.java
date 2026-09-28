package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMarketRepositoryTest {
    @Test
    void sqliteBackendCreatesItsSchema(@TempDir Path directory) {
        try (JdbcMarketRepository repository = new JdbcMarketRepository(
                "org.sqlite.JDBC", "jdbc:sqlite:" + directory.resolve("market.db"), null, null,
                86_400_000L, Logger.getLogger("test"))) {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void filtersBeforeDecodingListings(@TempDir Path directory) throws Exception {
        long now = 1_000_000L;
        String url = "jdbc:sqlite:" + directory.resolve("market.db");
        Logger logger = Logger.getLogger("test-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        AtomicInteger decoded = new AtomicInteger();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage().contains("повреждённый SQL-лот")) decoded.incrementAndGet();
            }

            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try (JdbcMarketRepository repository = new JdbcMarketRepository(
                "org.sqlite.JDBC", url, null, null, 86_400_000L, logger);
             Connection connection = DriverManager.getConnection(url)) {
            insert(connection, now - 20_000L, now + 20_000L);
            insert(connection, now - 2_000L, now + 20_000L);
            insert(connection, now - 1_000L, now - 1L);

            assertTrue(repository.findActiveCreatedAfter(now - 5_000L, now).isEmpty());
            assertEquals(1, decoded.get());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private void insert(Connection connection, long createdAt, long expiresAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pnmarket_listings (id, seller, item, price_per_unit, amount, created_at, expires_at, status)"
                        + " VALUES (?, ?, 'invalid', 1, 1, ?, ?, 'ACTIVE')")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, UUID.randomUUID().toString());
            statement.setLong(3, createdAt);
            statement.setLong(4, expiresAt);
            statement.executeUpdate();
        }
    }
}
