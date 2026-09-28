package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.model.DeliveryEntry;
import ru.privatenull.market.FavoriteFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

public interface MarketStorage extends AutoCloseable {
    default MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                 int amount, long createdAt) throws IOException {
        return create(sellerId, item, pricePerUnit, amount, createdAt, Long.MAX_VALUE);
    }

    MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit, int amount,
                         long createdAt, long expiresAt) throws IOException;

    List<MarketListing> findAll();

    /** Returns active listings created after the supplied moment without loading market history. */
    List<MarketListing> findActiveCreatedAfter(long createdAfter, long now);

    List<MarketListing> findBySeller(UUID sellerId);

    Optional<MarketListing> findById(String id);

    boolean hasActiveListings(UUID sellerId);

    int countActiveListings(UUID sellerId);

    void delete(String id);

    void updateAmount(String id, int amount);

    void updateStatus(String id, String status);

    void relist(String id, long createdAt, long expiresAt);

    Optional<PurchaseReservation> reserve(String id, int requestedAmount);

    void rollbackReservation(String id, int quantity);

    void finalizeReservation(PurchaseReservation reservation);

    Map<UUID, Map<Boolean, List<FavoriteFilter>>> loadAll();
    void save(UUID playerId, boolean donate, FavoriteFilter filter);
    void delete(UUID playerId, boolean donate, String filterId);
    void clear(UUID playerId, boolean donate);
    boolean isLegacyMigrationComplete();
    void markLegacyMigrationComplete();

    void queue(UUID playerId, String message);
    List<String> takeAll(UUID playerId);

    List<String> store(UUID playerId, List<ItemStack> items);
    List<DeliveryEntry> find(UUID playerId);
    void delete(UUID playerId, String id);
    void delete(UUID playerId, List<String> ids);

    @Override
    void close();
}
