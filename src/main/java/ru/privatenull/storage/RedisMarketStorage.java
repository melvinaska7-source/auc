package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.JedisPooled;
import ru.privatenull.market.FavoriteFilter;
import ru.privatenull.model.DeliveryEntry;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.pnlibrary.database.RedisDatabaseManager;

import java.io.IOException;
import java.util.*;

/** One Redis adapter for listings, favorites, pending messages and deliveries. */
public final class RedisMarketStorage implements MarketStorage {
    private static final String RESERVE_SCRIPT = """
            local status = redis.call('HGET', KEYS[1], 'status')
            local amount = tonumber(redis.call('HGET', KEYS[1], 'amount') or '0')
            local expires = tonumber(redis.call('HGET', KEYS[1], 'expiresAt') or '0')
            local requested = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            if status ~= 'ACTIVE' or amount <= 0 or expires <= now then return {-1, -1} end
            local quantity = math.min(requested, amount)
            local remaining = amount - quantity
            redis.call('HSET', KEYS[1], 'amount', remaining)
            return {quantity, remaining}
            """;
    private static final String TAKE_MESSAGES_SCRIPT = """
            local values = redis.call('LRANGE', KEYS[1], 0, -1)
            redis.call('DEL', KEYS[1])
            return values
            """;

    private final JedisPooled redis;
    private final String listings;
    private final String favoriteIds;
    private final String favoritePrefix;
    private final String pendingPrefix;
    private final String deliveryPrefix;
    private final String migrationKey;

    public RedisMarketStorage(RedisDatabaseManager database, boolean donate) {
        this.redis = database.client();
        this.listings = database.key("listings:" + (donate ? "donate" : "regular"));
        this.favoriteIds = database.key("favorites:ids");
        this.favoritePrefix = database.key("favorite:");
        this.pendingPrefix = database.key("pending:");
        this.deliveryPrefix = database.key("delivery:");
        this.migrationKey = database.key("meta:favorites-yaml-migrated");
    }

    @Override
    public MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit, int amount,
                                long createdAt, long expiresAt) throws IOException {
        String id = UUID.randomUUID().toString();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("seller", sellerId.toString());
        values.put("item", ItemStackCodec.encode(item));
        values.put("price", Double.toString(pricePerUnit));
        values.put("amount", Integer.toString(amount));
        values.put("createdAt", Long.toString(createdAt));
        values.put("expiresAt", Long.toString(expiresAt));
        values.put("status", "ACTIVE");
        redis.hset(listing(id), values);
        redis.sadd(listings, id);
        return new MarketListing(id, sellerId, item.clone(), pricePerUnit, amount, createdAt, expiresAt, "ACTIVE");
    }

    @Override public List<MarketListing> findAll() { return listings(redis.smembers(listings)); }

    @Override
    public List<MarketListing> findActiveCreatedAfter(long createdAfter, long now) {
        return findAll().stream().filter(value -> "ACTIVE".equalsIgnoreCase(value.status()))
                .filter(value -> value.amount() > 0 && value.createdAt() > createdAfter && value.expiresAt() > now)
                .toList();
    }

    @Override
    public List<MarketListing> findBySeller(UUID sellerId) {
        return findAll().stream().filter(value -> value.sellerId().equals(sellerId)).toList();
    }

    @Override public Optional<MarketListing> findById(String id) { return decode(id, redis.hgetAll(listing(id))); }
    @Override public boolean hasActiveListings(UUID sellerId) { return countActiveListings(sellerId) > 0; }
    @Override public int countActiveListings(UUID sellerId) {
        return (int) findBySeller(sellerId).stream().filter(value -> "ACTIVE".equalsIgnoreCase(value.status()))
                .filter(value -> value.amount() > 0 && value.expiresAt() > System.currentTimeMillis()).count();
    }

    @Override public void delete(String id) { redis.del(listing(id)); redis.srem(listings, id); }
    @Override public void updateAmount(String id, int amount) { redis.hset(listing(id), "amount", Integer.toString(amount)); }
    @Override public void updateStatus(String id, String status) { redis.hset(listing(id), "status", status); }
    @Override public void relist(String id, long createdAt, long expiresAt) {
        redis.hset(listing(id), Map.of("createdAt", Long.toString(createdAt),
                "expiresAt", Long.toString(expiresAt), "status", "ACTIVE"));
    }

    @Override
    public Optional<PurchaseReservation> reserve(String id, int requestedAmount) {
        if (id == null || id.isBlank() || requestedAmount <= 0) return Optional.empty();
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<MarketListing> current = findById(id);
            if (current.isEmpty()) return Optional.empty();
            Object raw = redis.eval(RESERVE_SCRIPT, List.of(listing(id)),
                    List.of(Integer.toString(requestedAmount), Long.toString(System.currentTimeMillis())));
            if (!(raw instanceof List<?> values) || values.size() < 2) return Optional.empty();
            int quantity = ((Number) values.get(0)).intValue();
            int remaining = ((Number) values.get(1)).intValue();
            if (quantity > 0) return Optional.of(new PurchaseReservation(current.get(), quantity, remaining));
        }
        return Optional.empty();
    }

    @Override public void rollbackReservation(String id, int quantity) {
        if (id != null && quantity > 0) redis.hincrBy(listing(id), "amount", quantity);
    }
    @Override public void finalizeReservation(PurchaseReservation reservation) {
        if (reservation.remainingAmount() == 0 && "0".equals(redis.hget(listing(reservation.listing().id()), "amount"))) {
            delete(reservation.listing().id());
        }
    }

    @Override
    public Map<UUID, Map<Boolean, List<FavoriteFilter>>> loadAll() {
        Map<UUID, Map<Boolean, List<FavoriteFilter>>> result = new LinkedHashMap<>();
        for (String id : redis.smembers(favoriteIds)) {
            Map<String, String> data = redis.hgetAll(favorite(id));
            try {
                UUID player = UUID.fromString(data.get("player"));
                boolean donate = Boolean.parseBoolean(data.get("donate"));
                FavoriteFilter filter = new FavoriteFilter(id, FavoriteFilter.Type.valueOf(data.get("type")),
                        data.get("value"), number(data.get("maximumPrice"), 0D), data.getOrDefault("enchantment", ""),
                        integer(data.get("enchantmentLevel"), 0), Boolean.parseBoolean(data.get("autoBuy")));
                result.computeIfAbsent(player, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(donate, ignored -> new ArrayList<>()).add(filter);
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    @Override public void save(UUID playerId, boolean donate, FavoriteFilter filter) {
        redis.hset(favorite(filter.id()), Map.of(
                "player", playerId.toString(), "donate", Boolean.toString(donate),
                "type", filter.type().name(), "value", filter.value(),
                "maximumPrice", Double.toString(filter.maximumPrice()),
                "enchantment", Objects.toString(filter.enchantment(), ""),
                "enchantmentLevel", Integer.toString(filter.enchantmentLevel()),
                "autoBuy", Boolean.toString(filter.autoBuy())));
        redis.sadd(favoriteIds, filter.id());
    }

    @Override public void delete(UUID playerId, boolean donate, String filterId) {
        Map<String, String> data = redis.hgetAll(favorite(filterId));
        if (playerId.toString().equals(data.get("player"))
                && Boolean.toString(donate).equals(data.get("donate"))) {
            redis.del(favorite(filterId)); redis.srem(favoriteIds, filterId);
        }
    }
    @Override public void clear(UUID playerId, boolean donate) {
        for (String id : new ArrayList<>(redis.smembers(favoriteIds))) delete(playerId, donate, id);
    }
    @Override public boolean isLegacyMigrationComplete() { return redis.exists(migrationKey); }
    @Override public void markLegacyMigrationComplete() { redis.set(migrationKey, "1"); }

    @Override public void queue(UUID playerId, String message) {
        if (playerId != null && message != null && !message.isBlank()) redis.rpush(pending(playerId), message);
    }
    @Override @SuppressWarnings("unchecked") public List<String> takeAll(UUID playerId) {
        if (playerId == null) return List.of();
        Object raw = redis.eval(TAKE_MESSAGES_SCRIPT, List.of(pending(playerId)), List.of());
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    @Override
    public List<String> store(UUID playerId, List<ItemStack> items) {
        List<String> ids = new ArrayList<>();
        long createdAt = System.currentTimeMillis();
        try {
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                String id = UUID.randomUUID().toString();
                redis.hset(delivery(id), Map.of("player", playerId.toString(), "item", ItemStackCodec.encode(item),
                        "createdAt", Long.toString(createdAt++)));
                redis.rpush(deliveries(playerId), id);
                ids.add(id);
            }
            return ids;
        } catch (Exception exception) {
            delete(playerId, ids);
            throw new IllegalStateException("Ошибка Redis-хранилища доставок", exception);
        }
    }

    @Override public List<DeliveryEntry> find(UUID playerId) {
        List<DeliveryEntry> result = new ArrayList<>();
        for (String id : redis.lrange(deliveries(playerId), 0, -1)) {
            Map<String, String> data = redis.hgetAll(delivery(id));
            try { result.add(new DeliveryEntry(id, ItemStackCodec.decode(data.get("item")),
                    Long.parseLong(data.get("createdAt")))); } catch (Exception ignored) { }
        }
        return result;
    }
    @Override public void delete(UUID playerId, String id) { delete(playerId, List.of(id)); }
    @Override public void delete(UUID playerId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            redis.lrem(deliveries(playerId), 0, id);
            redis.del(delivery(id));
        }
    }

    @Override public void close() { }

    private String listing(String id) { return listings + ":" + id; }
    private String favorite(String id) { return favoritePrefix + id; }
    private String pending(UUID player) { return pendingPrefix + player; }
    private String deliveries(UUID player) { return deliveryPrefix + "ids:" + player; }
    private String delivery(String id) { return deliveryPrefix + "item:" + id; }
    private List<MarketListing> listings(Collection<String> ids) {
        List<MarketListing> result = new ArrayList<>();
        for (String id : ids) decode(id, redis.hgetAll(listing(id))).ifPresent(result::add);
        result.sort(Comparator.comparingLong(MarketListing::createdAt).reversed());
        return result;
    }
    private Optional<MarketListing> decode(String id, Map<String, String> data) {
        if (data == null || data.isEmpty()) return Optional.empty();
        try {
            long createdAt = Long.parseLong(data.get("createdAt"));
            long expiresAt = Long.parseLong(data.get("expiresAt"));
            String status = data.getOrDefault("status", "ACTIVE");
            if ("ACTIVE".equals(status) && expiresAt <= System.currentTimeMillis()) {
                status = "EXPIRED"; redis.hset(listing(id), "status", status);
            }
            return Optional.of(new MarketListing(id, UUID.fromString(data.get("seller")),
                    ItemStackCodec.decode(data.get("item")), Double.parseDouble(data.get("price")),
                    Integer.parseInt(data.get("amount")), createdAt, expiresAt, status));
        } catch (Exception ignored) { return Optional.empty(); }
    }
    private static double number(String value, double fallback) { try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; } }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
}
