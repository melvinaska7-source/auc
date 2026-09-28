package ru.privatenull.market;

import ru.privatenull.model.MarketListing;

import java.util.*;

public final class MarketFilter {

    private MarketFilter() {
    }

    public enum SortType {
        NEW_FIRST,
        OLD_FIRST,
        PRICE_TOTAL_DESC,
        PRICE_TOTAL_ASC,
        PRICE_UNIT_DESC,
        PRICE_UNIT_ASC
    }

    /**
     * Keep the actual click cycle in the same order that is shown in the GUI lore.
     * Otherwise one click can appear to jump over entries even though the enum value
     * itself only changes once.
     */
    public static final List<SortType> SORT_ORDER = Arrays.asList(
            SortType.NEW_FIRST,
            SortType.OLD_FIRST,
            SortType.PRICE_UNIT_DESC,
            SortType.PRICE_UNIT_ASC,
            SortType.PRICE_TOTAL_DESC,
            SortType.PRICE_TOTAL_ASC
    );

    public static SortType nextSort(SortType current) {
        int i = SORT_ORDER.indexOf(current);
        if (i == -1) return SortType.NEW_FIRST;
        i++;
        if (i >= SORT_ORDER.size()) i = 0;
        return SORT_ORDER.get(i);
    }

    public static SortType prevSort(SortType current) {
        int i = SORT_ORDER.indexOf(current);
        if (i == -1) return SortType.NEW_FIRST;
        i--;
        if (i < 0) i = SORT_ORDER.size() - 1;
        return SORT_ORDER.get(i);
    }

    public static void sortListings(List<MarketListing> list, SortType sort) {
        switch (sort) {
            case OLD_FIRST:
                list.sort(Comparator.comparingLong(MarketListing::createdAt));
                break;
            case PRICE_TOTAL_DESC:
                list.sort((a, b) -> Double.compare(b.pricePerUnit() * b.amount(), a.pricePerUnit() * a.amount()));
                break;
            case PRICE_TOTAL_ASC:
                list.sort(Comparator.comparingDouble(l -> l.pricePerUnit() * l.amount()));
                break;
            case PRICE_UNIT_DESC:
                list.sort((a, b) -> Double.compare(b.pricePerUnit(), a.pricePerUnit()));
                break;
            case PRICE_UNIT_ASC:
                list.sort(Comparator.comparingDouble(MarketListing::pricePerUnit));
                break;
            case NEW_FIRST:
            default:
                list.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
                break;
        }
    }
}
