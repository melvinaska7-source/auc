package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.model.DeliveryEntry;
import ru.privatenull.market.FavoriteFilter;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.database.JdbcDatabase;
import ru.privatenull.pnlibrary.database.JdbcSettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** JDBC market storage. Connections are borrowed per operation from pnLibrary's shared router pool. */
public final class JdbcMarketRepository implements MarketStorage {
    private static final String FAVORITES = "pnmarket_favorites";
    private static final String FAVORITES_META = "pnmarket_favorites_meta";
    private static final String NOTIFICATIONS = "pnmarket_notifications";
    private static final String DELIVERIES = "pnmarket_deliveries";
    private static final String MIGRATION_KEY = "favorites-yaml-migrated";
    private final JdbcDatabase database;
    private final boolean ownsDatabase;
    private final long expiryMillis;
    private final Logger logger;
    private final String tableName;
    private long lastNotificationCreatedAt;

    /** Compatibility constructor for standalone consumers and older tests. */
    public JdbcMarketRepository(String driver, String url, String username, String password,
                                long expiryMillis, Logger logger) {
        this(driver, url, username, password, expiryMillis, logger, "pnmarket_listings");
    }

    /** Compatibility constructor for standalone consumers and older tests. */
    public JdbcMarketRepository(String driver, String url, String username, String password,
                                long expiryMillis, Logger logger, String tableName) {
        this(openLegacyDatabase(driver, url, username, password), true, expiryMillis, logger, tableName);
    }

    public JdbcMarketRepository(JdbcDatabase database, long expiryMillis, Logger logger, String tableName) {
        this(database, false, expiryMillis, logger, tableName);
    }

    private JdbcMarketRepository(JdbcDatabase database, boolean ownsDatabase, long expiryMillis,
                                 Logger logger, String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid table name");
        }
        this.database = database;
        this.ownsDatabase = ownsDatabase;
        this.expiryMillis = expiryMillis;
        this.logger = logger;
        this.tableName = tableName;
        try {
            createTable();
            createSharedTables();
        } catch (SQLException exception) {
            if (ownsDatabase) database.close();
            throw new IllegalStateException("Не удалось открыть SQL-хранилище: " + exception.getMessage(), exception);
        }
    }

    @Override
    public synchronized MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                              int amount, long createdAt, long expiresAt) throws IOException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO " + tableName + " (id, seller, item, price_per_unit, amount, created_at, expires_at, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, sellerId.toString());
            statement.setString(3, encodeItem(item));
            statement.setDouble(4, pricePerUnit);
            statement.setInt(5, amount);
            statement.setLong(6, createdAt);
            statement.setLong(7, expiresAt);
            statement.executeUpdate();
            return new MarketListing(id, sellerId, item.clone(), pricePerUnit, amount, createdAt, expiresAt, "ACTIVE");
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить лот: " + exception.getMessage(), exception);
        }
    }

    @Override
    public synchronized List<MarketListing> findAll() {
        return find("SELECT * FROM " + tableName, null);
    }

    @Override
    public synchronized List<MarketListing> findActiveCreatedAfter(long createdAfter, long now) {
        String sql = "SELECT * FROM " + tableName
                + " WHERE status = 'ACTIVE' AND amount > 0 AND created_at > ?"
                + " AND (expires_at = 0 OR expires_at > ?) ORDER BY created_at DESC";
        List<MarketListing> listings = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, createdAfter);
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) decode(connection, result).ifPresent(listings::add);
            }
            return listings;
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override
    public synchronized List<MarketListing> findBySeller(UUID sellerId) {
        return find("SELECT * FROM " + tableName + " WHERE seller = ?", sellerId.toString());
    }

    @Override
    public synchronized Optional<MarketListing> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return find("SELECT * FROM " + tableName + " WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public synchronized boolean hasActiveListings(UUID sellerId) {
        return countActiveListings(sellerId) > 0;
    }

    @Override
    public synchronized int countActiveListings(UUID sellerId) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE seller = ? AND status = 'ACTIVE' AND amount > 0";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sellerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override
    public synchronized void delete(String id) {
        executeUpdate("DELETE FROM " + tableName + " WHERE id = ?", id);
    }

    @Override
    public synchronized void updateAmount(String id, int amount) {
        executeUpdate("UPDATE " + tableName + " SET amount = ? WHERE id = ?", amount, id);
    }

    @Override
    public synchronized void updateStatus(String id, String status) {
        executeUpdate("UPDATE " + tableName + " SET status = ? WHERE id = ?", status, id);
    }

    @Override
    public synchronized void relist(String id, long createdAt, long expiresAt) {
        executeUpdate("UPDATE " + tableName
                + " SET status = 'ACTIVE', created_at = ?, expires_at = ? WHERE id = ?",
                createdAt, expiresAt, id);
    }

    @Override
    public synchronized Optional<PurchaseReservation> reserve(String id, int requestedAmount) {
        if (id == null || id.isBlank() || requestedAmount <= 0) return Optional.empty();
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<MarketListing> result = findById(id);
            if (result.isEmpty() || !"ACTIVE".equalsIgnoreCase(result.get().status())
                    || result.get().amount() <= 0 || result.get().expiresAt() <= System.currentTimeMillis()) {
                return Optional.empty();
            }
            MarketListing listing = result.get();
            int quantity = Math.min(requestedAmount, listing.amount());
            String sql = "UPDATE " + tableName + " SET amount = amount - ?"
                    + " WHERE id = ? AND status = 'ACTIVE' AND amount = ? AND expires_at > ?";
            try (Connection connection = database.connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, quantity);
                statement.setString(2, id);
                statement.setInt(3, listing.amount());
                statement.setLong(4, System.currentTimeMillis());
                if (statement.executeUpdate() == 1) {
                    return Optional.of(new PurchaseReservation(listing, quantity, listing.amount() - quantity));
                }
            } catch (SQLException exception) {
                throw storageError(exception);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized void rollbackReservation(String id, int quantity) {
        if (id == null || quantity <= 0) return;
        executeUpdate("UPDATE " + tableName + " SET amount = amount + ? WHERE id = ?", quantity, id);
    }

    @Override
    public synchronized void finalizeReservation(PurchaseReservation reservation) {
        if (reservation.remainingAmount() == 0) delete(reservation.listing().id());
    }

    @Override
    public synchronized Map<UUID, Map<Boolean, List<FavoriteFilter>>> loadAll() {
        Map<UUID, Map<Boolean, List<FavoriteFilter>>> loaded = new LinkedHashMap<>();
        String sql = "SELECT player, donate, id, type, value, maximum_price, enchantment, enchantment_level, auto_buy"
                + " FROM " + FAVORITES + " ORDER BY player, donate, id";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                try {
                    UUID player = UUID.fromString(rows.getString("player"));
                    FavoriteFilter filter = new FavoriteFilter(rows.getString("id"),
                            FavoriteFilter.Type.valueOf(rows.getString("type").toUpperCase(Locale.ROOT)),
                            rows.getString("value"), rows.getDouble("maximum_price"),
                            rows.getString("enchantment"), rows.getInt("enchantment_level"),
                            rows.getBoolean("auto_buy"));
                    loaded.computeIfAbsent(player, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(rows.getBoolean("donate"), ignored -> new ArrayList<>()).add(filter);
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // A broken legacy favorite must not block the complete storage.
                }
            }
            return loaded;
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override
    public synchronized void save(UUID playerId, boolean donate, FavoriteFilter filter) {
        if (playerId == null || filter == null || filter.id() == null || filter.id().isBlank()) return;
        String sql = "REPLACE INTO " + FAVORITES
                + " (id, player, donate, type, value, maximum_price, enchantment, enchantment_level, auto_buy)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, filter.id());
            statement.setString(2, playerId.toString());
            statement.setBoolean(3, donate);
            statement.setString(4, filter.type().name());
            statement.setString(5, filter.value());
            statement.setDouble(6, filter.maximumPrice());
            statement.setString(7, filter.enchantment());
            statement.setInt(8, filter.enchantmentLevel());
            statement.setBoolean(9, filter.autoBuy());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override public synchronized void delete(UUID playerId, boolean donate, String filterId) {
        if (playerId != null && filterId != null && !filterId.isBlank()) {
            executeUpdate("DELETE FROM " + FAVORITES + " WHERE id = ? AND player = ? AND donate = ?",
                    filterId, playerId.toString(), donate);
        }
    }

    @Override public synchronized void clear(UUID playerId, boolean donate) {
        if (playerId != null) executeUpdate("DELETE FROM " + FAVORITES + " WHERE player = ? AND donate = ?",
                playerId.toString(), donate);
    }

    @Override public synchronized boolean isLegacyMigrationComplete() {
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM " + FAVORITES_META + " WHERE name = ?")) {
            statement.setString(1, MIGRATION_KEY);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw storageError(exception); }
    }

    @Override public synchronized void markLegacyMigrationComplete() {
        executeUpdate("REPLACE INTO " + FAVORITES_META + " (name, value) VALUES (?, ?)", MIGRATION_KEY, "true");
    }

    @Override public synchronized void queue(UUID playerId, String message) {
        if (playerId == null || message == null || message.isBlank()) return;
        executeUpdate("INSERT INTO " + NOTIFICATIONS + " (id, player, message, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), playerId.toString(), message, nextNotificationCreatedAt());
    }

    @Override public synchronized List<String> takeAll(UUID playerId) {
        if (playerId == null) return List.of();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                List<PendingMessage> selected = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id, message FROM "
                        + NOTIFICATIONS + " WHERE player = ? ORDER BY created_at, id")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) selected.add(new PendingMessage(result.getString("id"), result.getString("message")));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + NOTIFICATIONS + " WHERE id = ?")) {
                    for (PendingMessage message : selected) { statement.setString(1, message.id()); statement.addBatch(); }
                    if (!selected.isEmpty()) statement.executeBatch();
                }
                connection.commit();
                return selected.stream().map(PendingMessage::message).toList();
            } catch (SQLException exception) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                throw exception;
            }
        } catch (SQLException exception) { throw storageError(exception); }
    }

    @Override public synchronized List<String> store(UUID playerId, List<ItemStack> items) {
        List<String> ids = new ArrayList<>();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + DELIVERIES
                    + " (id, player, item_data, created_at) VALUES (?, ?, ?, ?)")) {
                long createdAt = System.currentTimeMillis();
                for (ItemStack item : items) {
                    if (item == null || item.getType().isAir()) continue;
                    String deliveryId = UUID.randomUUID().toString();
                    statement.setString(1, deliveryId);
                    statement.setString(2, playerId.toString());
                    statement.setString(3, ItemStackCodec.encode(item.clone()));
                    statement.setLong(4, createdAt++);
                    statement.addBatch();
                    ids.add(deliveryId);
                }
                statement.executeBatch();
                connection.commit();
                return ids;
            } catch (Exception exception) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                throw exception;
            }
        } catch (Exception exception) { throw new IllegalStateException("Ошибка SQL-доставок: " + exception.getMessage(), exception); }
    }

    @Override public synchronized List<DeliveryEntry> find(UUID playerId) {
        List<DeliveryEntry> deliveries = new ArrayList<>();
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT id, item_data, created_at FROM " + DELIVERIES + " WHERE player = ? ORDER BY created_at, id")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) deliveries.add(new DeliveryEntry(rows.getString("id"),
                        ItemStackCodec.decode(rows.getString("item_data")), rows.getLong("created_at")));
            }
            return deliveries;
        } catch (Exception exception) { throw new IllegalStateException("Ошибка SQL-доставок: " + exception.getMessage(), exception); }
    }

    @Override public synchronized void delete(UUID playerId, String deliveryId) { delete(playerId, List.of(deliveryId)); }

    @Override public synchronized void delete(UUID playerId, List<String> ids) {
        if (playerId == null || ids == null || ids.isEmpty()) return;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + DELIVERIES + " WHERE player = ? AND id = ?")) {
            for (String deliveryId : ids) {
                statement.setString(1, playerId.toString()); statement.setString(2, deliveryId); statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) { throw storageError(exception); }
    }

    @Override
    public synchronized void close() {
        if (ownsDatabase) database.close();
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id VARCHAR(36) PRIMARY KEY, seller VARCHAR(36) NOT NULL, item TEXT NOT NULL, "
                + "price_per_unit DOUBLE NOT NULL, amount INTEGER NOT NULL, created_at BIGINT NOT NULL, "
                + "expires_at BIGINT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL)";
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
            try {
                statement.execute("ALTER TABLE " + tableName + " ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // Existing installations already containing the column need no migration.
            }
            try {
                statement.execute("CREATE INDEX " + tableName + "_active_created"
                        + " ON " + tableName + " (status, created_at)");
            } catch (SQLException ignored) {
                // Existing installations already containing the index need no migration.
            }
        }
    }

    private void createSharedTables() throws SQLException {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + FAVORITES + " (id VARCHAR(36) PRIMARY KEY, "
                    + "player VARCHAR(36) NOT NULL, donate BOOLEAN NOT NULL, type VARCHAR(16) NOT NULL, value TEXT NOT NULL, "
                    + "maximum_price DOUBLE NOT NULL, enchantment TEXT NOT NULL, enchantment_level INTEGER NOT NULL, "
                    + "auto_buy BOOLEAN NOT NULL DEFAULT FALSE)");
            try { statement.execute("ALTER TABLE " + FAVORITES + " ADD COLUMN auto_buy BOOLEAN NOT NULL DEFAULT FALSE"); }
            catch (SQLException ignored) { }
            statement.execute("CREATE TABLE IF NOT EXISTS " + FAVORITES_META
                    + " (name VARCHAR(64) PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + NOTIFICATIONS + " (id VARCHAR(36) PRIMARY KEY, "
                    + "player VARCHAR(36) NOT NULL, message TEXT NOT NULL, created_at BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + DELIVERIES + " (id VARCHAR(36) PRIMARY KEY, "
                    + "player VARCHAR(36) NOT NULL, item_data LONGTEXT NOT NULL, created_at BIGINT NOT NULL)");
            createIndex(statement, "pnmarket_favorites_player_donate", FAVORITES, "player, donate");
            createIndex(statement, "pnmarket_notifications_player_created", NOTIFICATIONS, "player, created_at");
            createIndex(statement, "pnmarket_deliveries_player_created", DELIVERIES, "player, created_at");
        }
    }

    private static void createIndex(Statement statement, String name, String table, String columns) {
        try { statement.execute("CREATE INDEX " + name + " ON " + table + " (" + columns + ")"); }
        catch (SQLException ignored) { }
    }

    private long nextNotificationCreatedAt() {
        long now = System.currentTimeMillis();
        lastNotificationCreatedAt = Math.max(now, lastNotificationCreatedAt + 1);
        return lastNotificationCreatedAt;
    }

    private List<MarketListing> find(String sql, String parameter) {
        List<MarketListing> listings = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) decode(connection, result).ifPresent(listings::add);
            }
            return listings;
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    private Optional<MarketListing> decode(Connection connection, ResultSet result) {
        try {
            String id = result.getString("id");
            String status = result.getString("status");
            long createdAt = result.getLong("created_at");
            long storedExpiresAt = result.getLong("expires_at");
            long expiresAt = storedExpiresAt > 0 ? storedExpiresAt : safeAdd(createdAt, expiryMillis);
            if ("ACTIVE".equalsIgnoreCase(status) && System.currentTimeMillis() >= expiresAt) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + tableName + " SET status = 'EXPIRED' WHERE id = ?")) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
                status = "EXPIRED";
            }
            return Optional.of(new MarketListing(id, UUID.fromString(result.getString("seller")),
                    decodeItem(result.getString("item")), result.getDouble("price_per_unit"),
                    result.getInt("amount"), createdAt, expiresAt, status));
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            logger.warning("Пропущен повреждённый SQL-лот: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private void executeUpdate(String sql, Object... values) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                if (value instanceof Integer integer) statement.setInt(index + 1, integer);
                else if (value instanceof Long longValue) statement.setLong(index + 1, longValue);
                else if (value instanceof Boolean bool) statement.setBoolean(index + 1, bool);
                else statement.setString(index + 1, String.valueOf(value));
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    private IllegalStateException storageError(SQLException exception) {
        return new IllegalStateException("Ошибка SQL-хранилища: " + exception.getMessage(), exception);
    }

    private static JdbcDatabase openLegacyDatabase(String driver, String url, String username, String password) {
        try {
            Class.forName(driver);
            DatabaseType type = url != null && url.startsWith("jdbc:mysql:")
                    ? DatabaseType.MYSQL : DatabaseType.SQLITE;
            JdbcDatabase database = new JdbcDatabase(new JdbcSettings(type, url, username, password,
                    type == DatabaseType.SQLITE ? 1 : 10, 10_000L));
            database.open();
            return database;
        } catch (ClassNotFoundException | RuntimeException exception) {
            throw new IllegalStateException("Не удалось открыть SQL-хранилище: " + exception.getMessage(), exception);
        }
    }

    private String encodeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeObject(item);
            data.flush();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private ItemStack decodeItem(String encoded) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object value = data.readObject();
            if (!(value instanceof ItemStack item)) throw new IOException("item data has invalid type");
            return item;
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException("invalid item data", exception);
        }
    }

    private record PendingMessage(String id, String message) { }
}
