package ru.privatenull.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MarketSnapshot {
    private static final MarketSnapshot EMPTY = new MarketSnapshot(List.of());

    private final List<MarketListing> listings;
    private final Map<String, MarketListing> byId;
    private final Map<UUID, List<MarketListing>> bySeller;

    public MarketSnapshot(List<MarketListing> source) {
        List<MarketListing> copy = List.copyOf(source);
        Map<String, MarketListing> idIndex = new HashMap<>();
        Map<UUID, List<MarketListing>> sellerIndex = new HashMap<>();
        for (MarketListing listing : copy) {
            idIndex.put(listing.id(), listing);
            sellerIndex.computeIfAbsent(listing.sellerId(), ignored -> new ArrayList<>()).add(listing);
        }
        sellerIndex.replaceAll((seller, values) -> List.copyOf(values));
        this.listings = copy;
        this.byId = Collections.unmodifiableMap(idIndex);
        this.bySeller = Collections.unmodifiableMap(sellerIndex);
    }

    public static MarketSnapshot empty() {
        return EMPTY;
    }

    public List<MarketListing> all() {
        return listings;
    }

    public List<MarketListing> bySeller(UUID sellerId) {
        return bySeller.getOrDefault(sellerId, List.of());
    }

    public Optional<MarketListing> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int activeCount(UUID sellerId) {
        return (int) bySeller(sellerId).stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .count();
    }
}
