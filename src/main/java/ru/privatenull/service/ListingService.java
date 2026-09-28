package ru.privatenull.service;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.currency.*;
import ru.privatenull.market.*;
import ru.privatenull.model.MarketListing;
import ru.privatenull.storage.MarketStorage;
import ru.privatenull.util.NumberParser;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ListingService {
    private final PnMarketPlugin plugin;
    private final MarketRuntime runtime;
    private final ListingPolicy policy;
    private final MessagesConfig messages;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public ListingService(PnMarketPlugin plugin, MarketRuntime runtime, ListingPolicy policy,
                          MessagesConfig messages) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.policy = policy;
        this.messages = messages;
    }

    public void sell(Player player, String rawPrice, boolean donate) {
        MarketStorage storage = runtime.storage(donate);
        MarketSync sync = runtime.sync(donate);
        if (storage == null || sync == null || runtime.payment(donate) == null) {
            reject(player, "error.purchase-failed");
            return;
        }
        double totalPrice;
        try {
            totalPrice = NumberParser.parse(rawPrice);
            if (donate && runtime.payment(true) instanceof PlayerPointsPayment
                    && (Math.rint(totalPrice) != totalPrice || totalPrice > Integer.MAX_VALUE)) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            reject(player, "error.invalid-price");
            return;
        }
        if (!Double.isFinite(totalPrice) || totalPrice <= 0) {
            reject(player, "error.price-positive");
            return;
        }
        String pricePath = "price.limits." + (donate ? "donate" : "default");
        double minimum = Math.max(0, priceLimit(pricePath + ".min", 1));
        double maximum = Math.max(0, priceLimit(pricePath + ".max", 0));
        if (!validPrice(player, totalPrice, minimum, maximum, donate)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reject(player, "error.item-required");
            return;
        }
        int amount = hand.getAmount();
        if (amount <= 0) {
            reject(player, "error.invalid-amount");
            return;
        }
        if (donate && runtime.payment(true) instanceof PlayerPointsPayment && totalPrice % amount != 0) {
            player.sendMessage("§cЦена за стак должна делиться на количество предметов без остатка.");
            return;
        }
        if (!begin(player, donate, sync)) return;

        MarketPayment payment = runtime.payment(donate);
        double commission = plugin.commissions().listing(player, payment, totalPrice);
        if (commission > 0 && !payment.withdraw(player, commission)) {
            end(player.getUniqueId(), donate);
            player.sendMessage(messages.message("error.commission-funds", Map.of(
                    "commission", plugin.formatPrice(donate, commission, null))));
            return;
        }

        ItemStack stored = hand.clone();
        player.getInventory().setItemInMainHand(null);
        UUID playerId = player.getUniqueId();
        long createdAt = System.currentTimeMillis();
        long expiresAt = policy.expiresAt(player, createdAt);
        async(() -> storage.create(playerId, stored, totalPrice / amount, amount, createdAt, expiresAt), listing -> {
            end(playerId, donate);
            sync.listingCreated(listing);
            runtime.favorites().notifyListing(listing, donate);
            sendListed(player, donate, plugin.itemLocalization().getPlainName(stored), totalPrice, commission);
            plugin.playSound(player, "action.listing-created");
        }, exception -> {
            end(playerId, donate);
            restore(player, List.of(stored));
            refundCommission(player, payment, commission);
            plugin.getLogger().warning("Не удалось создать лот: " + exception.getMessage());
            reject(player, "error.serialization");
        });
    }

    public void sellAuto(Player player, boolean donate) {
        if (!player.hasPermission(configString("sell.auto.permission", "listings.auto.permission", "pnmarket.sell.auto"))) {
            reject(player, "command.no-permission");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reject(player, "error.item-required");
            return;
        }
        List<Double> samples = runtime.activeListings(donate).stream()
                .filter(listing -> listing.item() != null && listing.item().isSimilar(hand))
                .map(MarketListing::pricePerUnit).filter(value -> Double.isFinite(value) && value > 0)
                .sorted().toList();
        int required = Math.max(1, configInt("sell.auto.minimum-samples", "listings.auto.minimum-samples", 1));
        double unitPrice = samples.size() >= required ? statistic(samples)
                : configDouble("sell.auto.fallback-price-per-unit", "listings.auto.fallback-price-per-unit", 0);
        unitPrice *= Math.max(.01, configDouble("sell.auto.multiplier", "listings.auto.multiplier", 1));
        if (!Double.isFinite(unitPrice) || unitPrice <= 0) {
            reject(player, "error.auto-price-unavailable");
            return;
        }
        double total = unitPrice * hand.getAmount();
        if (donate && runtime.payment(true) instanceof PlayerPointsPayment) {
            total = Math.max(hand.getAmount(), Math.round(unitPrice) * hand.getAmount());
        }
        sell(player, NumberParser.compact(total), donate);
    }

    public void sellKit(Player player, String rawPrice, boolean donate, String rawName) {
        MarketStorage storage = runtime.storage(donate);
        MarketSync sync = runtime.sync(donate);
        if (storage == null || sync == null || runtime.payment(donate) == null) {
            player.sendMessage("§cАукцион недоступен.");
            return;
        }
        double totalPrice;
        try {
            totalPrice = NumberParser.parse(rawPrice);
            if (donate && runtime.payment(true) instanceof PlayerPointsPayment
                    && (Math.rint(totalPrice) != totalPrice || totalPrice > Integer.MAX_VALUE)) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            reject(player, "error.invalid-price");
            return;
        }
        if (!Double.isFinite(totalPrice) || totalPrice <= 0) {
            reject(player, "error.price-positive");
            return;
        }
        String pricePath = "price.limits." + (donate ? "donate" : "default");
        if (!validPrice(player, totalPrice,
                Math.max(0, priceLimit(pricePath + ".min", 1)),
                Math.max(0, priceLimit(pricePath + ".max", 0)), donate)) return;
        if (sync.activeCount(player.getUniqueId()) >= policy.listingLimit(player)) {
            listingLimit(player);
            return;
        }

        ItemStack[] inventory = player.getInventory().getStorageContents();
        Map<Integer, ItemStack> source = new LinkedHashMap<>();
        List<String> blocked = blockedMaterials();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack item = inventory[slot];
            if (item == null || item.getType().isAir()) continue;
            if (MarketBundle.isBundle(plugin, item)) {
                player.sendMessage("§cНельзя вложить аукционный набор в другой набор.");
                return;
            }
            if (blocked.contains(item.getType().name())) {
                player.sendMessage("§cПредмет §e" + plugin.itemLocalization().getPlainName(item)
                        + " §cзапрещён внутри наборов.");
                return;
            }
            source.put(slot, item.clone());
        }
        if (source.isEmpty()) {
            player.sendMessage("§cПоложите предметы набора в основной инвентарь.");
            return;
        }
        int maximumSlots = policy.kitSlots(player);
        if (source.size() > maximumSlots) {
            player.sendMessage("§cВ наборе может быть максимум §e" + maximumSlots + "§c заполненных слотов.");
            return;
        }
        List<ItemStack> contents = clones(source.values());
        int serializedSize;
        try {
            serializedSize = MarketBundle.serializedSize(contents);
        } catch (RuntimeException exception) {
            reject(player, "error.serialization");
            return;
        }
        int maxBytes = Math.max(4096, configInt("sell.kits.max-serialized-bytes",
                "listings.kits.max-serialized-bytes", 131072));
        if (serializedSize > maxBytes) {
            player.sendMessage("§cНабор содержит слишком много данных: §e" + serializedSize + "§c/§e" + maxBytes + " §cбайт.");
            return;
        }
        runtime.gui(donate).openBundleCreatePreview(player, totalPrice, sanitize(rawName), source, serializedSize);
    }

    public boolean confirmKit(Player player, boolean donate, String name, double totalPrice,
                              Map<Integer, ItemStack> source) {
        MarketStorage storage = runtime.storage(donate);
        MarketSync sync = runtime.sync(donate);
        if (storage == null || sync == null || !begin(player, donate, sync)) return false;
        ItemStack[] inventory = player.getInventory().getStorageContents();
        for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.length || inventory[slot] == null
                    || !inventory[slot].equals(entry.getValue())) {
                end(player.getUniqueId(), donate);
                player.sendMessage("§cСодержимое инвентаря изменилось. Создайте предпросмотр набора заново.");
                return false;
            }
        }
        List<ItemStack> contents = clones(source.values());
        if (contents.isEmpty() || contents.size() > policy.kitSlots(player)
                || contents.stream().anyMatch(item -> MarketBundle.isBundle(plugin, item)
                || blockedMaterials().contains(item.getType().name()))) {
            end(player.getUniqueId(), donate);
            player.sendMessage("§cСодержимое набора больше не проходит проверку.");
            return false;
        }
        try {
            if (MarketBundle.serializedSize(contents)
                    > Math.max(4096, configInt("sell.kits.max-serialized-bytes",
                    "listings.kits.max-serialized-bytes", 131072))) {
                throw new IllegalArgumentException("bundle too large");
            }
        } catch (RuntimeException exception) {
            end(player.getUniqueId(), donate);
            reject(player, "error.serialization");
            return false;
        }
        ItemStack bundle;
        try {
            bundle = MarketBundle.create(plugin, contents, name);
        } catch (RuntimeException exception) {
            end(player.getUniqueId(), donate);
            reject(player, "error.serialization");
            return false;
        }
        MarketPayment payment = runtime.payment(donate);
        double commission = plugin.commissions().listing(player, payment, totalPrice);
        if (commission > 0 && !payment.withdraw(player, commission)) {
            end(player.getUniqueId(), donate);
            player.sendMessage(messages.message("error.commission-funds", Map.of(
                    "commission", plugin.formatPrice(donate, commission, null))));
            return false;
        }
        source.keySet().forEach(slot -> inventory[slot] = null);
        player.getInventory().setStorageContents(inventory);
        UUID playerId = player.getUniqueId();
        long createdAt = System.currentTimeMillis();
        long expiresAt = policy.expiresAt(player, createdAt);
        async(() -> storage.create(playerId, bundle, totalPrice, 1, createdAt, expiresAt), listing -> {
            end(playerId, donate);
            sync.listingCreated(listing);
            runtime.favorites().notifyListing(listing, donate);
            sendListed(player, donate, name, totalPrice, commission);
            plugin.playSound(player, "action.listing-created");
            runtime.openAuction(player, donate);
        }, exception -> {
            end(playerId, donate);
            restore(player, contents);
            refundCommission(player, payment, commission);
            plugin.getLogger().warning("Не удалось создать лот-набор: " + exception.getMessage());
            reject(player, "error.serialization");
        });
        return true;
    }

    public void relist(Player player, MarketListing listing, boolean donate) {
        MarketStorage storage = runtime.storage(donate);
        MarketSync sync = runtime.sync(donate);
        MarketPayment payment = runtime.payment(donate);
        if (storage == null || sync == null || payment == null || listing == null
                || !listing.sellerId().equals(player.getUniqueId())
                || !"EXPIRED".equalsIgnoreCase(listing.status()) || listing.amount() <= 0) {
            reject(player, "error.listing-unavailable");
            return;
        }
        if (!begin(player, donate, sync)) return;
        double totalPrice = listing.pricePerUnit() * listing.amount();
        double commission = plugin.commissions().listing(player, payment, totalPrice);
        if (commission > 0 && !payment.withdraw(player, commission)) {
            end(player.getUniqueId(), donate);
            player.sendMessage(messages.message("error.commission-funds", Map.of(
                    "commission", plugin.formatPrice(donate, commission, null))));
            return;
        }
        long createdAt = System.currentTimeMillis();
        long expiresAt = policy.expiresAt(player, createdAt);
        async(() -> {
            storage.relist(listing.id(), createdAt, expiresAt);
            return listing.relisted(createdAt, expiresAt);
        }, relisted -> {
            end(player.getUniqueId(), donate);
            sync.listingUpdated(relisted);
            runtime.favorites().notifyListing(relisted, donate);
            String message = commission > 0
                    ? "notification.relisted-with-commission" : "notification.relisted";
            player.sendMessage(messages.message(message, Map.of(
                    "item", plugin.itemLocalization().getPlainName(listing.item()),
                    "price", plugin.formatPrice(donate, totalPrice, null),
                    "commission", plugin.formatPrice(donate, commission, null))));
            plugin.playSound(player, "action.listing-created");
            plugin.renderAllViews();
        }, exception -> {
            end(player.getUniqueId(), donate);
            refundCommission(player, payment, commission);
            plugin.getLogger().warning("Не удалось перевыставить лот " + listing.id() + ": " + exception.getMessage());
            reject(player, "error.serialization");
        });
    }

    private void sendListed(Player player, boolean donate, String item, double price, double commission) {
        String key = commission > 0 ? "notification.listed-with-commission" : "notification.listed";
        player.sendMessage(messages.message(key, Map.of(
                "item", item,
                "price", plugin.formatPrice(donate, price, null),
                "commission", plugin.formatPrice(donate, commission, null))));
    }

    private void refundCommission(Player player, MarketPayment payment, double commission) {
        if (commission <= 0) return;
        if (!payment.deposit(player, commission)) {
            plugin.getLogger().severe("Не удалось вернуть комиссию " + commission + " игроку " + player.getName());
        }
    }

    private boolean validPrice(Player player, double price, double minimum, double maximum, boolean donate) {
        if (price < minimum) {
            player.sendMessage(messages.message("error.price-too-low", Map.of("price",
                    plugin.formatPrice(donate, minimum, null))));
            return false;
        }
        if (maximum > 0 && price > maximum) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price",
                    plugin.formatPrice(donate, maximum, null))));
            return false;
        }
        return true;
    }

    private double priceLimit(String path, double fallback) {
        Object raw = plugin.getConfig().get(path);
        if (raw == null) return fallback;
        try {
            return raw instanceof Number number ? number.doubleValue() : NumberParser.parse(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Некорректный лимит цены " + path + ": " + raw);
            return fallback;
        }
    }

    private double statistic(List<Double> samples) {
        if (configString("sell.auto.algorithm", "listings.auto.algorithm", "median").equalsIgnoreCase("average")) {
            return samples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
        int middle = samples.size() / 2;
        return samples.size() % 2 == 0 ? (samples.get(middle - 1) + samples.get(middle)) / 2 : samples.get(middle);
    }

    private String configString(String path, String legacy, String fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getString(path, fallback) : plugin.getConfig().getString(legacy, fallback);
    }

    private int configInt(String path, String legacy, int fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getInt(path, fallback) : plugin.getConfig().getInt(legacy, fallback);
    }

    private double configDouble(String path, String legacy, double fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getDouble(path, fallback) : plugin.getConfig().getDouble(legacy, fallback);
    }

    private boolean begin(Player player, boolean donate, MarketSync sync) {
        int limit = policy.listingLimit(player);
        if (sync.activeCount(player.getUniqueId()) >= limit
                || !pending.add(player.getUniqueId() + ":" + donate)) {
            listingLimit(player);
            return false;
        }
        return true;
    }

    private void listingLimit(Player player) {
        player.sendMessage(messages.message("error.listing-limit", Map.of("limit", policy.listingLimit(player))));
        plugin.playSound(player, "error.default");
    }

    private void end(UUID playerId, boolean donate) {
        pending.remove(playerId + ":" + donate);
    }

    private List<String> blockedMaterials() {
        String path = plugin.getConfig().contains("sell.kits.blocked-materials")
                ? "sell.kits.blocked-materials" : "listings.kits.blocked-materials";
        return plugin.getConfig().getStringList(path).stream()
                .map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }

    private List<ItemStack> clones(Collection<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    private String sanitize(String raw) {
        String value = raw == null ? "" : ChatColor.stripColor(raw);
        value = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim().replaceAll("\\s{2,}", " ");
        if (value.isEmpty()) value = "Набор";
        return value.length() > 32 ? value.substring(0, 32).trim() : value;
    }

    private <T> void async(Callable<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = operation.call();
                if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> success.accept(result));
            } catch (Throwable throwable) {
                if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> failure.accept(throwable));
            }
        });
    }

    private void restore(Player player, List<ItemStack> items) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(clones(items).toArray(ItemStack[]::new));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void reject(Player player, String key) {
        player.sendMessage(messages.message(key));
        plugin.playSound(player, "error.default");
    }

}
