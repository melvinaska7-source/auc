package ru.privatenull.market;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.model.MarketListing;
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.util.NumberParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarketSearch {

    private MarketSearch() {
    }

    public static String extractName(MarketListing listing, ItemLocalization localization) {
        if (listing == null || listing.item() == null) return null;
        return extractName(listing.item(), localization);
    }

    public static String extractName(ItemStack item, ItemLocalization localization) {
        if (item == null || localization == null) return null;

        String fromLocalization = localization.getPlainName(item);
        if (fromLocalization != null && !fromLocalization.isEmpty()) {
            String stripped = ChatColor.stripColor(fromLocalization).trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }

        String mat = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (mat.isEmpty()) return null;
        return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
    }

    public static boolean matches(MarketListing listing, String query, ItemLocalization localization) {
        if (query == null || query.isEmpty()) return true;
        if (listing == null || listing.item() == null) return false;

        String q = query.toLowerCase(Locale.ROOT);

        try {
            double requestedPrice = NumberParser.parse(q);
            double listingPrice = listing.pricePerUnit() * listing.amount();
            double tolerance = Math.max(0.001, Math.abs(requestedPrice) * 0.000001);
            if (Math.abs(listingPrice - requestedPrice) <= tolerance) return true;
        } catch (NumberFormatException ignored) {
            // A normal text query continues through localized name and material matching.
        }

        String name = extractName(listing, localization);
        if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }

        String mat = listing.item().getType().name().toLowerCase(Locale.ROOT);
        return mat.contains(q);
    }

    public static List<String> tabComplete(Collection<MarketListing> listings, String prefix,
                                           ItemLocalization localization) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (MarketListing listing : listings) {
            if (listing == null || listing.item() == null) continue;
            String name = extractName(listing, localization);
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(p)) {
                result.add(name);
                if (result.size() >= 20) break;
            }
        }
        return new ArrayList<>(result);
    }
}
