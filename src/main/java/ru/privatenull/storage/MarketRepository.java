package ru.privatenull.storage;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.model.DeliveryEntry;
import ru.privatenull.market.FavoriteFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MarketRepository implements MarketStorage {
    private static final String MIGRATION_KEY = "favorites-yaml-migrated";
    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> favorites;
    private final MongoCollection<Document> metadata;
    private final MongoCollection<Document> notifications;
    private final MongoCollection<Document> deliveries;
    private final long expiryMillis;
    private final Logger logger;
    private long lastNotificationCreatedAt;

    public MarketRepository(MongoDatabase database, String collectionName, String sharedCollectionName,
                            long expiryMillis, Logger logger) {
        Objects.requireNonNull(database, "database");
        this.collection = database.getCollection(collectionName);
        this.favorites = database.getCollection(sharedCollectionName + "_favorites");
        this.metadata = database.getCollection(sharedCollectionName + "_favorites_meta");
        this.notifications = database.getCollection(sharedCollectionName + "_notifications");
        this.deliveries = database.getCollection(sharedCollectionName + "_deliveries");
        this.expiryMillis = expiryMillis;
        this.logger = logger;
        this.collection.createIndex(Indexes.compoundIndex(
                Indexes.ascending("status"), Indexes.descending("createdAt")));
        this.favorites.createIndex(Indexes.compoundIndex(Indexes.ascending("player"), Indexes.ascending("donate")));
        this.notifications.createIndex(Indexes.compoundIndex(Indexes.ascending("player"), Indexes.ascending("createdAt")));
        this.deliveries.createIndex(Indexes.compoundIndex(Indexes.ascending("player"), Indexes.ascending("createdAt")));
    }

    public MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                int amount, long createdAt, long expiresAt) throws IOException {
        String encodedItem = encodeItem(item);
        while (true) {
            String id = UUID.randomUUID().toString();
            Document document = new Document("_id", id)
                    .append("seller", sellerId.toString())
                    .append("item", encodedItem)
                    .append("pricePerUnit", pricePerUnit)
                    .append("amount", amount)
                    .append("createdAt", createdAt)
                    .append("expiresAt", expiresAt)
                    .append("status", "ACTIVE");
            try {
                collection.insertOne(document);
                return new MarketListing(id, sellerId, item.clone(), pricePerUnit, amount, createdAt, expiresAt, "ACTIVE");
            } catch (MongoWriteException exception) {
                if (exception.getError() != null && exception.getError().getCode() == 11000) continue;
                throw exception;
            }
        }
    }

    public List<MarketListing> findAll() {
        List<MarketListing> listings = new ArrayList<>();
        for (Document document : collection.find()) decode(document).ifPresent(listings::add);
        return listings;
    }

    @Override
    public List<MarketListing> findActiveCreatedAfter(long createdAfter, long now) {
        List<MarketListing> listings = new ArrayList<>();
        var filter = Filters.and(
                Filters.eq("status", "ACTIVE"),
                Filters.gt("amount", 0),
                Filters.gt("createdAt", createdAfter),
                Filters.or(
                        Filters.gt("expiresAt", now),
                        Filters.exists("expiresAt", false),
                        Filters.lte("expiresAt", 0)
                ));
        for (Document document : collection.find(filter).sort(Sorts.descending("createdAt"))) {
            decode(document).ifPresent(listings::add);
        }
        return listings;
    }

    public List<MarketListing> findBySeller(UUID sellerId) {
        List<MarketListing> listings = new ArrayList<>();
        for (Document document : collection.find(Filters.eq("seller", sellerId.toString()))) {
            decode(document).ifPresent(listings::add);
        }
        return listings;
    }

    public Optional<MarketListing> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Document document = collection.find(Filters.eq("_id", id)).first();
        return document == null ? Optional.empty() : decode(document);
    }

    public boolean hasActiveListings(UUID sellerId) {
        return collection.countDocuments(Filters.and(
                Filters.eq("seller", sellerId.toString()),
                Filters.eq("status", "ACTIVE"),
                Filters.gt("amount", 0)
        )) > 0;
    }

    public int countActiveListings(UUID sellerId) {
        long count = collection.countDocuments(Filters.and(
                Filters.eq("seller", sellerId.toString()),
                Filters.eq("status", "ACTIVE"),
                Filters.gt("amount", 0)
        ));
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public void delete(String id) {
        collection.deleteOne(Filters.eq("_id", id));
    }

    public void updateAmount(String id, int amount) {
        collection.updateOne(Filters.eq("_id", id),
                new Document("$set", new Document("amount", amount)));
    }

    public void updateStatus(String id, String status) {
        collection.updateOne(Filters.eq("_id", id),
                new Document("$set", new Document("status", status)));
    }

    @Override
    public void relist(String id, long createdAt, long expiresAt) {
        collection.updateOne(Filters.eq("_id", id), Updates.combine(
                Updates.set("status", "ACTIVE"),
                Updates.set("createdAt", createdAt),
                Updates.set("expiresAt", expiresAt)));
    }

    public Optional<PurchaseReservation> reserve(String id, int requestedAmount) {
        if (id == null || id.isBlank() || requestedAmount <= 0) return Optional.empty();
        for (int attempt = 0; attempt < 5; attempt++) {
            Document current = collection.find(Filters.and(
                    Filters.eq("_id", id),
                    Filters.eq("status", "ACTIVE"),
                    Filters.gt("amount", 0),
                    Filters.gt("expiresAt", System.currentTimeMillis())
            )).first();
            if (current == null) return Optional.empty();
            int available = current.getInteger("amount", 0);
            int quantity = Math.min(requestedAmount, available);
            if (quantity <= 0) return Optional.empty();

            Document reserved = collection.findOneAndUpdate(
                    Filters.and(
                            Filters.eq("_id", id),
                            Filters.eq("status", "ACTIVE"),
                            Filters.gte("amount", quantity),
                            Filters.gt("expiresAt", System.currentTimeMillis())
                    ),
                    Updates.inc("amount", -quantity),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE)
            );
            if (reserved == null) continue;
            Optional<MarketListing> listing = decode(reserved);
            if (listing.isEmpty()) {
                rollbackReservation(id, quantity);
                return Optional.empty();
            }
            return Optional.of(new PurchaseReservation(listing.get(), quantity, available - quantity));
        }
        return Optional.empty();
    }

    public void rollbackReservation(String id, int quantity) {
        if (id == null || quantity <= 0) return;
        collection.updateOne(Filters.eq("_id", id), Updates.inc("amount", quantity));
    }

    public void finalizeReservation(PurchaseReservation reservation) {
        if (reservation.remainingAmount() == 0) {
            collection.deleteOne(Filters.and(
                    Filters.eq("_id", reservation.listing().id()),
                    Filters.eq("amount", 0)
            ));
        }
    }

    @Override
    public void close() {
        // The shared MongoClient belongs to pnLibrary's DatabaseRouter.
    }

    @Override public Map<UUID, Map<Boolean, List<FavoriteFilter>>> loadAll() {
        Map<UUID, Map<Boolean, List<FavoriteFilter>>> loaded = new LinkedHashMap<>();
        for (Document document : favorites.find()) {
            try {
                UUID player = UUID.fromString(document.getString("player"));
                Number maximumPrice = document.get("maximumPrice", Number.class);
                Number enchantmentLevel = document.get("enchantmentLevel", Number.class);
                FavoriteFilter filter = new FavoriteFilter(document.getString("_id"),
                        FavoriteFilter.Type.valueOf(document.getString("type").toUpperCase(Locale.ROOT)),
                        document.getString("value"), maximumPrice == null ? 0 : maximumPrice.doubleValue(),
                        document.getString("enchantment"), enchantmentLevel == null ? 0 : enchantmentLevel.intValue(),
                        Boolean.TRUE.equals(document.getBoolean("autoBuy")));
                loaded.computeIfAbsent(player, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(Boolean.TRUE.equals(document.getBoolean("donate")), ignored -> new ArrayList<>())
                        .add(filter);
            } catch (IllegalArgumentException | NullPointerException ignored) { }
        }
        return loaded;
    }

    @Override public void save(UUID playerId, boolean donate, FavoriteFilter filter) {
        if (playerId == null || filter == null || filter.id() == null || filter.id().isBlank()) return;
        Document document = new Document("_id", filter.id()).append("player", playerId.toString())
                .append("donate", donate).append("type", filter.type().name()).append("value", filter.value())
                .append("maximumPrice", filter.maximumPrice()).append("enchantment", filter.enchantment())
                .append("enchantmentLevel", filter.enchantmentLevel()).append("autoBuy", filter.autoBuy());
        favorites.replaceOne(Filters.eq("_id", filter.id()), document, new ReplaceOptions().upsert(true));
    }

    @Override public void delete(UUID playerId, boolean donate, String filterId) {
        if (playerId != null && filterId != null && !filterId.isBlank()) favorites.deleteOne(Filters.and(
                Filters.eq("_id", filterId), Filters.eq("player", playerId.toString()), Filters.eq("donate", donate)));
    }

    @Override public void clear(UUID playerId, boolean donate) {
        if (playerId != null) favorites.deleteMany(Filters.and(
                Filters.eq("player", playerId.toString()), Filters.eq("donate", donate)));
    }

    @Override public boolean isLegacyMigrationComplete() {
        return metadata.find(Filters.eq("_id", MIGRATION_KEY)).first() != null;
    }

    @Override public void markLegacyMigrationComplete() {
        metadata.replaceOne(Filters.eq("_id", MIGRATION_KEY), new Document("_id", MIGRATION_KEY),
                new ReplaceOptions().upsert(true));
    }

    @Override public synchronized void queue(UUID playerId, String message) {
        if (playerId == null || message == null || message.isBlank()) return;
        notifications.insertOne(new Document("_id", UUID.randomUUID().toString())
                .append("player", playerId.toString()).append("message", message)
                .append("createdAt", nextNotificationCreatedAt()));
    }

    @Override public synchronized List<String> takeAll(UUID playerId) {
        if (playerId == null) return List.of();
        List<String> messages = new ArrayList<>();
        FindOneAndDeleteOptions oldest = new FindOneAndDeleteOptions().sort(Sorts.ascending("createdAt", "_id"));
        Document document;
        while ((document = notifications.findOneAndDelete(Filters.eq("player", playerId.toString()), oldest)) != null) {
            String message = document.getString("message");
            if (message != null && !message.isBlank()) messages.add(message);
        }
        return messages;
    }

    @Override public List<String> store(UUID playerId, List<ItemStack> items) {
        List<String> ids = new ArrayList<>();
        List<Document> documents = new ArrayList<>();
        long createdAt = System.currentTimeMillis();
        try {
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                String deliveryId = UUID.randomUUID().toString();
                documents.add(new Document("_id", deliveryId).append("player", playerId.toString())
                        .append("item", ItemStackCodec.encode(item.clone())).append("createdAt", createdAt++));
                ids.add(deliveryId);
            }
            if (!documents.isEmpty()) deliveries.insertMany(documents);
            return ids;
        } catch (Exception exception) {
            if (!ids.isEmpty()) deliveries.deleteMany(Filters.in("_id", ids));
            throw new IllegalStateException("Ошибка MongoDB-доставок: " + exception.getMessage(), exception);
        }
    }

    @Override public List<DeliveryEntry> find(UUID playerId) {
        List<DeliveryEntry> found = new ArrayList<>();
        try {
            for (Document document : deliveries.find(Filters.eq("player", playerId.toString()))
                    .sort(Sorts.ascending("createdAt", "_id"))) {
                found.add(new DeliveryEntry(document.getString("_id"),
                        ItemStackCodec.decode(document.getString("item")), document.getLong("createdAt")));
            }
            return found;
        } catch (Exception exception) {
            throw new IllegalStateException("Ошибка MongoDB-доставок: " + exception.getMessage(), exception);
        }
    }

    @Override public void delete(UUID playerId, String deliveryId) { delete(playerId, List.of(deliveryId)); }

    @Override public void delete(UUID playerId, List<String> ids) {
        if (playerId != null && ids != null && !ids.isEmpty()) deliveries.deleteMany(Filters.and(
                Filters.eq("player", playerId.toString()), Filters.in("_id", ids)));
    }

    private Optional<MarketListing> decode(Document document) {
        String id = document.getString("_id");
        try {
            UUID sellerId = UUID.fromString(document.getString("seller"));
            Number rawPrice = document.get("pricePerUnit", Number.class);
            Number rawCreatedAt = document.get("createdAt", Number.class);
            if (rawPrice == null || rawCreatedAt == null) throw new IllegalArgumentException("missing numeric field");
            int amount = document.getInteger("amount", 0);
            ItemStack item = decodeItem(document.getString("item"));
            String status = document.getString("status");
            if (status == null) status = "ACTIVE";
            long createdAt = rawCreatedAt.longValue();
            Number rawExpiresAt = document.get("expiresAt", Number.class);
            long expiresAt = rawExpiresAt == null || rawExpiresAt.longValue() <= 0
                    ? safeAdd(createdAt, expiryMillis) : rawExpiresAt.longValue();
            if ("ACTIVE".equalsIgnoreCase(status)
                    && System.currentTimeMillis() >= expiresAt) {
                updateStatus(id, "EXPIRED");
                status = "EXPIRED";
            }
            return Optional.of(new MarketListing(
                    id, sellerId, item, rawPrice.doubleValue(), amount, createdAt, expiresAt, status
            ));
        } catch (IOException | IllegalArgumentException | ClassCastException exception) {
            logger.log(Level.WARNING, "Пропущен повреждённый лот MongoDB id=" + safeId(id)
                    + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private long nextNotificationCreatedAt() {
        long now = System.currentTimeMillis();
        lastNotificationCreatedAt = Math.max(now, lastNotificationCreatedAt + 1);
        return lastNotificationCreatedAt;
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
        if (encoded == null || encoded.isBlank()) throw new IOException("missing item data");
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object value = data.readObject();
            if (!(value instanceof ItemStack item)) throw new IOException("item data has invalid type");
            return item;
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException("invalid item data", exception);
        }
    }

    private String safeId(String id) {
        return id == null || id.isBlank() ? "unknown" : id.replaceAll("[^A-Za-z0-9_-]", "?");
    }
}
