package ru.privatenull.delivery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.model.DeliveryEntry;
import ru.privatenull.service.MarketStorageFactory;
import ru.privatenull.storage.MarketStorage;

import java.util.*;
import java.util.concurrent.*;

public final class DeliveryService implements Listener, AutoCloseable {
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int PAGE_SIZE = CONTENT_SLOTS.length;
    private final PnMarketPlugin plugin;
    private final MarketStorage storage;
    private final GuiConfig gui;
    private final ExecutorService executor;
    private final GuiOpenAnimationService animations;
    private final Set<String> claiming = ConcurrentHashMap.newKeySet();

    public DeliveryService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.gui = plugin.guiConfig();
        this.storage = plugin.storageFactory().openDeliveries();
        this.animations = new GuiOpenAnimationService(plugin, plugin.guiUpdates());
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "pnMarket-deliveries");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<String> store(UUID playerId, List<ItemStack> items) {
        return storage.store(playerId, items);
    }

    public void delete(UUID playerId, List<String> ids) {
        storage.delete(playerId, ids);
    }

    public void open(Player player) {
        load(player, 0, true, -1);
    }

    public void deliverFitting(Player player) {
        submit(() -> {
            List<DeliveryEntry> entries = storage.find(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> deliverFitting(player, entries));
        });
    }

    private void deliverFitting(Player player, List<DeliveryEntry> entries) {
        if (!player.isOnline() || entries.isEmpty()) return;
        List<DeliveryEntry> fitting = new ArrayList<>();
        ItemStack[] simulated = cloneContents(player.getInventory().getStorageContents());
        for (DeliveryEntry entry : entries) {
            if (fit(simulated, entry.item())) fitting.add(entry);
        }
        if (fitting.isEmpty()) {
            player.sendMessage(plugin.messages().message("notification.delivery-waiting",
                    Map.of("amount", entries.size())));
            return;
        }
        List<String> ids = fitting.stream().map(DeliveryEntry::id).toList();
        if (!reserve(ids)) return;
        submit(() -> {
            try {
                storage.delete(player.getUniqueId(), ids);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        claiming.removeAll(ids);
                        submit(() -> storage.store(player.getUniqueId(),
                                fitting.stream().map(DeliveryEntry::item).toList()));
                        return;
                    }
                    List<ItemStack> overflow = give(player, fitting.stream().map(DeliveryEntry::item).toList());
                    claiming.removeAll(ids);
                    if (!overflow.isEmpty()) submit(() -> storage.store(player.getUniqueId(), overflow));
                    int remaining = entries.size() - fitting.size() + overflow.size();
                    player.sendMessage(plugin.messages().message("notification.delivery-received",
                            Map.of("amount", fitting.size() - overflow.size())));
                    plugin.playSound(player, "action.item-collected");
                    if (remaining > 0) player.sendMessage(plugin.messages().message(
                            "notification.delivery-waiting", Map.of("amount", remaining)));
                });
            } catch (RuntimeException exception) {
                claiming.removeAll(ids);
                plugin.getLogger().warning("Не удалось выдать доставку: " + exception.getMessage());
            }
        });
    }

    private void load(Player player, int requestedPage, boolean open, int sourceSlot) {
        submit(() -> {
            List<DeliveryEntry> entries = storage.find(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> render(player, entries, requestedPage, open, sourceSlot));
        });
    }

    private void render(Player player, List<DeliveryEntry> entries, int requestedPage, boolean open, int sourceSlot) {
        if (!player.isOnline()) return;
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        DeliveryView view = new DeliveryView(page);
        view.inventory = Bukkit.createInventory(view, 54, ColorUtil.colorize("&8" + gui.text("delivery.title")));
        decorate(view.inventory);
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            DeliveryEntry entry = entries.get(start + index);
            int slot = CONTENT_SLOTS[index];
            view.inventory.setItem(slot, deliveryIcon(entry.item()));
            view.ids.put(slot, entry.id());
            view.items.put(slot, entry.item().clone());
        }
        view.inventory.setItem(4, gui.item("delivery.info", Material.CHEST, Map.of(
                "amount", entries.size(), "page", page + 1, "pages", pages)));
        view.inventory.setItem(46, page > 0
                ? gui.item("delivery.navigation.previous", Material.PLAYER_HEAD, Map.of("page", page))
                : gui.item("delivery.navigation.previous-disabled", Material.PLAYER_HEAD, Map.of()));
        view.inventory.setItem(49, gui.item("delivery.navigation.close", Material.PLAYER_HEAD, Map.of()));
        view.inventory.setItem(52, page + 1 < pages
                ? gui.item("delivery.navigation.next", Material.PLAYER_HEAD, Map.of("page", page + 2))
                : gui.item("delivery.navigation.next-disabled", Material.PLAYER_HEAD, Map.of()));
        animations.open(player, view.inventory, false, plugin.guiAnimationProfile(), sourceSlot);
        plugin.playSound(player, open ? "gui.open" : "gui.click");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DeliveryView view)
                || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (animations.isAnimating(player)) return;
        if (!Objects.equals(event.getClickedInventory(), event.getView().getTopInventory())) return;
        int slot = event.getRawSlot();
        if (slot == 49) { player.closeInventory(); plugin.playSound(player, "gui.close"); return; }
        if (slot == 46 && view.page > 0) { load(player, view.page - 1, false, slot); return; }
        if (slot == 52) { load(player, view.page + 1, false, slot); return; }
        String id = view.ids.get(slot);
        ItemStack item = view.items.get(slot);
        if (id == null || item == null || !claiming.add(id)) return;
        if (!canFit(player, item)) {
            claiming.remove(id);
            player.sendMessage(plugin.messages().message("notification.delivery-full"));
            plugin.playSound(player, "error.default");
            return;
        }
        submit(() -> {
            try {
                storage.delete(player.getUniqueId(), id);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        claiming.remove(id);
                        submit(() -> storage.store(player.getUniqueId(), List.of(item)));
                        return;
                    }
                    List<ItemStack> overflow = give(player, List.of(item));
                    claiming.remove(id);
                    if (!overflow.isEmpty()) submit(() -> storage.store(player.getUniqueId(), overflow));
                    plugin.playSound(player, "action.item-collected");
                    load(player, view.page, false, -1);
                });
            } catch (RuntimeException exception) {
                claiming.remove(id);
                plugin.getLogger().warning("Не удалось забрать доставку: " + exception.getMessage());
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DeliveryView
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private boolean canFit(Player player, ItemStack item) {
        return fit(cloneContents(player.getInventory().getStorageContents()), item);
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] result = source.clone();
        for (int i = 0; i < result.length; i++) if (result[i] != null) result[i] = result[i].clone();
        return result;
    }

    private static boolean fit(ItemStack[] contents, ItemStack source) {
        int remaining = source.getAmount();
        for (ItemStack stored : contents) {
            if (stored == null || !stored.isSimilar(source)) continue;
            int added = Math.min(stored.getMaxStackSize() - stored.getAmount(), remaining);
            stored.setAmount(stored.getAmount() + added);
            remaining -= added;
            if (remaining == 0) return true;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) continue;
            ItemStack placed = source.clone();
            int added = Math.min(source.getMaxStackSize(), remaining);
            placed.setAmount(added);
            contents[i] = placed;
            remaining -= added;
        }
        return remaining == 0;
    }

    private static List<ItemStack> give(Player player, List<ItemStack> items) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                items.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        return new ArrayList<>(overflow.values());
    }

    private ItemStack deliveryIcon(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.addAll(gui.lore("delivery.item.lore"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void decorate(Inventory inventory) {
        ItemStack black = gui.item("delivery.decor.black", Material.BLACK_STAINED_GLASS_PANE, Map.of());
        ItemStack orange = gui.item("delivery.decor.orange", Material.ORANGE_STAINED_GLASS_PANE, Map.of());
        for (int slot : new int[]{0, 2, 3, 5, 6, 8, 18, 26, 27, 35, 45, 47, 48, 50, 51, 53}) {
            inventory.setItem(slot, black);
        }
        for (int slot : new int[]{1, 7, 9, 17, 36, 44}) inventory.setItem(slot, orange);
        gui.applyDecorations(inventory, "delivery");
    }

    private void submit(Runnable task) {
        try { executor.execute(task); } catch (RejectedExecutionException ignored) { }
    }

    private boolean reserve(List<String> ids) {
        synchronized (claiming) {
            if (ids.stream().anyMatch(claiming::contains)) return false;
            claiming.addAll(ids);
            return true;
        }
    }

    @Override public void close() {
        animations.shutdown();
        executor.shutdown();
        try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        storage.close();
    }

    private static final class DeliveryView implements InventoryHolder {
        private final int page;
        private final Map<Integer, String> ids = new HashMap<>();
        private final Map<Integer, ItemStack> items = new HashMap<>();
        private Inventory inventory;
        private DeliveryView(int page) { this.page = page; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
