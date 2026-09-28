package ru.privatenull;

import org.junit.jupiter.api.Test;

import ru.privatenull.market.MarketFilter;
import ru.privatenull.model.MarketListing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketFilterTest {
    @Test
    void sortsListingsWithoutDependingOnLocalizedLabels() {
        List<MarketListing> listings = new ArrayList<>(List.of(
                listing("old", 10, 1, 100),
                listing("new", 2, 2, 200)
        ));
        MarketFilter.sortListings(listings, MarketFilter.SortType.NEW_FIRST);
        assertEquals("new", listings.get(0).id());
        MarketFilter.sortListings(listings, MarketFilter.SortType.PRICE_TOTAL_DESC);
        assertEquals("old", listings.get(0).id());
    }

    private MarketListing listing(String id, double unitPrice, int amount, long createdAt) {
        return new MarketListing(id, UUID.randomUUID(), null,
                unitPrice, amount, createdAt, "ACTIVE");
    }
}
