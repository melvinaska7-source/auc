package ru.privatenull.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSnapshotTest {
    @Test
    void indexesListingsByIdAndSeller() {
        UUID seller = UUID.randomUUID();
        MarketListing active = listing("active", seller, 2, "ACTIVE");
        MarketListing returned = listing("returned", seller, 1, "RETURNED");
        MarketSnapshot snapshot = new MarketSnapshot(List.of(active, returned));

        assertEquals(2, snapshot.bySeller(seller).size());
        assertEquals(1, snapshot.activeCount(seller));
        assertTrue(snapshot.byId("active").isPresent());
    }

    @Test
    void snapshotDoesNotExposeMutableSourceList() {
        UUID seller = UUID.randomUUID();
        var source = new java.util.ArrayList<>(List.of(listing("one", seller, 1, "ACTIVE")));
        MarketSnapshot snapshot = new MarketSnapshot(source);
        source.clear();
        assertEquals(1, snapshot.all().size());
    }

    private MarketListing listing(String id, UUID seller, int amount, String status) {
        return new MarketListing(id, seller, null, 10, amount, 1, status);
    }
}
