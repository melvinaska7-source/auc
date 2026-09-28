package ru.privatenull.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.model.MarketListing;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.service.MarketStorageFactory;
import ru.privatenull.storage.MarketStorage;

import java.io.File;
import java.util.*;

public final class FavoriteService {
    private final PnMarketPlugin plugin;
    private final File legacyFile;
    private final MarketStorage storage;
    private final Map<UUID, Map<Boolean, List<FavoriteFilter>>> favorites = new LinkedHashMap<>();

    public FavoriteService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.legacyFile = new File(plugin.getDataFolder(), "favorites.yml");
        this.storage = plugin.storageFactory().openFavorites();
        migrateLegacyFile();
        load();
    }

    public synchronized List<FavoriteFilter> list(UUID playerId, boolean donate) {
        return List.copyOf(favorites.getOrDefault(playerId, Map.of()).getOrDefault(donate, List.of()));
    }

    public synchronized AddResult addMaterial(UUID playerId, boolean donate, String rawMaterial) {
        Material material = plugin.itemLocalization().matchMaterial(rawMaterial);
        if (material == null || material.isAir()) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.MATERIAL, material.name(), 0);
    }

    public synchronized AddResult addName(UUID playerId, boolean donate, String rawName) {
        String value = normalize(rawName);
        if (value.length() < 2 || value.length() > 32) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.NAME, value, 0);
    }

    public synchronized AddResult addPrice(UUID playerId, boolean donate, Material material, double price) {
        if (material == null || material.isAir()) return AddResult.INVALID;
        return addPrice(playerId, donate, material.name(), price);
    }

    public synchronized AddResult addPrice(UUID playerId, boolean donate, String itemKey, double price) {
        Material material = plugin.itemLocalization().getKeyMaterial(itemKey);
        if (material == null || material.isAir()) return AddResult.INVALID;
        List<FavoriteFilter> values = mutableList(playerId, donate);
        for (int index = 0; index < values.size(); index++) {
            FavoriteFilter filter = values.get(index);
            if (filter.type() != FavoriteFilter.Type.PRICE
                    || !filter.value().equalsIgnoreCase(itemKey) || filter.hasEnchantment()) continue;
            FavoriteFilter updated = new FavoriteFilter(filter.id(), filter.type(), filter.value(), Math.max(0, price),
                    filter.enchantment(), filter.enchantmentLevel(), filter.autoBuy());
            storage.save(playerId, donate, updated);
            values.set(index, updated);
            return AddResult.UPDATED;
        }
        return add(playerId, donate, FavoriteFilter.Type.PRICE, itemKey, Math.max(0, price));
    }

    public synchronized FavoriteFilter priceFilter(UUID playerId, boolean donate, Material material) {
        if (material == null) return null;
        return priceFilter(playerId, donate, material.name());
    }

    public synchronized FavoriteFilter priceFilter(UUID playerId, boolean donate, String itemKey) {
        return list(playerId, donate).stream()
                .filter(filter -> filter.type() == FavoriteFilter.Type.PRICE
                        && filter.value().equalsIgnoreCase(itemKey)
                        && !filter.hasEnchantment())
                .findFirst().orElse(null);
    }

    public synchronized List<FavoriteFilter> priceFilters(UUID playerId, boolean donate, String itemKey) {
        return list(playerId, donate).stream().filter(filter -> filter.type() == FavoriteFilter.Type.PRICE
                && filter.value().equalsIgnoreCase(itemKey)).toList();
    }

    /** Counts independent profiles for one item: a plain profile and every enchantment variant. */
    public synchronized int profileCount(UUID playerId, boolean donate, String itemKey) {
        return priceFilters(playerId, donate, itemKey).size();
    }

    public synchronized AddResult addEnchantmentProfile(UUID playerId, boolean donate, Material material,
                                                         Map<String, Integer> enchantments, double price) {
        if (material == null || material.isAir() || enchantments == null || enchantments.isEmpty()) {
            return AddResult.INVALID;
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        enchantments.forEach((key, level) -> { if (key != null && !key.isBlank() && level > 0) normalized.put(key, level); });
        if (normalized.isEmpty()) return AddResult.INVALID;
        List<FavoriteFilter> values = mutableList(playerId, donate);
        boolean duplicate = values.stream().anyMatch(filter -> filter.type() == FavoriteFilter.Type.PRICE
                && filter.value().equalsIgnoreCase(material.name()) && filter.enchantments().equals(normalized));
        if (duplicate) return AddResult.DUPLICATE;
        FavoriteFilter filter = new FavoriteFilter(UUID.randomUUID().toString(), FavoriteFilter.Type.PRICE,
                material.name(), Math.max(0, price)).withEnchantments(normalized);
        storage.save(playerId, donate, filter);
        values.add(filter);
        return AddResult.ADDED;
    }

    public synchronized FavoriteFilter enchantmentFilter(UUID playerId, boolean donate, Material material,
                                                          Enchantment enchantment) {
        if (material == null || enchantment == null) return null;
        String key = enchantment.getKey().toString();
        return list(playerId, donate).stream()
                .filter(filter -> filter.type() == FavoriteFilter.Type.PRICE
                        && filter.value().equalsIgnoreCase(material.name())
                        && filter.enchantments().containsKey(key))
                .findFirst().orElse(null);
    }

    public synchronized AddResult setEnchantment(UUID playerId, boolean donate, Material material,
                                                  Enchantment enchantment, int level, double price) {
        if (material == null || material.isAir() || enchantment == null) return AddResult.INVALID;
        FavoriteFilter existing = priceFilter(playerId, donate, material.name());
        Map<String, Integer> conditions = existing == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.enchantments());
        String enchantmentKey = enchantment.getKey().toString();
        if (level <= 0) {
            conditions.remove(enchantmentKey);
            if (existing != null && conditions.isEmpty()) remove(playerId, donate, existing.id());
            else if (existing != null) {
                replaceFilter(playerId, donate, existing, existing.withEnchantments(conditions));
            }
            return AddResult.UPDATED;
        }
        int normalizedLevel = Math.min(Math.max(1, level), Math.max(1, enchantment.getMaxLevel()));
        conditions.put(enchantmentKey, normalizedLevel);
        if (existing != null) {
            replaceFilter(playerId, donate, existing, existing.withEnchantments(conditions));
            return AddResult.UPDATED;
        }
        List<FavoriteFilter> values = mutableList(playerId, donate);
        FavoriteFilter filter = new FavoriteFilter(UUID.randomUUID().toString(), FavoriteFilter.Type.PRICE,
                material.name(), Math.max(0, price)).withEnchantments(conditions);
        storage.save(playerId, donate, filter);
        values.add(filter);
        return AddResult.ADDED;
    }

    public synchronized boolean remove(UUID playerId, boolean donate, String id) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        boolean exists = values.stream().anyMatch(filter -> filter.id().equals(id));
        if (!exists) return false;
        storage.delete(playerId, donate, id);
        values.removeIf(filter -> filter.id().equals(id));
        return true;
    }

    public synchronized void clear(UUID playerId, boolean donate) {
        storage.clear(playerId, donate);
        mutableList(playerId, donate).clear();
    }

    public synchronized boolean toggleAutoBuy(UUID playerId, boolean donate, String filterId) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        for (FavoriteFilter filter : values) {
            if (!filter.id().equals(filterId) || filter.type() != FavoriteFilter.Type.PRICE
                    || filter.maximumPrice() <= 0) continue;
            replaceFilter(playerId, donate, filter, filter.withAutoBuy(!filter.autoBuy()));
            return true;
        }
        return false;
    }

    public synchronized boolean configureAutoBuy(UUID playerId, boolean donate, String filterId, double price) {
        if (price <= 0) return false;
        List<FavoriteFilter> values = mutableList(playerId, donate);
        for (FavoriteFilter filter : values) {
            if (!filter.id().equals(filterId) || filter.type() != FavoriteFilter.Type.PRICE) continue;
            FavoriteFilter updated = new FavoriteFilter(filter.id(), filter.type(), filter.value(), price,
                    filter.enchantment(), filter.enchantmentLevel(), true);
            replaceFilter(playerId, donate, filter, updated);
            return true;
        }
        return false;
    }

    public synchronized void notifyListing(MarketListing listing, boolean donate) {
        if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;
        ItemStack item = listing.item();
        String itemName = plugin.itemLocalization().getPlainName(item);

        for (Map.Entry<UUID, Map<Boolean, List<FavoriteFilter>>> playerEntry : favorites.entrySet()) {
            UUID playerId = playerEntry.getKey();
            if (playerId.equals(listing.sellerId())) continue;
            Player player = plugin.getServer().getPlayer(playerId);
            List<FavoriteFilter> filters = playerEntry.getValue().getOrDefault(donate, List.of());
            List<FavoriteFilter> matched = matchingFilters(filters, item, itemName);
            if (matched.isEmpty()) continue;
            if (matched.stream().anyMatch(filter -> shouldAutoBuy(filter, listing))) {
                plugin.autoPurchase(playerId, listing, donate);
            }
            if (player != null && player.isOnline()) {
                sendListingNotification(player, List.of(listing), donate, matched.get(0));
            }
        }
    }

    /**
     * Rebuilds missed favorite notifications from the live market snapshot. No offline
     * message queue is needed, so unavailable listings can never be delivered later.
     */
    public synchronized void notifyAvailable(Player player, long offlineSince,
                                             List<MarketListing> regularListings,
                                             List<MarketListing> donateListings) {
        if (player == null || !plugin.getConfig().getBoolean("notifications.enabled", true)) return;
        if (offlineSince <= 0) return;
        long now = System.currentTimeMillis();
        Map<NotificationGroup, List<MarketListing>> grouped = new LinkedHashMap<>();

        for (boolean donate : List.of(false, true)) {
            List<FavoriteFilter> filters = favorites.getOrDefault(player.getUniqueId(), Map.of())
                    .getOrDefault(donate, List.of());
            if (filters.isEmpty()) continue;
            List<MarketListing> source = donate ? donateListings : regularListings;
            List<MarketListing> listings = source.stream()
                    .filter(listing -> listing.amount() > 0)
                    .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                    .filter(listing -> listing.createdAt() > offlineSince)
                    .filter(listing -> listing.expiresAt() > now)
                    .filter(listing -> !listing.sellerId().equals(player.getUniqueId()))
                    .sorted(Comparator.comparingLong(MarketListing::createdAt).reversed())
                    .toList();
            for (MarketListing listing : listings) {
                String itemName = plugin.itemLocalization().getPlainName(listing.item());
                List<FavoriteFilter> matched = matchingFilters(filters, listing.item(), itemName);
                if (matched.isEmpty()) continue;
                if (matched.stream().anyMatch(filter -> shouldAutoBuy(filter, listing))) {
                    plugin.autoPurchase(player.getUniqueId(), listing, donate);
                }
                grouped.computeIfAbsent(new NotificationGroup(donate, matched.get(0)), ignored -> new ArrayList<>())
                        .add(listing);
            }
        }
        grouped.forEach((group, listings) -> {
            MarketListing cheapest = listings.stream()
                    .min(Comparator.comparingDouble(MarketListing::pricePerUnit)).orElse(null);
            sendListingNotification(player, listings, group.donate(), group.filter());
        });
    }

    private void sendListingNotification(Player player, List<MarketListing> listings, boolean donate,
                                         FavoriteFilter matched) {
        if (listings.isEmpty()) return;
        MarketListing newest = listings.stream().min(Comparator.comparingDouble(MarketListing::pricePerUnit))
                .orElse(listings.get(0));
        String itemName = plugin.itemLocalization().getPlainName(newest.item());
        String command = (donate ? "/dah" : "/ah") + " view " + newest.id();
        String elapsed = NotificationTimeFormatter.elapsed(newest.createdAt(), System.currentTimeMillis());

        Component message = ColorUtil.component(plugin.messages().message("notification.favorite-found",
                        Map.of("item", itemName, "time", elapsed)))
                .hoverEvent(HoverEvent.showText(notificationHover(listings, donate)))
                .clickEvent(ClickEvent.runCommand(command));
        player.sendMessage(message);
        plugin.playSound(player, "action.favorite-found");
    }

    private Component notificationHover(List<MarketListing> listings, boolean donate) {
        int visible = Math.min(listings.size(), 5);
        List<Component> lines = new ArrayList<>();
        for (int index = 0; index < visible; index++) {
            MarketListing listing = listings.get(index);
            double totalPrice = listing.pricePerUnit() * listing.amount();
            Map<String, Object> values = Map.of(
                    "item", plugin.itemLocalization().getPlainName(listing.item()),
                    "amount", listing.amount(),
                    "unit-price", plugin.formatPrice(donate, listing.pricePerUnit(), null),
                    "total-price", plugin.formatPrice(donate, totalPrice, null));
            plugin.messages().lines("notification.favorite-found-hover", values).stream()
                    .map(ColorUtil::component)
                    .forEach(lines::add);
            if (index + 1 < visible) lines.add(Component.empty());
        }
        if (listings.size() > visible) {
            lines.add(Component.empty());
            lines.add(ColorUtil.component(plugin.messages().message("notification.favorite-found-more",
                    Map.of("amount", listings.size() - visible))));
        }
        lines.add(Component.empty());
        lines.add(ColorUtil.component(plugin.messages().message("notification.favorite-found-click")));
        Component hover = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) hover = hover.append(Component.newline());
            hover = hover.append(lines.get(index));
        }
        return hover;
    }

    private boolean shouldAutoBuy(FavoriteFilter filter, MarketListing listing) {
        return filter.autoBuy() && filter.maximumPrice() > 0
                && listing.pricePerUnit() <= filter.maximumPrice();
    }

    private record NotificationGroup(boolean donate, FavoriteFilter filter) {
    }


    public String filterLabel(FavoriteFilter filter) {
        String key = filter.hasEnchantment() ? "favorites.filter.enchantment" : switch (filter.type()) {
            case MATERIAL -> "favorites.filter.material";
            case NAME -> "favorites.filter.name";
            case PRICE -> "favorites.filter.price";
        };
        return plugin.messages().message(key, Map.of(
                "value", displayValue(filter),
                "enchantment", enchantmentSummary(filter),
                "level", filter.enchantmentLevel(),
                "price", filter.maximumPrice() > 0
                        ? plugin.formatPrice(false, filter.maximumPrice(), null)
                        : plugin.messages().message("favorites.filter.no-price")));
    }

    public String displayValue(FavoriteFilter filter) {
        if (filter.type() == FavoriteFilter.Type.NAME) return filter.value();
        return plugin.itemLocalization().getItemName(filter.value());
    }

    public String enchantmentSummary(FavoriteFilter filter) {
        if (!filter.hasEnchantment()) return "Без условий";
        return filter.enchantments().entrySet().stream().map(entry -> {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
            return plugin.itemLocalization().getEnchantmentName(enchantment) + " " + entry.getValue();
        }).collect(java.util.stream.Collectors.joining(", "));
    }

    private List<FavoriteFilter> matchingFilters(List<FavoriteFilter> filters, ItemStack item, String itemName) {
        List<FavoriteFilter> matches = new ArrayList<>();
        String itemKey = plugin.itemLocalization().getItemKey(item);
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.PRICE
                    && filter.hasEnchantment()
                    && item.getType().name().equalsIgnoreCase(filter.value())
                    && matchesEnchantments(item, filter)) matches.add(filter);
        }
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.PRICE && !filter.hasEnchantment()
                    && itemKey.equalsIgnoreCase(filter.value())) matches.add(filter);
        }
        String normalizedName = normalize(itemName);
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.MATERIAL
                    && item.getType().name().equalsIgnoreCase(filter.value())) matches.add(filter);
            if (filter.type() == FavoriteFilter.Type.NAME
                    && normalizedName.contains(normalize(filter.value()))) matches.add(filter);
        }
        return matches;
    }

    private boolean matchesEnchantments(ItemStack item, FavoriteFilter filter) {
        for (Map.Entry<String, Integer> condition : filter.enchantments().entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(condition.getKey());
            Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
            if (enchantment == null) return false;
            int level = item.getEnchantmentLevel(enchantment);
            if (item.getItemMeta() instanceof EnchantmentStorageMeta stored) {
                level = Math.max(level, stored.getStoredEnchantLevel(enchantment));
            }
            if (level < condition.getValue()) return false;
        }
        return true;
    }

    private AddResult add(UUID playerId, boolean donate, FavoriteFilter.Type type,
                          String value, double maximumPrice) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        if (values.stream().anyMatch(filter -> filter.type() == type
                && filter.value().equalsIgnoreCase(value)
                && (type != FavoriteFilter.Type.PRICE || !filter.hasEnchantment()))) return AddResult.DUPLICATE;
        FavoriteFilter filter = new FavoriteFilter(UUID.randomUUID().toString(), type, value, maximumPrice);
        storage.save(playerId, donate, filter);
        values.add(filter);
        return AddResult.ADDED;
    }

    private void replaceFilter(UUID playerId, boolean donate, FavoriteFilter oldFilter,
                               FavoriteFilter newFilter) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        int index = values.indexOf(oldFilter);
        if (index >= 0) {
            storage.save(playerId, donate, newFilter);
            values.set(index, newFilter);
        }
    }

    private List<FavoriteFilter> mutableList(UUID playerId, boolean donate) {
        return favorites.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(donate, ignored -> new ArrayList<>());
    }

    private void load() {
        favorites.clear();
        storage.loadAll().forEach((playerId, auctions) -> auctions.forEach((donate, loaded) -> {
            List<FavoriteFilter> values = mutableList(playerId, donate);
            values.addAll(loaded);
            deduplicateFilters(values);
        }));
    }

    private void migrateLegacyFile() {
        if (storage.isLegacyMigrationComplete()) return;
        if (!legacyFile.isFile()) {
            storage.markLegacyMigrationComplete();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String uuidValue : players.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidValue);
                    migrateAuction(players.getConfigurationSection(uuidValue + ".vault"), playerId, false);
                    migrateAuction(players.getConfigurationSection(uuidValue + ".donate"), playerId, true);
                } catch (IllegalArgumentException ignored) {
                    // Invalid player entries are ignored.
                }
            }
        }
        storage.markLegacyMigrationComplete();
    }

    private void migrateAuction(ConfigurationSection section, UUID playerId, boolean donate) {
        if (section == null) return;
        List<FavoriteFilter> values = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String typeValue = section.getString(id + ".type", "");
            String value = section.getString(id + ".value", "");
            try {
                FavoriteFilter.Type type = FavoriteFilter.Type.valueOf(typeValue.toUpperCase(Locale.ROOT));
                if (!value.isBlank()) values.add(new FavoriteFilter(id, type, value,
                        section.getDouble(id + ".maximum-price", 0),
                        section.getString(id + ".enchantment", ""),
                        section.getInt(id + ".enchantment-level", 0)));
            } catch (IllegalArgumentException ignored) {
                // Invalid filters do not prevent the plugin from starting.
            }
        }
        deduplicateFilters(values);
        values.forEach(filter -> storage.save(playerId, donate, filter));
    }

    private void deduplicateFilters(List<FavoriteFilter> values) {
        Map<String, FavoriteFilter> unique = new LinkedHashMap<>();
        List<FavoriteFilter> result = new ArrayList<>();
        for (FavoriteFilter filter : values) {
            String key = filter.type() + "|" + filter.value().toLowerCase(Locale.ROOT) + "|"
                    + filter.enchantments().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(";"));
            unique.putIfAbsent(key, filter);
        }
        result.addAll(unique.values());
        values.clear();
        values.addAll(result);
    }

    public synchronized void close() {
        storage.close();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s{2,}", " ");
    }

    public enum AddResult {
        ADDED, UPDATED, DUPLICATE, INVALID
    }
}
