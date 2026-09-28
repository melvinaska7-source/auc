package ru.privatenull.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.currency.MarketPayment;
import ru.privatenull.market.MarketFilter;
import ru.privatenull.market.FavoriteFilter;
import ru.privatenull.market.FavoriteService;
import ru.privatenull.market.MarketFilter.SortType;
import ru.privatenull.market.MarketBundle;
import ru.privatenull.market.MarketCategories;
import ru.privatenull.market.MarketSearch;
import ru.privatenull.market.MarketSync;
import ru.privatenull.config.GuiLabels;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.storage.MarketStorage;
import ru.privatenull.util.NumberParser;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.item.HeadUtil;
import ru.privatenull.pnlibrary.compat.BukkitCompat.MaterialCompat;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class MarketGuiController {
    private static final int[] AUCTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] PURCHASE_BLACK_SLOTS = {
            0, 2, 3, 4, 5, 6, 8, 10, 16, 17, 18, 26, 27, 35, 37, 43, 45, 47, 48, 49, 50, 51, 53
    };
    private static final int[] PURCHASE_ORANGE_SLOTS = {
            1, 7, 9, 11, 12, 13, 14, 15, 19, 25, 28, 34, 36, 38, 39, 40, 41, 42, 44, 46, 52
    };
    private static final int[] AUCTION_BLACK_SLOTS = {
            0, 2, 3, 4, 5, 6, 8,
            18, 26, 27, 35, 45,
            47, 48, 49, 50, 51, 53
    };

    private static final int[] AUCTION_ORANGE_SLOTS = {
            1, 7, 9, 17, 36, 44, 46, 52
    };

    private static final int SLOT_BACK_TOP = 21;
    private static final int SLOT_PREVIEW = 22;
    private static final int SLOT_BUY = 23;
    private static final int SLOT_SELLER_HEAD = 31;
    private static final int SLOT_MINUS_1 = 29;
    private static final int SLOT_MINUS_10 = 30;
    private static final int SLOT_PLUS_1 = 32;
    private static final int SLOT_PLUS_10 = 33;
    private static final int SLOT_BACK_BOTTOM = 49;

    private static final int SLOT_BUNDLE_BACK = 49;
    private static final int SLOT_BUNDLE_CREATE_CONFIRM = 45;
    private static final int SLOT_BUNDLE_CREATE_CANCEL = 49;
    private static final int SLOT_BUNDLE_CREATE_INFO = 53;
    private static final int[] BUNDLE_CREATE_CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private static final String BUNDLE_PREVIOUS_TEXTURE = "69ea1d86247f4af351ed1866bca6a3040a06c68177c78e42316a1098e60fb7d3";
    private static final String BUNDLE_NEXT_TEXTURE = "8271a47104495e357c3e8e80f511a9f102b0700ca9b88e88b795d33ff20105eb";
    private static final String BUNDLE_DISABLED_TEXTURE = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065";

    // Prefer the 1.17+/1.19+ icons while retaining valid 1.16.5 fallbacks.
    private static final Material ICON_BUY = MaterialCompat.first("LIME_CANDLE", "LIME_WOOL");
    private static final Material ICON_INFO = MaterialCompat.first("MANGROVE_HANGING_SIGN", "OAK_SIGN");

    private static final int[] SELLER_SLOTS = {
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };

    private final PnMarketPlugin plugin;
    private final MarketStorage repository;
    private final MarketPayment payment;
    private final MessagesConfig messages;
    private final GuiConfig gui;
    private final GuiLabels guiLabels;
    private final MarketGuiLayout layout;
    private final MarketCategories categories;
    private final MarketSync sync;
    private final boolean donateAuction;
    private final GuiOpenAnimationService guiAnimations;
    private final GuiUpdateService guiUpdates;
    private final ExecutorService autoPurchaseQueue;
    private final Map<String, ItemStack> texturedHeadCache = new HashMap<>();
    private final List<NotificationEntry> notificationItems;

    final Map<UUID, AuctionView> auctionViews = new HashMap<>();
    final Map<UUID, PurchaseView> purchaseViews = new HashMap<>();
    final Map<UUID, SellerView> sellerViews = new HashMap<>();
    final Map<UUID, MyItemsView> myItemsViews = new HashMap<>();
    final Map<UUID, BundleCreateView> bundleCreateViews = new HashMap<>();
    final Map<UUID, FavoritesView> favoritesViews = new HashMap<>();
    final Map<UUID, NotificationCatalogView> notificationCatalogViews = new HashMap<>();
    private final Set<UUID> pendingPurchases = new HashSet<>();
    private final Set<String> pendingAutomaticPurchases = new HashSet<>();
    private final Map<String, Deque<AutoPurchaseRequest>> automaticQueues = new HashMap<>();
    private final Set<String> pendingListingActions = new HashSet<>();

    public MarketGuiController(PnMarketPlugin plugin, MarketStorage repository, MarketPayment payment,
                               MessagesConfig messages, GuiConfig gui, GuiLabels guiLabels, MarketCategories categories,
                               MarketSync sync, boolean donateAuction) {
        this.plugin = plugin;
        this.repository = repository;
        this.payment = payment;
        this.messages = messages;
        this.gui = gui;
        this.guiLabels = guiLabels;
        this.layout = MarketGuiLayout.load(gui);
        this.categories = categories;
        this.sync = sync;
        this.donateAuction = donateAuction;
        this.guiUpdates = plugin.guiUpdates();
        this.guiAnimations = new GuiOpenAnimationService(plugin, guiUpdates);
        this.autoPurchaseQueue = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, donateAuction
                    ? "pnMarket-autobuy-donate" : "pnMarket-autobuy-regular");
            thread.setDaemon(true);
            return thread;
        });
        this.notificationItems = loadNotificationItems();
    }

    public void shutdown() {
        guiAnimations.shutdown();
        autoPurchaseQueue.shutdownNow();
        pendingPurchases.clear();
        pendingAutomaticPurchases.clear();
        automaticQueues.clear();
        pendingListingActions.clear();
    }

    private void openGui(Player player, Inventory inventory) {
        guiAnimations.cancel(player);
        guiAnimations.open(player, inventory, false, plugin.guiAnimationProfile(),
                plugin.guiTransitionOrigin(player));
    }

    private void animateRedraw(Player player, Inventory inventory) {
        guiAnimations.cancel(player);
        guiAnimations.open(player, inventory, false, plugin.guiAnimationProfile(),
                plugin.guiTransitionOrigin(player));
    }

    private void applyConfiguredDecorations(Inventory inventory) {
    if (inventory == null) return;
    Object holder = inventory.getHolder();
    String view = null;
    if (holder instanceof AuctionView) view = "auction";
    else if (holder instanceof PurchaseView) view = "purchase";
    else if (holder instanceof SellerView) view = "seller";
    else if (holder instanceof MyItemsView) view = "my-items";
    else if (holder instanceof BundlePreviewView) view = "bundle-preview";
    else if (holder instanceof BundleCreateView) view = "bundle-create";
    else if (holder instanceof FavoritesView) view = "favorites";
    else if (holder instanceof NotificationCatalogView) view = "notification-catalog";
    if (view != null) gui.applyDecorations(inventory, view);
}

    public void openAnimated(Player player, Inventory inventory) {
        openGui(player, inventory);
    }

    public boolean isAnimating(Player player) {
        return guiAnimations.isAnimating(player);
    }

    private void setSlot(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (sameSlotItem(inventory.getItem(slot), item)) return;
        List<Player> viewers = inventory.getViewers().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .filter(viewer -> viewer.getOpenInventory().getTopInventory() == inventory)
                .toList();
        if (viewers.isEmpty()) {
            inventory.setItem(slot, item == null ? null : item.clone());
            return;
        }
        for (Player viewer : viewers) {
            guiAnimations.complete(viewer);
            guiUpdates.setTopSlot(viewer, inventory, slot, item);
        }
    }

    private boolean sameSlotItem(ItemStack current, ItemStack replacement) {
        boolean currentEmpty = current == null || current.getType().isAir();
        boolean replacementEmpty = replacement == null || replacement.getType().isAir();
        if (currentEmpty || replacementEmpty) return currentEmpty == replacementEmpty;
        return current.equals(replacement);
    }

    private MarketSync sync() {
        return sync;
    }

    public List<MarketListing> activeListings() {
        return sync().all().stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList();
    }

    private boolean hasActiveListings(UUID sellerId) {
        return sync().activeCount(sellerId) > 0;
    }

    private String formatPrice(double amount) {
        return plugin.formatPrice(donateAuction, amount, null);
    }

    private String color(String s) {
        return ColorUtil.colorize(s);
    }

    private Component component(String value) {
        return ColorUtil.component(value);
    }

    private void setDisplayName(ItemMeta meta, String value) {
        meta.displayName(ColorUtil.component(value).decoration(TextDecoration.ITALIC, false));
    }

    private String formatTimeRemaining(long expiresAt) {
        long left = expiresAt - System.currentTimeMillis();
        if (left <= 0) return messages.message("time.empty");
        return NumberParser.compactDuration(left);
    }

    private ItemStack createIcon(Material material, String name, String... loreLines) {
        ItemStack i = new ItemStack(material);
        ItemMeta meta = i.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, name);
            if (loreLines != null && loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            i.setItemMeta(meta);
        }
        return hideAttributes(i);
    }

    private ItemStack createIcon(Material material, String name, Collection<String> lore) {
        return createIcon(material, name, lore.toArray(String[]::new));
    }

    private ItemStack createIcon(ItemStack source, String name, Collection<String> lore) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, name);
            meta.setLore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return hideAttributes(item);
    }

    private ItemStack texturedHead(String texture, String name, String... loreLines) {
        String cacheKey = texture + '\u0000' + name + '\u0000' + String.join("\u0000", loreLines);
        ItemStack cached = texturedHeadCache.get(cacheKey);
        if (cached != null) return cached.clone();
        ItemStack head = HeadUtil.create(texture, "&r" + name);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, name);
            meta.setLore(Arrays.asList(loreLines));
            head.setItemMeta(meta);
        }
        head = hideAttributes(head);
        texturedHeadCache.put(cacheKey, head.clone());
        return head;
    }

    private ItemStack texturedHead(String texture, String name, Collection<String> lore) {
        return texturedHead(texture, name, lore.toArray(String[]::new));
    }

    private List<String> buildListingLore(MarketListing listing, int amountForLore) {
        List<String> lore = new ArrayList<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
        String ownerName = seller.getName() != null ? seller.getName() : listing.sellerId().toString();
        double totalPrice = listing.pricePerUnit() * amountForLore;
        String time = formatTimeRemaining(listing.expiresAt());
        lore.addAll(gui.lore("listing.lore", Map.of(
                "seller", ownerName,
                "price", formatPrice(totalPrice),
                "amount", amountForLore,
                "time", time)));
        if (isBundle(listing)) {
            List<ItemStack> contents = bundleItems(listing);
            lore.add(" §7- §fПредметов внутри: §e" + contents.size());
            lore.add(" §7- §fРедкость: " + MarketBundle.rarity(contents).displayName());
        }
        lore.add("");
        return lore;
    }

    private void appendPriceStatistics(Player viewer, List<String> lore, MarketListing listing) {
        if (!plugin.getConfig().getBoolean("price.statistics.enabled", true)) return;
        String permission = plugin.getConfig().getString("price.statistics.permission", "pnmarket.admin");
        if (permission != null && !permission.isBlank() && !viewer.hasPermission(permission)) return;
        if (isBundle(listing)) return;

        List<Double> prices = activeListings().stream()
                .filter(candidate -> !isBundle(candidate))
                .filter(candidate -> candidate.item().isSimilar(listing.item()))
                .map(MarketListing::pricePerUnit)
                .filter(price -> Double.isFinite(price) && price > 0)
                .toList();
        int minimumSamples = Math.max(1,
                plugin.getConfig().getInt("price.statistics.minimum-samples", 1));
        if (prices.size() < minimumSamples) return;
        double minimum = prices.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double average = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        lore.add("§x§D§5§B§F§F§F «Статистика цены за 1 шт.»");
        lore.add(" §7- §fМинимальная: " + formatPrice(minimum));
        lore.add(" §7- §fСредняя: " + formatPrice(average));
        lore.add(" §7- §fЛотов в расчёте: §e" + prices.size());
        lore.add("");
    }

    private void addBundleActionLore(List<String> lore, boolean ownListing) {
        lore.add("§x§F§F§8§7§0§2➥ §fНажмите, §eЛКМ §fчтобы посмотреть содержимое");
        if (ownListing) {
            lore.add("§x§F§F§0§0§0§0➥ §fНажмите, §cПКМ §fчтобы снять с продажи");
        } else {
            lore.add("§x§7§C§F§F§8§0➥ §fНажмите, §aПКМ §fчтобы купить");
        }
    }

    private boolean isBundle(MarketListing listing) {
        return MarketBundle.isBundle(plugin, listing.item());
    }

    private String bundleDisplayName(MarketListing listing) {
        int numericId = Math.floorMod(listing.id().hashCode(), 1_000_000);
        return "§6" + MarketBundle.displayName(listing.item()) + " §8#"
                + String.format(Locale.ROOT, "%06d", numericId);
    }

    private List<ItemStack> bundleItems(MarketListing listing) {
        return MarketBundle.contents(plugin, listing.item());
    }

    private List<ItemStack> deliveryItems(MarketListing listing, int amount) {
        if (isBundle(listing)) return bundleItems(listing);
        ItemStack item = listing.item().clone();
        item.setAmount(amount);
        return List.of(item);
    }

    private boolean canFitAll(Player player, List<ItemStack> items) {
        ItemStack[] simulated = player.getInventory().getStorageContents();
        for (int index = 0; index < simulated.length; index++) {
            if (simulated[index] != null) simulated[index] = simulated[index].clone();
        }

        for (ItemStack source : items) {
            if (source == null || source.getType().isAir()) continue;
            int remaining = source.getAmount();
            for (ItemStack stored : simulated) {
                if (stored == null || !stored.isSimilar(source)) continue;
                int space = stored.getMaxStackSize() - stored.getAmount();
                if (space <= 0) continue;
                int added = Math.min(space, remaining);
                stored.setAmount(stored.getAmount() + added);
                remaining -= added;
                if (remaining == 0) break;
            }
            if (remaining > 0) {
                for (int index = 0; index < simulated.length && remaining > 0; index++) {
                    if (simulated[index] != null && !simulated[index].getType().isAir()) continue;
                    ItemStack placed = source.clone();
                    int added = Math.min(placed.getMaxStackSize(), remaining);
                    placed.setAmount(added);
                    simulated[index] = placed;
                    remaining -= added;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void giveItemsOrDrop(Player player, List<ItemStack> items) {
        ItemStack[] delivery = items.stream()
                .filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(delivery);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public void openAuction(Player player) {
        openAuction(player, MarketCategories.ALL, SortType.NEW_FIRST, null, 0, false);
    }

    public void openAuctionSearch(Player player, String query) {
        openAuction(player, MarketCategories.ALL, SortType.NEW_FIRST, query, 0, true);
    }

    private void openAuction(Player player, String category, SortType sort, String searchQuery, int page, boolean isSearch) {
        UUID uuid = player.getUniqueId();
        AuctionView view = new AuctionView();
        view.controller = this;
        view.viewer = uuid;
        view.category = category;
        view.sort = sort;
        view.searchQuery = searchQuery;
        view.page = page;
        view.isSearch = isSearch;
        view.slotToListingId = new HashMap<>();

        List<MarketListing> filtered = getFilteredListings(view);
        int pageSize = layout.listings().size();
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        if (view.page < 0) view.page = 0;
        if (view.page >= totalPages) view.page = totalPages - 1;

        String baseTitle = (searchQuery != null && !searchQuery.isEmpty())
                ? searchQuery : auctionTitle();
        String title = gui.text("titles.auction-pages", Map.of(
                "name", baseTitle, "page", view.page + 1, "pages", totalPages
        ));

        Inventory inv = Bukkit.createInventory(view, layout.size(), title);
        view.inventory = inv;
        auctionViews.put(uuid, view);

        decorateAuction(inv, isSearch);
        initFilterIcons(view);

        fillAuctionInventory(player, view, false);
        openGui(player, inv);
    }

    private List<MarketListing> getFilteredListings(AuctionView view) {
        List<MarketListing> all = sync().all().stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList();

        List<MarketListing> filtered = new ArrayList<>(all);

        if (view.searchQuery != null && !view.searchQuery.isEmpty()) {
            String q = view.searchQuery.toLowerCase(Locale.ROOT);
            filtered.removeIf(l -> !MarketSearch.matches(l, q, plugin.itemLocalization()));
        }

        if (!view.isSearch && !MarketCategories.ALL.equals(view.category)) {
            filtered.removeIf(l -> !categories.categoryOf(l).equals(view.category));
        }

        MarketFilter.sortListings(filtered, view.sort);
        return filtered;
    }

    private String auctionTitle() {
        return donateAuction ? gui.text("titles.donate-auction") : gui.text("titles.auction");
    }

    private void decorateAuction(Inventory inv, boolean isSearch) {
        ItemStack black = gui.item("auction.decor.black", Material.BLACK_STAINED_GLASS_PANE, Map.of());
        ItemStack orange = gui.item("auction.decor.orange", Material.ORANGE_STAINED_GLASS_PANE, Map.of());

        for (int slot : layout.blackDecor()) {
            setSlot(inv, slot, black);
        }
        for (int slot : layout.orangeDecor()) {
            setSlot(inv, slot, orange);
        }
        applyConfiguredDecorations(inv);

        if (isSearch) {
            ItemStack back = gui.item("search", Material.PLAYER_HEAD, Map.of());
            setSlot(inv, layout.auctionSwitch(), back);
        }
        String myItemsPath = "my-items." + (donateAuction ? "donate" : "regular");
        setSlot(inv, layout.myItems(), gui.item(myItemsPath, Material.BARREL, Map.of()));
    }

    private void initFilterIcons(AuctionView view) {
        if (view.isSearch) {
            updateSortIcon(view);
            setSlot(view.inventory, layout.category(), null);
            return;
        }

        updateCategoryIcon(view, categoryCounts(), activeListings().size());
        updateSortIcon(view);
    }

    void fillAuctionInventory(Player player, AuctionView view) {
        fillAuctionInventory(player, view, true);
    }

    private void fillAuctionInventory(Player player, AuctionView view, boolean updateTitle) {
        Inventory inv = view.inventory;
        view.slotToListingId.clear();

        List<MarketListing> filtered = getFilteredListings(view);

        int pageSize = layout.listings().size();
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);

        if (view.page < 0) view.page = 0;
        if (view.page >= totalPages) view.page = totalPages - 1;

        int startIndex = view.page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filtered.size());

        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = black.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            black.setItemMeta(bm);
        }
        hideAttributes(black);
        setSlot(inv, layout.previous(), gui.item("pagination.previous-disabled", Material.PLAYER_HEAD, Map.of()));
        setSlot(inv, layout.next(), gui.item("pagination.next-disabled", Material.PLAYER_HEAD, Map.of()));

        if (view.page > 0) {
            ItemStack prev = gui.item("pagination.previous", Material.PLAYER_HEAD,
                    Map.of("page", view.page));
            setSlot(inv, layout.previous(), prev);
        }

        if (view.page < totalPages - 1) {
            ItemStack next = gui.item("pagination.next", Material.PLAYER_HEAD,
                    Map.of("page", view.page + 2));
            setSlot(inv, layout.next(), next);
        }

        if (!view.isSearch) {
            updateCategoryIcon(view, categoryCounts(), activeListings().size());
        }
        updateSortIcon(view);

        int index = startIndex;
        for (int slot : layout.listings()) {
            if (index >= endIndex) {
                setSlot(inv, slot, null);
                continue;
            }
            MarketListing listing = filtered.get(index++);
            ItemStack display = listing.item().clone();
            int displayAmount = Math.min(listing.amount(), display.getMaxStackSize());
            if (displayAmount <= 0) displayAmount = 1;
            display.setAmount(displayAmount);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) {
                    setDisplayName(meta, bundleDisplayName(listing));
                } else if (!meta.hasDisplayName()) {
                    setDisplayName(meta, plugin.itemLocalization().getPlainName(listing.item()));
                }
                List<String> lore = isBundle(listing)
                        ? new ArrayList<>()
                        : meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.addAll(buildListingLore(listing, listing.amount()));
                appendPriceStatistics(player, lore, listing);
                boolean ownListing = listing.sellerId().equals(player.getUniqueId());
                if (isBundle(listing)) {
                    addBundleActionLore(lore, ownListing);
                } else if (ownListing) {
                    lore.add(gui.text("hints.collect"));
                } else {
                    lore.add(gui.text("hints.purchase"));
                }
                meta.setLore(lore);
                applyListingDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(inv, slot, display);
            view.slotToListingId.put(slot, listing.id());
        }

        String baseTitle = (view.searchQuery != null && !view.searchQuery.isEmpty())
                ? view.searchQuery : auctionTitle();
        String newTitle = gui.text("titles.auction-pages", Map.of(
                "name", baseTitle, "page", view.page + 1, "pages", totalPages
        ));
        if (updateTitle && !player.getOpenInventory().getTitle().equals(newTitle)) {
            if (!guiUpdates.setTitle(player, inv, newTitle)) {
                Inventory newInv = Bukkit.createInventory(view, layout.size(), newTitle);
                newInv.setContents(inv.getContents());
                view.inventory = newInv;
                openGui(player, newInv);
            }
        }
    }

    private void updateCategoryIcon(AuctionView view, Map<String, Integer> counts, int allCount) {
        if (view.isSearch) {
            setSlot(view.inventory, layout.category(), null);
            return;
        }
        Inventory inv = view.inventory;
        ItemStack item = gui.item("filter.category", ICON_INFO,
                Map.of("category", categories.displayName(view.category)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, gui.text("filter.category.name"));
            List<String> lore = new ArrayList<>(gui.lore("filter.category.lore",
                    Map.of("category", categories.displayName(view.category))));
            for (String category : categories.ids()) {
                String label = categories.displayName(category);
                int count = MarketCategories.ALL.equals(category) ? allCount : counts.getOrDefault(category, 0);
                String prefix = category.equals(view.category) ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + label + " §7(" + count + ")"));
            }
            lore.add("");
            lore.add(gui.text("hints.next"));
            lore.add(gui.text("hints.previous"));
            lore.add("");
            meta.setLore(lore);
            applyDisplayFlags(meta);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        setSlot(inv, layout.category(), item);
    }

    private Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (MarketListing listing : activeListings()) {
            String category = categories.categoryOf(listing);
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return counts;
    }

    private void updateSortIcon(AuctionView view) {
        Inventory inv = view.inventory;
        ItemStack item = gui.item("filter.sort", Material.HOPPER,
                Map.of("sort", guiLabels.sort(view.sort)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String currentName = guiLabels.sort(view.sort);
            setDisplayName(meta, gui.text("filter.sort.name"));
            List<String> lore = new ArrayList<>(gui.lore("filter.sort.lore", Map.of("sort", currentName)));
            List<SortType> types = new ArrayList<>(Arrays.asList(SortType.values()));
            types.sort(Comparator.comparingInt(this::getSortOrderPriority));
            for (SortType type : types) {
                String name = guiLabels.sort(type);
                String prefix = type == view.sort ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + name));
            }
            lore.add("");
            lore.add(gui.text("hints.next"));
            lore.add(gui.text("hints.previous"));
            meta.setLore(lore);
            applyDisplayFlags(meta);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        int sortSlot = layout.sort();
        if (view.isSearch) {
            ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta blackMeta = black.getItemMeta();
            if (blackMeta != null) {
                blackMeta.setDisplayName(" ");
                black.setItemMeta(blackMeta);
            }
            hideAttributes(black);
            setSlot(inv, 52, black);
        }
        setSlot(inv, sortSlot, item);
    }

    private int getSortOrderPriority(SortType type) {
        return switch (type) {
            case NEW_FIRST -> 0;
            case OLD_FIRST -> 1;
            case PRICE_UNIT_DESC -> 2;
            case PRICE_UNIT_ASC -> 3;
            case PRICE_TOTAL_DESC -> 4;
            case PRICE_TOTAL_ASC -> 5;
        };
    }

    private void updateSellerSortIcon(SellerView view) {
        ItemStack item = gui.item("filter.sort", Material.HOPPER,
                Map.of("sort", guiLabels.sort(view.sort)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, gui.text("filter.sort.name"));
            List<String> lore = new ArrayList<>(gui.lore("filter.sort.lore",
                    Map.of("sort", guiLabels.sort(view.sort))));
            List<SortType> types = new ArrayList<>(Arrays.asList(SortType.values()));
            types.sort(Comparator.comparingInt(this::getSortOrderPriority));
            for (SortType type : types) {
                String prefix = type == view.sort ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + guiLabels.sort(type)));
            }
            lore.add("");
            lore.add(gui.text("hints.next"));
            lore.add(gui.text("hints.previous"));
            meta.setLore(lore);
            applyDisplayFlags(meta);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        setSlot(view.inventory, 53, item);
    }

    void fillSellerInventory(SellerView view) {
        view.slotToListingId.clear();
        List<MarketListing> listings = new ArrayList<>(sync().bySeller(view.sellerId).stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList());
        MarketFilter.sortListings(listings, view.sort);
        int index = 0;
        for (int slot : SELLER_SLOTS) {
            if (index >= listings.size()) {
                setSlot(view.inventory, slot, null);
                continue;
            }
            MarketListing listing = listings.get(index++);
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) setDisplayName(meta, bundleDisplayName(listing));
                List<String> lore = new ArrayList<>(buildListingLore(listing, listing.amount()));
                if (isBundle(listing)) addBundleActionLore(lore, false);
                else lore.add(gui.text("hints.purchase"));
                meta.setLore(lore);
                applyListingDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(view.inventory, slot, display);
            view.slotToListingId.put(slot, listing.id());
        }
        updateSellerSortIcon(view);
    }

    private void refreshAuctionForAll() {
        for (Map.Entry<UUID, AuctionView> entry : auctionViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            AuctionView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillAuctionInventory(player, view);
            }
        }
    }

    private void refreshSellerViewsForAll() {
        for (Map.Entry<UUID, SellerView> entry : sellerViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            SellerView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillSellerInventory(view);
            }
        }
    }

    private void refreshMyItemsForAll() {
        for (Map.Entry<UUID, MyItemsView> entry : myItemsViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            MyItemsView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillMyItemsInventory(entry.getKey(), view);
            }
        }
    }

    private void refreshPurchaseViewsForAll() {
        for (Map.Entry<UUID, PurchaseView> entry : new ArrayList<>(purchaseViews.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PurchaseView view = entry.getValue();
            if (player == null || !player.isOnline()
                    || !player.getOpenInventory().getTopInventory().equals(view.inventory)) continue;
            MarketListing fresh = sync().byId(view.listing.id()).orElse(null);
            if (fresh == null || fresh.amount() <= 0 || !"ACTIVE".equalsIgnoreCase(fresh.status())) {
                purchaseViews.remove(entry.getKey());
                player.closeInventory();
                player.sendMessage(messages.message("error.listing-unavailable"));
                continue;
            }
            view.listing = fresh;
            view.maxAmount = fresh.amount();
            view.quantity = Math.max(1, Math.min(view.quantity, view.maxAmount));
            updateQuantityItems(view);
        }
    }

    private void refreshAllViews() {
        sync().refreshAsync();
    }

    public void renderAllViews() {
        refreshAuctionForAll();
        refreshSellerViewsForAll();
        refreshMyItemsForAll();
        refreshPurchaseViewsForAll();
    }

    public void openFavorites(Player player) {
        FavoritesView view = new FavoritesView();
        view.controller = this;
        view.slotToFavoriteId = new HashMap<>();
        view.page = 0;
        view.inventory = Bukkit.createInventory(view, 54,
                ColorUtil.colorize("&8" + gui.text("favorites.title")));
        favoritesViews.put(player.getUniqueId(), view);
        fillFavorites(player, view);
        openGui(player, view.inventory);
    }

    public void openNotificationCatalog(Player player, int page) {
        NotificationCatalogView view = new NotificationCatalogView();
        view.controller = this;
        view.page = Math.max(0, page);
        view.category = NotificationCategory.ALL.id();
        view.mode = NotificationCatalogView.Mode.ITEMS;
        view.slotToItemKey = new HashMap<>();
        view.slotToCategory = new HashMap<>();
        view.slotToEnchantment = new HashMap<>();
        view.inventory = Bukkit.createInventory(view, 54,
                ColorUtil.colorize("&8" + gui.text("favorites.catalog.title")));
        notificationCatalogViews.put(player.getUniqueId(), view);
        fillNotificationCatalog(player, view);
        openGui(player, view.inventory);
    }

    private void fillNotificationCatalog(Player player, NotificationCatalogView view) {
        view.mode = NotificationCatalogView.Mode.ITEMS;
        decoratePurchase(view.inventory);
        view.slotToItemKey.clear();
        view.slotToCategory.clear();
        view.slotToEnchantment.clear();
        NotificationCategory selectedCategory = NotificationCategory.byId(view.category);
        List<NotificationEntry> entries = notificationItems.stream()
                .filter(entry -> selectedCategory.matches(entry.material()))
                .toList();
        Map<String, Double> lowestPrices = lowestPrices();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) AUCTION_SLOTS.length));
        view.page = Math.min(view.page, pages - 1);
        int start = view.page * AUCTION_SLOTS.length;
        for (int index = 0; index < AUCTION_SLOTS.length; index++) {
            int slot = AUCTION_SLOTS[index];
            int materialIndex = start + index;
            if (materialIndex >= entries.size()) {
                setSlot(view.inventory, slot, null);
                continue;
            }
            NotificationEntry entry = entries.get(materialIndex);
            List<FavoriteFilter> profiles = plugin.favorites().priceFilters(
                    player.getUniqueId(), donateAuction, entry.key());
            FavoriteFilter subscribed = profiles.stream().filter(filter -> !filter.hasEnchantment())
                    .findFirst().orElse(null);
            double lowest = lowestPrices.getOrDefault(entry.key(), 0D);
            Map<String, Object> replacements = Map.of(
                    "item", entry.name(),
                    "price", lowest > 0 ? formatPrice(lowest) : gui.text("favorites.catalog.no-price"),
                    "auto-buy", subscribed != null && subscribed.autoBuy()
                            ? gui.text("favorites.auto-buy-enabled") : gui.text("favorites.auto-buy-disabled"),
                    "profiles", profiles.size(),
                    "status", profiles.isEmpty()
                            ? gui.text("favorites.catalog.not-subscribed")
                            : gui.text("favorites.catalog.subscribed"));
            ItemStack icon = createIcon(entry.icon(),
                    gui.text("favorites.catalog.item.name", replacements),
                    gui.lore("favorites.catalog.item.lore", replacements));
            setSlot(view.inventory, slot, icon);
            view.slotToItemKey.put(slot, entry.key());
        }
        setSlot(view.inventory, 4, gui.item("favorites.catalog.category", Material.COMPASS,
                Map.of("category", gui.text("favorites.catalog.categories." + selectedCategory.id() + ".name"))));
        setSlot(view.inventory, 45, gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
        setSlot(view.inventory, 46, view.page > 0
                ? gui.item("pagination.previous", Material.PLAYER_HEAD, Map.of("page", view.page))
                : gui.item("pagination.previous-disabled", Material.PLAYER_HEAD, Map.of()));
        setSlot(view.inventory, 49, gui.item("favorites.catalog.subscriptions", Material.BOOK,
                Map.of("amount", plugin.favorites().list(player.getUniqueId(), donateAuction).size())));
        setSlot(view.inventory, 53, view.page + 1 < pages
                ? gui.item("pagination.next", Material.PLAYER_HEAD, Map.of("page", view.page + 2))
                : gui.item("pagination.next-disabled", Material.PLAYER_HEAD, Map.of()));
    }

    void handleNotificationCatalogClick(Player player, NotificationCatalogView view, int slot,
                                        boolean leftClick, boolean rightClick, boolean shiftClick) {
        if (view.mode == NotificationCatalogView.Mode.CATEGORIES) {
            if (slot == 45) {
                fillNotificationCatalog(player, view);
                animateRedraw(player, view.inventory);
                return;
            }
            String category = view.slotToCategory.get(slot);
            if (category != null) {
                view.category = category;
                view.page = 0;
                fillNotificationCatalog(player, view);
                animateRedraw(player, view.inventory);
                plugin.playSound(player, "gui.click-high");
            }
            return;
        }
        if (view.mode == NotificationCatalogView.Mode.ENCHANTMENTS) {
            if (slot == 45) {
                fillNotificationCatalog(player, view);
                animateRedraw(player, view.inventory);
                return;
            }
            Enchantment enchantment = view.slotToEnchantment.get(slot);
            if (enchantment != null) changeEnchantmentFilter(player, view, enchantment, leftClick, rightClick);
            if (slot == 4 && !view.draftEnchantments.isEmpty()) saveEnchantmentProfile(player, view);
            return;
        }
        if (slot == 45) {
            notificationCatalogViews.remove(player.getUniqueId());
            openAuction(player);
            return;
        }
        if (slot == 46 && view.page > 0) {
            view.page--;
            fillNotificationCatalog(player, view);
            animateRedraw(player, view.inventory);
            return;
        }
        if (slot == 4) {
            fillNotificationCategories(view);
            animateRedraw(player, view.inventory);
            return;
        }
        if (slot == 53) {
            view.page++;
            fillNotificationCatalog(player, view);
            animateRedraw(player, view.inventory);
            return;
        }
        if (slot == 49) {
            openFavorites(player);
            return;
        }
        String itemKey = view.slotToItemKey.get(slot);
        if (itemKey == null) return;
        Material material = plugin.itemLocalization().getKeyMaterial(itemKey);
        if (shiftClick && rightClick) {
            plugin.requestAutoBuyPrice(player, itemKey, donateAuction);
            return;
        }
        if (rightClick) {
            view.selectedMaterial = material;
            view.selectedItemKey = itemKey;
            view.draftEnchantments.clear();
            fillEnchantmentCatalog(player, view);
            animateRedraw(player, view.inventory);
            return;
        }
        FavoriteFilter existing = plugin.favorites().priceFilter(
                player.getUniqueId(), donateAuction, itemKey);
        if (shiftClick && leftClick) {
            if (existing == null) {
                double price = lowestPrice(itemKey);
                if (price <= 0) {
                    plugin.requestAutoBuyPrice(player, itemKey, donateAuction);
                    return;
                }
                plugin.favorites().addPrice(player.getUniqueId(), donateAuction, itemKey, price);
                existing = plugin.favorites().priceFilter(player.getUniqueId(), donateAuction, itemKey);
            }
            if (existing == null || !plugin.favorites().toggleAutoBuy(
                    player.getUniqueId(), donateAuction, existing.id())) {
                player.sendMessage(messages.message("notification.auto-buy-no-price"));
                plugin.playSound(player, "error.default");
            } else {
                FavoriteFilter changed = plugin.favorites().priceFilter(
                        player.getUniqueId(), donateAuction, itemKey);
                player.sendMessage(messages.message(changed != null && changed.autoBuy()
                        ? "notification.auto-buy-enabled" : "notification.auto-buy-disabled"));
                plugin.playSound(player, "gui.click-high");
            }
            fillNotificationCatalog(player, view);
            return;
        }
        if (existing != null) {
            plugin.favorites().remove(player.getUniqueId(), donateAuction, existing.id());
            player.sendMessage(messages.message("notification.favorite-removed"));
        } else {
            FavoriteService.AddResult result = plugin.favorites().addPrice(
                    player.getUniqueId(), donateAuction, itemKey, lowestPrice(itemKey));
            switch (result) {
                case ADDED, UPDATED -> player.sendMessage(messages.message("notification.favorite-added"));
                case INVALID -> player.sendMessage(messages.message("notification.favorite-invalid"));
                case DUPLICATE -> player.sendMessage(messages.message("notification.favorite-duplicate"));
            }
        }
        plugin.playSound(player, "gui.click-high");
        fillNotificationCatalog(player, view);
    }

    private void fillNotificationCategories(NotificationCatalogView view) {
        view.mode = NotificationCatalogView.Mode.CATEGORIES;
        view.inventory.clear();
        decoratePurchase(view.inventory);
        view.slotToItemKey.clear();
        view.slotToCategory.clear();
        view.slotToEnchantment.clear();
        int index = 0;
        for (NotificationCategory category : NotificationCategory.values()) {
            int slot = AUCTION_SLOTS[index++];
            boolean selected = category.id().equals(view.category);
            ItemStack icon = gui.item("favorites.catalog.categories." + category.id(), category.icon(),
                    Map.of("status", selected
                            ? gui.text("favorites.catalog.category-selected")
                            : gui.text("favorites.catalog.category-available")));
            setSlot(view.inventory, slot, icon);
            view.slotToCategory.put(slot, category.id());
        }
        setSlot(view.inventory, 45, gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
    }

    private void fillEnchantmentCatalog(Player player, NotificationCatalogView view) {
        view.mode = NotificationCatalogView.Mode.ENCHANTMENTS;
        view.inventory.clear();
        decoratePurchase(view.inventory);
        view.slotToItemKey.clear();
        view.slotToCategory.clear();
        view.slotToEnchantment.clear();
        Material material = view.selectedMaterial;
        List<Enchantment> enchantments = availableEnchantments(material);
        int index = 0;
        for (Enchantment enchantment : enchantments) {
            if (index >= AUCTION_SLOTS.length) break;
            int level = view.draftEnchantments.getOrDefault(enchantment.getKey().toString(), 0);
            boolean compatible = enchantmentsCompatible(enchantment, view.draftEnchantments);
            int slot = AUCTION_SLOTS[index++];
            Map<String, Object> replacements = Map.of(
                    "enchantment", plugin.itemLocalization().getEnchantmentName(enchantment),
                    "level", level <= 0 ? gui.text("favorites.catalog.enchantments.disabled") : level,
                    "maximum", enchantment.getMaxLevel(),
                    "compatibility", level > 0
                            ? gui.text("favorites.catalog.enchantments.selected-status")
                            : gui.text(compatible
                            ? "favorites.catalog.enchantments.compatible"
                            : "favorites.catalog.enchantments.conflicting"));
            ItemStack icon = createIcon(Material.ENCHANTED_BOOK,
                    gui.text("favorites.catalog.enchantments.item.name", replacements),
                    gui.lore("favorites.catalog.enchantments.item.lore", replacements));
            setSlot(view.inventory, slot, icon);
            view.slotToEnchantment.put(slot, enchantment);
        }
        List<String> selectedLore = new ArrayList<>(gui.lore("favorites.catalog.enchantments.selected.lore"));
        if (!view.draftEnchantments.isEmpty()) {
            selectedLore.add(gui.text("favorites.catalog.enchantments.selected.conditions-title"));
            view.draftEnchantments.forEach((key, level) -> {
                org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(key);
                Enchantment selected = namespacedKey == null ? null : Enchantment.getByKey(namespacedKey);
                selectedLore.add(gui.text("favorites.catalog.enchantments.selected.condition", Map.of(
                        "enchantment", plugin.itemLocalization().getEnchantmentName(selected), "level", level)));
            });
        } else {
            selectedLore.add(gui.text("favorites.catalog.enchantments.selected.no-conditions"));
        }
        setSlot(view.inventory, 4, createIcon(plugin.itemLocalization().createItem(view.selectedItemKey),
                gui.text("favorites.catalog.enchantments.selected.name",
                        Map.of("item", plugin.itemLocalization().getItemName(view.selectedItemKey))), selectedLore));
        setSlot(view.inventory, 45, gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
        if (enchantments.isEmpty()) {
            setSlot(view.inventory, 22, gui.item("favorites.catalog.enchantments.empty", Material.BARRIER, Map.of()));
        }
    }

    private void changeEnchantmentFilter(Player player, NotificationCatalogView view, Enchantment enchantment,
                                         boolean leftClick, boolean rightClick) {
        String key = enchantment.getKey().toString();
        int level = view.draftEnchantments.getOrDefault(key, 0);
        if (leftClick && level <= 0 && !enchantmentsCompatible(enchantment, view.draftEnchantments)) {
            player.sendMessage(messages.message("notification.enchantment-conflict", Map.of(
                    "enchantment", plugin.itemLocalization().getEnchantmentName(enchantment))));
            plugin.playSound(player, "error.default");
            return;
        }
        if (leftClick) level++;
        if (rightClick) level--;
        level = Math.min(level, enchantment.getMaxLevel());
        if (level <= 0) view.draftEnchantments.remove(key);
        else view.draftEnchantments.put(key, level);
        plugin.playSound(player, level <= 0 ? "action.favorite-removed" : "action.favorite-added");
        fillEnchantmentCatalog(player, view);
    }

    private void saveEnchantmentProfile(Player player, NotificationCatalogView view) {
        FavoriteService.AddResult result = plugin.favorites().addEnchantmentProfile(player.getUniqueId(),
                donateAuction, view.selectedMaterial, view.draftEnchantments, lowestPrice(view.selectedMaterial));
        player.sendMessage(messages.message(result == FavoriteService.AddResult.ADDED
                ? "notification.favorite-added" : result == FavoriteService.AddResult.DUPLICATE
                ? "notification.favorite-duplicate" : "notification.favorite-invalid"));
        plugin.playSound(player, result == FavoriteService.AddResult.ADDED
                ? "action.favorite-added" : "error.default");
        if (result == FavoriteService.AddResult.ADDED) {
            view.draftEnchantments.clear();
            // The player can immediately assemble another independent profile for this material.
            fillEnchantmentCatalog(player, view);
            animateRedraw(player, view.inventory);
        }
    }

    private List<Enchantment> availableEnchantments(Material material) {
        if (material == null || material.isAir()) return List.of();
        ItemStack item = new ItemStack(material);
        return Arrays.stream(Enchantment.values())
                .filter(enchantment -> material == Material.ENCHANTED_BOOK || enchantment.canEnchantItem(item))
                .sorted(Comparator.comparing(plugin.itemLocalization()::getEnchantmentName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean enchantmentsCompatible(Enchantment candidate, Map<String, Integer> enchantments) {
        if (candidate == null || enchantments == null || enchantments.isEmpty()) return true;
        for (String keyValue : enchantments.keySet()) {
            if (candidate.getKey().toString().equalsIgnoreCase(keyValue)) continue;
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(keyValue);
            Enchantment selected = key == null ? null : Enchantment.getByKey(key);
            if (selected != null && (candidate.conflictsWith(selected) || selected.conflictsWith(candidate))) {
                return false;
            }
        }
        return true;
    }

    private List<NotificationEntry> loadNotificationItems() {
        List<NotificationEntry> entries = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isAir() && material.isItem() && !material.name().startsWith("LEGACY_")
                    && !isPotionMaterial(material)) {
                ItemStack item = new ItemStack(material);
                entries.add(new NotificationEntry(plugin.itemLocalization().getItemKey(item), item,
                        plugin.itemLocalization().getPlainName(item)));
            }
        }
        for (PotionType type : PotionType.values()) {
            addPotionEntries(entries, type, false, false);
            if (type.isExtendable()) addPotionEntries(entries, type, true, false);
            if (type.isUpgradeable()) addPotionEntries(entries, type, false, true);
        }
        entries.sort(Comparator.comparing(NotificationEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private void addPotionEntries(List<NotificationEntry> entries, PotionType type,
                                  boolean extended, boolean upgraded) {
        for (String name : List.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW")) {
            Material material = Material.matchMaterial(name);
            if (material == null) continue;
            try {
                ItemStack item = new ItemStack(material);
                if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta)) continue;
                meta.setBasePotionData(new PotionData(type, extended, upgraded));
                item.setItemMeta(meta);
                entries.add(new NotificationEntry(plugin.itemLocalization().getItemKey(item), item,
                        plugin.itemLocalization().getPlainName(item)));
            } catch (IllegalArgumentException ignored) {
                // Unsupported combinations are intentionally absent from the catalog.
            }
        }
    }

    private boolean isPotionMaterial(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW;
    }

    private Map<String, Double> lowestPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (MarketListing listing : activeListings()) {
            double price = listing.pricePerUnit();
            if (price > 0) prices.merge(plugin.itemLocalization().getItemKey(listing.item()), price, Math::min);
        }
        return prices;
    }

    private double lowestPrice(Material material) {
        return lowestPrice(material.name());
    }

    private double lowestPrice(String itemKey) {
        return lowestPrices().getOrDefault(itemKey, 0D);
    }

    private void fillFavorites(Player player, FavoritesView view) {
        decoratePurchase(view.inventory);
        view.slotToFavoriteId.clear();
        List<FavoriteFilter> filters = plugin.favorites().list(player.getUniqueId(), donateAuction);
        int pages = Math.max(1, (int) Math.ceil(filters.size() / (double) AUCTION_SLOTS.length));
        view.page = Math.min(view.page, pages - 1);
        int index = view.page * AUCTION_SLOTS.length;
        for (int slot : AUCTION_SLOTS) {
            if (index >= filters.size()) {
                setSlot(view.inventory, slot, null);
                continue;
            }
            FavoriteFilter filter = filters.get(index++);
            ItemStack favoriteIcon = filter.type() == FavoriteFilter.Type.NAME
                    ? new ItemStack(Material.NAME_TAG) : plugin.itemLocalization().createItem(filter.value());
            String value = plugin.favorites().displayValue(filter);
            String key = filter.type().name().toLowerCase(Locale.ROOT);
            String type = gui.text("favorites.type." + key);
            String entry = filter.hasEnchantment() ? "favorites.enchantment" : "favorites." + key;
            Map<String, Object> placeholders = Map.of(
                    "value", value,
                    "type", type,
                    "enchantment", plugin.favorites().enchantmentSummary(filter),
                    "level", filter.enchantmentLevel(),
                    "price", filter.maximumPrice() > 0
                            ? formatPrice(filter.maximumPrice()) : gui.text("favorites.catalog.no-price"),
                    "auto-buy", filter.autoBuy()
                            ? gui.text("favorites.auto-buy-enabled") : gui.text("favorites.auto-buy-disabled"));
            List<String> favoriteLore;
            if (filter.hasEnchantment()) {
                favoriteLore = new ArrayList<>(gui.lore("favorites.enchantment.lore-top", placeholders));
                filter.enchantments().forEach((enchantmentKey, level) -> {
                    org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(enchantmentKey);
                    Enchantment enchantment = namespacedKey == null ? null : Enchantment.getByKey(namespacedKey);
                    favoriteLore.add(gui.text("favorites.enchantment.condition", Map.of(
                            "enchantment", plugin.itemLocalization().getEnchantmentName(enchantment), "level", level)));
                });
                favoriteLore.addAll(gui.lore("favorites.enchantment.lore-bottom", placeholders));
            } else {
                favoriteLore = gui.lore(entry + ".lore", placeholders);
            }
            ItemStack icon = createIcon(favoriteIcon,
                    gui.text(entry + ".name", placeholders), favoriteLore);
            setSlot(view.inventory, slot, icon);
            view.slotToFavoriteId.put(slot, filter.id());
        }
        setSlot(view.inventory, 45, gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
        setSlot(view.inventory, 46, view.page > 0
                ? gui.item("pagination.previous", Material.PLAYER_HEAD, Map.of("page", view.page))
                : gui.item("pagination.previous-disabled", Material.PLAYER_HEAD, Map.of()));
        setSlot(view.inventory, 49, gui.item("favorites.catalog.open", Material.COMPASS,
                Map.of("amount", filters.size())));
        setSlot(view.inventory, 53, view.page + 1 < pages
                ? gui.item("pagination.next", Material.PLAYER_HEAD, Map.of("page", view.page + 2))
                : gui.item("pagination.next-disabled", Material.PLAYER_HEAD, Map.of()));
    }

    void handleFavoritesClick(Player player, FavoritesView view, int slot, boolean shiftClick) {
        if (slot == 45) {
            favoritesViews.remove(player.getUniqueId());
            openNotificationCatalog(player, 0);
            return;
        }
        if (slot == 46 && view.page > 0) {
            view.page--;
            fillFavorites(player, view);
            return;
        }
        if (slot == 53) {
            view.page++;
            fillFavorites(player, view);
            return;
        }
        if (slot == 49) {
            openNotificationCatalog(player, 0);
            return;
        }
        String id = view.slotToFavoriteId.get(slot);
        if (id == null) return;
        if (shiftClick) {
            FavoriteFilter current = plugin.favorites().list(player.getUniqueId(), donateAuction).stream()
                    .filter(filter -> filter.id().equals(id)).findFirst().orElse(null);
            if (current != null && current.type() == FavoriteFilter.Type.PRICE && current.maximumPrice() <= 0) {
                plugin.requestAutoBuyPrice(player, current, donateAuction);
                return;
            }
            if (plugin.favorites().toggleAutoBuy(player.getUniqueId(), donateAuction, id)) {
                FavoriteFilter changed = plugin.favorites().list(player.getUniqueId(), donateAuction).stream()
                        .filter(filter -> filter.id().equals(id)).findFirst().orElse(null);
                player.sendMessage(messages.message(changed != null && changed.autoBuy()
                        ? "notification.auto-buy-enabled" : "notification.auto-buy-disabled"));
                plugin.playSound(player, "gui.click-high");
                fillFavorites(player, view);
            } else {
                player.sendMessage(messages.message("notification.auto-buy-no-price"));
                plugin.playSound(player, "error.default");
            }
            return;
        }
        if (plugin.favorites().remove(player.getUniqueId(), donateAuction, id)) {
            plugin.playSound(player, "action.favorite-deleted");
            fillFavorites(player, view);
        }
    }

    public void openBundleCreatePreview(Player player, double totalPrice, String name,
                                        Map<Integer, ItemStack> sourceSlots, int serializedSize) {
        BundleCreateView view = new BundleCreateView();
        view.controller = this;
        view.viewer = player.getUniqueId();
        view.name = name;
        view.totalPrice = totalPrice;
        view.serializedSize = serializedSize;
        view.sourceSlots = new LinkedHashMap<>();
        sourceSlots.forEach((slot, item) -> view.sourceSlots.put(slot, item.clone()));
        view.inventory = Bukkit.createInventory(view, 54,
                ColorUtil.colorize("&8Выставление • " + name));
        bundleCreateViews.put(player.getUniqueId(), view);

        ItemStack black = createIcon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 36; slot < 54; slot++) setSlot(view.inventory, slot, black);
        applyConfiguredDecorations(view.inventory);
        int index = 0;
        for (ItemStack source : view.sourceSlots.values()) {
            if (index >= BUNDLE_CREATE_CONTENT_SLOTS.length) break;
            ItemStack display = source.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                applyListingDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(view.inventory, BUNDLE_CREATE_CONTENT_SLOTS[index++], display);
        }
        List<ItemStack> contents = view.sourceSlots.values().stream().map(ItemStack::clone).toList();
        setSlot(view.inventory, SLOT_BUNDLE_CREATE_CONFIRM, createIcon(
                Material.LIME_WOOL,
                "§x§7§C§F§F§8§0 Подтвердить выставление",
                "",
                " §7- §fНазвание: §e" + name,
                " §7- §fПредметов: §e" + contents.size(),
                " §7- §fРедкость: " + MarketBundle.rarity(contents).displayName(),
                " §7- §fСтоимость: " + formatPrice(totalPrice),
                "",
                "§x§7§C§F§F§8§0➥ §fНажмите, чтобы выставить"
        ));
        setSlot(view.inventory, SLOT_BUNDLE_CREATE_CANCEL,
                texturedHead(BUNDLE_PREVIOUS_TEXTURE, "§x§F§F§5§5§5§5 Отмена",
                        "", "§7Предметы останутся в инвентаре."));
        setSlot(view.inventory, SLOT_BUNDLE_CREATE_INFO, createIcon(
                Material.PAPER,
                "§x§D§5§B§F§F§F Проверка набора",
                "",
                " §7- §fРазмер данных: §e" + serializedSize + " байт",
                " §7- §fСлотов: §e" + contents.size(),
                "",
                "§7До подтверждения предметы не изымаются."
        ));
        openGui(player, view.inventory);
    }

    void handleBundleCreateClick(Player player, BundleCreateView view, int slot) {
        if (!player.getUniqueId().equals(view.viewer) || view.processing) return;
        if (slot == SLOT_BUNDLE_CREATE_CANCEL) {
            bundleCreateViews.remove(player.getUniqueId());
            openAuction(player);
            return;
        }
        if (slot != SLOT_BUNDLE_CREATE_CONFIRM) return;
        view.processing = true;
        setSlot(view.inventory, SLOT_BUNDLE_CREATE_CONFIRM,
                createIcon(Material.YELLOW_WOOL, "§eСохраняем набор...", "", "§7Пожалуйста, подождите."));
        if (!plugin.confirmKitListing(player, donateAuction, view.name, view.totalPrice, view.sourceSlots)) {
            view.processing = false;
            setSlot(view.inventory, SLOT_BUNDLE_CREATE_CONFIRM,
                    createIcon(Material.RED_WOOL, "§cНе удалось выставить", "",
                            "§7Проверьте инвентарь и повторите команду."));
        }
    }

    private void openMyItems(Player player) {
        UUID viewerId = player.getUniqueId();
        MyItemsView view = new MyItemsView();
        view.controller = this;
        view.inventory = Bukkit.createInventory(view, 54,
                ColorUtil.colorize("&8" + (donateAuction ? gui.text("titles.donate-my-items") : gui.text("titles.my-items"))));
        view.slotToListingId = new HashMap<>();
        myItemsViews.put(viewerId, view);
        fillMyItemsInventory(viewerId, view);
        openGui(player, view.inventory);
    }

    void fillMyItemsInventory(UUID viewerId, MyItemsView view) {
        view.slotToListingId = new HashMap<>();
        decoratePurchase(view.inventory);
        Iterator<MarketListing> listings = sync().bySeller(viewerId).stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "EXPIRED".equalsIgnoreCase(listing.status())
                        || "RETURNED".equalsIgnoreCase(listing.status()))
                .iterator();
        for (int slot : SELLER_SLOTS) {
            if (!listings.hasNext()) {
                setSlot(view.inventory, slot, null);
                continue;
            }
            MarketListing listing = listings.next();
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) setDisplayName(meta, bundleDisplayName(listing));
                List<String> lore = new ArrayList<>(buildListingLore(listing, listing.amount()));
                String status = "EXPIRED".equalsIgnoreCase(listing.status())
                        ? gui.text("listing.status.expired")
                        : gui.text("listing.status.returned");
                lore.add(gui.text("listing.status.name"));
                lore.addAll(gui.lore("listing.status.lore", Map.of(
                        "amount", listing.amount(), "status", status)));
                lore.add("");
                lore.add(gui.text("hints.collect"));
                if ("EXPIRED".equalsIgnoreCase(listing.status())) lore.add(gui.text("hints.relist"));
                meta.setLore(lore);
                applyListingDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(view.inventory, slot, display);
            view.slotToListingId.put(slot, listing.id());
        }
        setSlot(view.inventory, SLOT_BACK_BOTTOM,
                gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
    }

    void handleMyItemsClick(Player player, MyItemsView view, int slot, boolean rightClick) {
        if (slot == SLOT_BACK_BOTTOM) {
            myItemsViews.remove(player.getUniqueId());
            openAuction(player);
            plugin.playSound(player, "gui.open");
            return;
        }
        String id = view.slotToListingId.get(slot);
        if (id == null) return;
        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            setSlot(view.inventory, slot, null);
            view.slotToListingId.remove(slot);
            plugin.playSound(player, "error.default");
            return;
        }
        if (rightClick && "EXPIRED".equalsIgnoreCase(listing.status())) {
            plugin.relist(player, listing, donateAuction);
            return;
        }
        String actionKey = "collect:" + listing.id();
        if (!pendingListingActions.add(actionKey)) return;
        storageAsync(() -> {
            repository.delete(listing.id());
            return Boolean.TRUE;
        }, ignored -> {
            pendingListingActions.remove(actionKey);
            giveItemsOrDrop(player, deliveryItems(listing, listing.amount()));
            sync().listingRemoved(listing.id());
            player.sendMessage(messages.message("notification.collected", Map.of(
                    "item", plugin.itemLocalization().getPlainName(listing.item()))));
            plugin.playSound(player, "action.item-collected");
            if (player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                setSlot(view.inventory, slot, null);
                view.slotToListingId.remove(slot);
            }
        }, exception -> {
            pendingListingActions.remove(actionKey);
            plugin.getLogger().warning("Не удалось забрать лот " + listing.id() + ": " + exception.getMessage());
            player.sendMessage(messages.message("error.purchase-failed"));
        });
    }

    private void openBundlePreview(Player player, MarketListing listing) {
        List<ItemStack> contents = bundleItems(listing);
        if (contents.isEmpty()) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            plugin.playSound(player, "error.default");
            return;
        }

        BundlePreviewView view = new BundlePreviewView();
        view.controller = this;
        view.listing = listing;
        view.inventory = Bukkit.createInventory(view, 54,
                bundleDisplayName(listing));
        fillBundlePreview(player, view);
        openGui(player, view.inventory);
        plugin.playSound(player, "gui.open");
    }

    private void fillBundlePreview(Player player, BundlePreviewView view) {
        List<ItemStack> contents = bundleItems(view.listing);
        decorateBundlePreview(view.inventory);
        for (int index = 0; index < AUCTION_SLOTS.length; index++) {
            if (index >= contents.size()) {
                setSlot(view.inventory, AUCTION_SLOTS[index], null);
                continue;
            }
            ItemStack display = contents.get(index).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (!meta.hasDisplayName()) {
                    setDisplayName(meta, plugin.itemLocalization().getPlainName(display));
                }
                applyListingDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(view.inventory, AUCTION_SLOTS[index], display);
        }

        setSlot(view.inventory, SLOT_BUNDLE_BACK,
                gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
    }

    private void decorateBundlePreview(Inventory inventory) {
        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = black.getItemMeta();
        if (blackMeta != null) {
            blackMeta.setDisplayName(" ");
            black.setItemMeta(blackMeta);
        }
        hideAttributes(black);

        ItemStack orange = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta orangeMeta = orange.getItemMeta();
        if (orangeMeta != null) {
            orangeMeta.setDisplayName(" ");
            orange.setItemMeta(orangeMeta);
        }
        hideAttributes(orange);

        for (int slot : AUCTION_BLACK_SLOTS) setSlot(inventory, slot, black);
        for (int slot : AUCTION_ORANGE_SLOTS) setSlot(inventory, slot, orange);
        applyConfiguredDecorations(inventory);
    }

    void handleBundlePreviewClick(Player player, BundlePreviewView view, int slot) {
        if (slot != SLOT_BUNDLE_BACK) return;
        openAuction(player);
        plugin.playSound(player, "gui.open-low");
    }

    public void openListing(Player player, String listingId) {
        MarketListing listing = loadListingById(listingId);
        if (listing == null) {
            storageAsync(() -> repository.findById(listingId).orElse(null), fresh -> {
                if (isPurchasableBy(player, fresh)) {
                    sync().listingUpdated(fresh);
                    openPurchaseGui(player, fresh);
                } else {
                    player.sendMessage(messages.message("error.listing-unavailable"));
                    plugin.playSound(player, "error.default");
                    refreshAllViews();
                }
            }, exception -> {
                player.sendMessage(messages.message("error.listing-unavailable"));
                plugin.playSound(player, "error.default");
            });
            return;
        }
        if (!isPurchasableBy(player, listing)) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            plugin.playSound(player, "error.default");
            refreshAllViews();
            return;
        }
        openPurchaseGui(player, listing);
    }

    public void autoPurchase(UUID buyerId, MarketListing listing) {
        String purchaseKey = buyerId + ":" + (listing == null ? "" : listing.id());
        if (buyerId == null || listing == null || listing.amount() <= 0
                || !"ACTIVE".equalsIgnoreCase(listing.status())
                || listing.expiresAt() <= System.currentTimeMillis()
                || listing.sellerId().equals(buyerId) || !pendingAutomaticPurchases.add(purchaseKey)) return;
        sync().listingUpdated(listing);
        Deque<AutoPurchaseRequest> queue = automaticQueues.computeIfAbsent(listing.id(), ignored -> new ArrayDeque<>());
        queue.addLast(new AutoPurchaseRequest(buyerId, purchaseKey, listing));
        if (queue.size() == 1) startNextAutomaticPurchase(listing.id());
    }

    private void startNextAutomaticPurchase(String listingId) {
        Deque<AutoPurchaseRequest> queue = automaticQueues.get(listingId);
        AutoPurchaseRequest request = queue == null ? null : queue.peekFirst();
        if (request == null) return;
        try {
            autoPurchaseQueue.execute(() -> {
                PurchaseReservation reservation;
                try {
                    reservation = repository.reserve(listingId, 1).orElse(null);
                } catch (RuntimeException exception) {
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                        finishAllAutomatic(listingId);
                        plugin.getLogger().warning("Не удалось зарезервировать автопокупку: "
                                + exception.getMessage());
                    });
                    return;
                }
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (reservation == null) finishAllAutomatic(listingId);
                    else completeAutomaticPurchase(request.buyerId(), request.purchaseKey(), reservation);
                });
            });
        } catch (RejectedExecutionException ignored) {
            finishAllAutomatic(listingId);
        }
    }

    private void finishAutomatic(String purchaseKey, String listingId) {
        pendingAutomaticPurchases.remove(purchaseKey);
        Deque<AutoPurchaseRequest> queue = automaticQueues.get(listingId);
        if (queue == null) return;
        queue.pollFirst();
        if (queue.isEmpty()) automaticQueues.remove(listingId);
        else startNextAutomaticPurchase(listingId);
    }

    private void finishAllAutomatic(String listingId) {
        Deque<AutoPurchaseRequest> queue = automaticQueues.remove(listingId);
        if (queue != null) queue.forEach(request -> pendingAutomaticPurchases.remove(request.purchaseKey()));
    }

    private boolean isPurchasableBy(Player player, MarketListing listing) {
        return player != null && player.isOnline() && listing != null && listing.amount() > 0
                && "ACTIVE".equalsIgnoreCase(listing.status())
                && listing.expiresAt() > System.currentTimeMillis()
                && !listing.sellerId().equals(player.getUniqueId());
    }

    private void openPurchaseGui(Player player, MarketListing listing) {
        PurchaseView view = new PurchaseView();
        view.controller = this;
        view.listing = listing;
        view.maxAmount = listing.amount();
        view.quantity = 1;
        view.sellerId = listing.sellerId();
        view.inventory = Bukkit.createInventory(view, 54,
                ColorUtil.colorize("&8" + (donateAuction ? gui.text("titles.donate-purchase") : gui.text("titles.purchase"))));
        purchaseViews.put(player.getUniqueId(), view);
        decoratePurchase(view.inventory);
        setSlot(view.inventory, SLOT_BACK_TOP,
                gui.item("actions.back", Material.PLAYER_HEAD, Map.of()));
        setSlot(view.inventory, SLOT_MINUS_1, createIcon(Material.RED_WOOL, "§c-1"));
        setSlot(view.inventory, SLOT_MINUS_10, createIcon(Material.RED_WOOL, "§c-10"));
        setSlot(view.inventory, SLOT_PLUS_1, createIcon(Material.GREEN_WOOL, "§a+1"));
        setSlot(view.inventory, SLOT_PLUS_10, createIcon(Material.GREEN_WOOL, "§a+10"));
        Inventory inv = view.inventory;
        PurchaseView pv = view;

        ItemStack buy = gui.item("actions.buy", ICON_BUY, Map.of());
        setSlot(inv, SLOT_BUY, buy);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
            sm.setOwningPlayer(seller);
            String sellerName = seller.getName() != null ? seller.getName() : listing.sellerId().toString();
            setDisplayName(sm, gui.text("seller.name", Map.of("seller", sellerName)));
            List<String> hl = new ArrayList<>(gui.lore("seller.lore", Map.of("seller", sellerName)));
            sm.setLore(hl);
            applyDisplayFlags(sm);
            head.setItemMeta(sm);
        }
        hideAttributes(head);
        setSlot(inv, SLOT_SELLER_HEAD, head);

        updateQuantityItems(pv);
        openGui(player, inv);
        plugin.playSound(player, "gui.open");
    }

    private void decoratePurchase(Inventory inv) {
        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = black.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            black.setItemMeta(bm);
        }
        hideAttributes(black);
        ItemStack orange = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta om = orange.getItemMeta();
        if (om != null) {
            om.setDisplayName(" ");
            orange.setItemMeta(om);
        }
        hideAttributes(orange);
        for (int slot : PURCHASE_BLACK_SLOTS) {
            setSlot(inv, slot, black);
        }
        for (int slot : PURCHASE_ORANGE_SLOTS) {
            setSlot(inv, slot, orange);
        }
        applyConfiguredDecorations(inv);
    }

    private void updateQuantityItems(PurchaseView pv) {
        Inventory inv = pv.inventory;
        MarketListing listing = pv.listing;
        ItemStack preview = listing.item().clone();
        int displayAmount = Math.min(pv.quantity, preview.getMaxStackSize());
        if (displayAmount <= 0) displayAmount = 1;
        preview.setAmount(displayAmount);
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            if (isBundle(listing)) setDisplayName(pm, bundleDisplayName(listing));
            List<String> lore = new ArrayList<>();
            lore.addAll(buildListingLore(listing, pv.quantity));
            pm.setLore(lore);
            applyListingDisplayFlags(pm);
            preview.setItemMeta(pm);
        }

        setSlot(inv, SLOT_PREVIEW, preview);
        ItemStack buy = inv.getItem(SLOT_BUY);
    }

    void handlePurchaseClick(Player player, PurchaseView pv, int slot) {
        if (slot == SLOT_BACK_TOP) {
            purchaseViews.remove(player.getUniqueId());
            openAuction(player);
            plugin.playSound(player, "gui.open");
            return;
        }
        if (slot == SLOT_SELLER_HEAD) {
            purchaseViews.remove(player.getUniqueId());
            openSellerGui(player, pv.sellerId);
            plugin.playSound(player, "gui.open");
            return;
        }
        if (slot == SLOT_MINUS_1) {
            pv.quantity -= 1;
            if (pv.quantity < 1) pv.quantity = 1;
            updateQuantityItems(pv);
            plugin.playSound(player, "gui.quantity-minus-one");
            return;
        }
        if (slot == SLOT_MINUS_10) {
            pv.quantity -= 10;
            if (pv.quantity < 1) pv.quantity = 1;
            updateQuantityItems(pv);
            plugin.playSound(player, "gui.quantity-minus-ten");
            return;
        }
        if (slot == SLOT_PLUS_1) {
            pv.quantity += 1;
            if (pv.quantity > pv.maxAmount) pv.quantity = pv.maxAmount;
            updateQuantityItems(pv);
            plugin.playSound(player, "gui.quantity-plus-one");
            return;
        }
        if (slot == SLOT_PLUS_10) {
            pv.quantity += 10;
            if (pv.quantity > pv.maxAmount) pv.quantity = pv.maxAmount;
            updateQuantityItems(pv);
            plugin.playSound(player, "gui.quantity-plus-ten");
            return;
        }
        if (slot == SLOT_BUY) {
            performPurchase(player, pv);
        }
    }

    private void performPurchase(Player player, PurchaseView pv) {
        MarketListing fresh = loadListingById(pv.listing.id());
        if (fresh == null || fresh.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(fresh.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }
        if (fresh.sellerId().equals(player.getUniqueId())) {
            player.sendMessage(messages.message("error.own-listing"));
            plugin.playSound(player, "error.default");
            return;
        }
        int requestedAmount = Math.min(pv.quantity, fresh.amount());
        if (requestedAmount <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }
        double totalPrice = fresh.pricePerUnit() * requestedAmount;
        if (!payment.has(player, totalPrice)) {
            player.sendMessage(messages.message("error.insufficient-funds"));
            plugin.playSound(player, "error.default");
            return;
        }
        List<ItemStack> delivery = deliveryItems(fresh, requestedAmount);
        if (delivery.isEmpty()) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            plugin.playSound(player, "error.default");
            return;
        }
        if (!canFitAll(player, delivery)) {
            player.sendMessage("§cНедостаточно места в инвентаре для покупки этого набора.");
            plugin.playSound(player, "error.default");
            return;
        }
        UUID buyerId = player.getUniqueId();
        if (!pendingPurchases.add(buyerId)) {
            player.sendMessage("§eПокупка уже обрабатывается.");
            return;
        }
        setSlot(pv.inventory, SLOT_BUY,
                createIcon(Material.YELLOW_WOOL, "§eОбработка покупки...", "", "§7Пожалуйста, подождите."));
        String listingId = fresh.id();
        storageAsync(() -> repository.reserve(listingId, requestedAmount).orElse(null), reservation -> {
            if (reservation == null) {
                pendingPurchases.remove(buyerId);
                player.sendMessage(messages.message("error.listing-unavailable"));
                refreshAllViews();
                return;
            }
            completeReservedPurchase(player, pv, reservation);
        }, exception -> {
            pendingPurchases.remove(buyerId);
            plugin.getLogger().warning("Не удалось зарезервировать покупку: " + exception.getMessage());
            player.sendMessage(messages.message("error.purchase-failed"));
            refreshAllViews();
        });
    }

    private void completeReservedPurchase(Player player, PurchaseView pv, PurchaseReservation reservation) {
        UUID buyerId = player.getUniqueId();
        MarketListing fresh = reservation.listing();
        int toBuy = reservation.quantity();
        double totalPrice = fresh.pricePerUnit() * toBuy;
        List<ItemStack> delivery = deliveryItems(fresh, toBuy);
        if (!player.isOnline() || delivery.isEmpty() || !canFitAll(player, delivery)) {
            rollbackReservationAsync(reservation);
            pendingPurchases.remove(buyerId);
            if (player.isOnline()) {
                player.sendMessage("§cНедостаточно места в инвентаре для покупки.");
                refreshAllViews();
            }
            return;
        }
        if (!payment.withdraw(player, totalPrice)) {
            rollbackReservationAsync(reservation);
            pendingPurchases.remove(buyerId);
            player.sendMessage(messages.message("error.insufficient-funds"));
            refreshAllViews();
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(fresh.sellerId());
        double saleCommission = plugin.commissions().sale(seller, payment, totalPrice);
        double sellerIncome = Math.max(0, totalPrice - saleCommission);
        if (sellerIncome > 0 && !payment.deposit(seller, sellerIncome)) {
            payment.deposit(player, totalPrice);
            rollbackReservationAsync(reservation);
            pendingPurchases.remove(buyerId);
            player.sendMessage(messages.message("error.purchase-failed"));
            refreshAllViews();
            return;
        }

        storageAsync(() -> {
            repository.finalizeReservation(reservation);
            return Boolean.TRUE;
        }, ignored -> {
            pendingPurchases.remove(buyerId);
            if (reservation.remainingAmount() == 0) {
                sync().listingRemoved(fresh.id());
            } else {
                sync().listingUpdated(fresh.withAmount(reservation.remainingAmount()));
            }
            plugin.notifySellerSale(player, fresh, totalPrice, donateAuction);
            if (seller.isOnline() && seller.getPlayer() != null) {
                plugin.playSound(seller.getPlayer(), "action.seller-sale");
            }
            giveItemsOrDrop(player, delivery);
            player.sendMessage(messages.message("notification.purchased", Map.of(
                    "item", plugin.itemLocalization().getPlainName(fresh.item()),
                    "price", formatPrice(totalPrice))));
            plugin.playSound(player, "action.purchase");
            if (purchaseViews.get(buyerId) == pv) {
                purchaseViews.remove(buyerId);
                player.closeInventory();
            }
        }, exception -> {
            pendingPurchases.remove(buyerId);
            boolean sellerRolledBack = sellerIncome <= 0 || payment.withdraw(seller, sellerIncome);
            boolean buyerRefunded = payment.deposit(player, totalPrice);
            rollbackReservationAsync(reservation);
            plugin.getLogger().severe("Не удалось завершить покупку " + fresh.id()
                    + "; rollback seller=" + sellerRolledBack + ", buyer=" + buyerRefunded
                    + ": " + exception.getMessage());
            player.sendMessage(messages.message("error.purchase-failed"));
            refreshAllViews();
        });
    }

    private void completeAutomaticPurchase(UUID buyerId, String purchaseKey, PurchaseReservation reservation) {
        MarketListing fresh = reservation.listing();
        int toBuy = reservation.quantity();
        double totalPrice = fresh.pricePerUnit() * toBuy;
        List<ItemStack> delivery = deliveryItems(fresh, toBuy);
        OfflinePlayer buyer = Bukkit.getOfflinePlayer(buyerId);
        if (delivery.isEmpty() || !payment.withdraw(buyer, totalPrice)) {
            plugin.queueNotification(buyerId, messages.message("notification.auto-buy-failed"));
            rollbackAutomatic(reservation, purchaseKey);
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(fresh.sellerId());
        double saleCommission = plugin.commissions().sale(seller, payment, totalPrice);
        double sellerIncome = Math.max(0, totalPrice - saleCommission);
        if (sellerIncome > 0 && !payment.deposit(seller, sellerIncome)) {
            payment.deposit(buyer, totalPrice);
            plugin.queueNotification(buyerId, messages.message("notification.auto-buy-failed"));
            rollbackAutomatic(reservation, purchaseKey);
            return;
        }

        storageAsync(() -> {
            List<String> deliveryIds = plugin.deliveries().store(buyerId, delivery);
            try {
                repository.finalizeReservation(reservation);
                return deliveryIds;
            } catch (RuntimeException exception) {
                plugin.deliveries().delete(buyerId, deliveryIds);
                throw exception;
            }
        }, deliveryIds -> {
            finishAutomatic(purchaseKey, fresh.id());
            if (reservation.remainingAmount() == 0) sync().listingRemoved(fresh.id());
            else sync().listingUpdated(fresh.withAmount(reservation.remainingAmount()));
            String buyerName = buyer.getName() == null ? buyerId.toString() : buyer.getName();
            plugin.notifySellerSale(buyerName, fresh, totalPrice, donateAuction);
            if (seller.isOnline() && seller.getPlayer() != null) {
                plugin.playSound(seller.getPlayer(), "action.seller-sale");
            }
            plugin.queueNotification(buyerId, messages.message("notification.auto-buy-purchased", Map.of(
                    "item", plugin.itemLocalization().getPlainName(fresh.item()),
                    "price", formatPrice(totalPrice))));
            Player online = Bukkit.getPlayer(buyerId);
            if (online != null && online.isOnline()) {
                plugin.deliveries().deliverFitting(online);
                plugin.playSound(online, "action.purchase");
            }
        }, exception -> {
            boolean sellerRolledBack = sellerIncome <= 0 || payment.withdraw(seller, sellerIncome);
            boolean buyerRefunded = payment.deposit(buyer, totalPrice);
            plugin.getLogger().severe("Не удалось завершить автопокупку " + fresh.id()
                    + "; rollback seller=" + sellerRolledBack + ", buyer=" + buyerRefunded
                    + ": " + exception.getMessage());
            plugin.queueNotification(buyerId, messages.message("notification.auto-buy-failed"));
            rollbackAutomatic(reservation, purchaseKey);
        });
    }

    private void rollbackAutomatic(PurchaseReservation reservation, String purchaseKey) {
        storageAsync(() -> {
            repository.rollbackReservation(reservation.listing().id(), reservation.quantity());
            return Boolean.TRUE;
        }, ignored -> finishAutomatic(purchaseKey, reservation.listing().id()), exception -> {
            plugin.getLogger().severe("Не удалось откатить резерв автопокупки "
                    + reservation.listing().id() + ": " + exception.getMessage());
            finishAllAutomatic(reservation.listing().id());
        });
    }

    private record AutoPurchaseRequest(UUID buyerId, String purchaseKey, MarketListing listing) {
    }

    private void rollbackReservationAsync(PurchaseReservation reservation) {
        storageAsync(() -> {
            repository.rollbackReservation(reservation.listing().id(), reservation.quantity());
            return Boolean.TRUE;
        }, ignored -> refreshAllViews(), exception ->
                plugin.getLogger().severe("Не удалось откатить резерв лота "
                        + reservation.listing().id() + ": " + exception.getMessage()));
    }

    public void openSellerGui(Player viewer, UUID sellerId) {
        if (!hasActiveListings(sellerId)) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
            String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
            viewer.sendMessage(messages.message("error.seller-empty", Map.of("seller", sellerName)));
            plugin.playSound(viewer, "error.default");
            return;
        }
        SellerView sv = new SellerView();
        sv.controller = this;
        sv.sellerId = sellerId;
        sv.slotToListingId = new HashMap<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
        String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
        Inventory inv = Bukkit.createInventory(sv, 54, ColorUtil.colorize("&8"
                + (donateAuction ? "Донат-товары " + sellerName
                : gui.text("titles.seller", Map.of("seller", sellerName)))));
        sv.inventory = inv;
        sellerViews.put(viewer.getUniqueId(), sv);
        decoratePurchase(inv);
        int activeCount = sync().activeCount(sellerId);
        ItemStack info = new ItemStack(ICON_INFO);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            setDisplayName(im, gui.text("seller-info.name"));
            List<String> lore = new ArrayList<>(gui.lore("seller-info.lore", Map.of(
                    "seller", sellerName, "amount", activeCount)));
            lore.add("");
            im.setLore(lore);
            applyDisplayFlags(im);
            info.setItemMeta(im);
        }
        hideAttributes(info);
        setSlot(inv, 13, info);
        ItemStack back = gui.item("actions.back", Material.PLAYER_HEAD, Map.of());
        setSlot(inv, 45, back);
        fillSellerInventory(sv);
        openGui(viewer, inv);
    }

    void handleSellerClick(Player player, SellerView sv, int slot, boolean leftClick, boolean rightClick) {

        // --- BACK ---
        if (slot == 45) {
            sellerViews.remove(player.getUniqueId());
            openAuction(player);
            plugin.playSound(player, "gui.open");
            return;
        }

        // --- SORT ---
        if (slot == 53) {
            if (leftClick) sv.sort = MarketFilter.nextSort(sv.sort);
            else if (rightClick) sv.sort = MarketFilter.prevSort(sv.sort);
            fillSellerInventory(sv);
            plugin.playSound(player, "gui.click");
            return;
        }

        // --- Покупка предмета ---
        String id = sv.slotToListingId.get(slot);
        if (id == null) return;

        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0 || !"ACTIVE".equalsIgnoreCase(listing.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            setSlot(sv.inventory, slot, null);
            sv.slotToListingId.remove(slot);
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }

        if (listing.sellerId().equals(player.getUniqueId())) {
            player.sendMessage(messages.message("error.own-listing"));
            plugin.playSound(player, "error.default");
            return;
        }

        sellerViews.remove(player.getUniqueId());
        if (isBundle(listing) && leftClick) openBundlePreview(player, listing);
        else openPurchaseGui(player, listing);
    }


    private void showNoMoneyBarrier(Player player, AuctionView view, int slot, MarketListing listing) {
        Inventory inv = view.inventory;
        ItemStack barrier = gui.item("actions.no-money", Material.BARRIER, Map.of());
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            setDisplayName(meta, gui.text("actions.no-money.name"));
            applyDisplayFlags(meta);
            barrier.setItemMeta(meta);
        }
        hideAttributes(barrier);
        inv.setItem(slot, barrier);
        plugin.playSound(player, "error.no-money");
        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = player;
                if (p == null || !p.isOnline()) return;
                if (!p.getOpenInventory().getTopInventory().equals(inv)) return;
                fillAuctionInventory(p, view);
            }
        }.runTaskLater(plugin, 40L);
    }

    void handleAuctionClick(Player player, AuctionView view, int slot, boolean leftClick, boolean rightClick) {
        if (slot == layout.auctionSwitch() && view.isSearch) {
            openAuction(player);
            plugin.playSound(player, "gui.open");
            return;
        }
        if (slot == layout.myItems()) {
            openMyItems(player);
            plugin.playSound(player, "gui.open");
            return;
        }
        if (slot == layout.previous()) {
            if (view.page > 0) {
                view.page--;
                fillAuctionInventory(player, view);
                plugin.playSound(player, "gui.page-previous");
            }
            return;
        }
        if (slot == layout.next()) {
            view.page++;
            fillAuctionInventory(player, view);
            plugin.playSound(player, "gui.page-next");
            return;
        }
        if (slot == layout.category() && !view.isSearch) {
            if (leftClick) {
                view.category = categories.next(view.category);
            } else if (rightClick) {
                view.category = categories.previous(view.category);
            }
            view.page = 0;
            fillAuctionInventory(player, view);
            plugin.playSound(player, "gui.click");
            return;
        }

        int sortSlot = layout.sort();
        if (slot == sortSlot) {
            if (leftClick) {
                view.sort = MarketFilter.nextSort(view.sort);
            } else if (rightClick) {
                view.sort = MarketFilter.prevSort(view.sort);
            }
            view.page = 0;
            fillAuctionInventory(player, view);
            plugin.playSound(player, "gui.click");
            return;
        }

        String id = view.slotToListingId.get(slot);
        if (id == null) return;
        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(listing.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            refreshAllViews();
            plugin.playSound(player, "error.default");
            return;
        }
        UUID viewerId = player.getUniqueId();
        if (listing.sellerId().equals(viewerId)) {
            if (isBundle(listing) && leftClick) {
                openBundlePreview(player, listing);
                return;
            }
            String actionKey = "return:" + listing.id();
            if (!pendingListingActions.add(actionKey)) return;
            storageAsync(() -> {
                repository.updateStatus(listing.id(), "RETURNED");
                return Boolean.TRUE;
            }, ignored -> {
                pendingListingActions.remove(actionKey);
                sync().listingUpdated(listing.withStatus("RETURNED"));
                if (player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                    setSlot(view.inventory, slot, null);
                    view.slotToListingId.remove(slot);
                }
                player.sendMessage(messages.message("success.listing-returned"));
                plugin.playSound(player, "action.listing-returned");
            }, exception -> {
                pendingListingActions.remove(actionKey);
                plugin.getLogger().warning("Не удалось вернуть лот " + listing.id() + ": " + exception.getMessage());
                player.sendMessage(messages.message("error.purchase-failed"));
            });
            return;
        }
        if (isBundle(listing) && leftClick) {
            openBundlePreview(player, listing);
            return;
        }
        double minCost = listing.pricePerUnit();
        if (!payment.has(player, minCost)) {
            player.sendMessage(messages.message("error.insufficient-funds"));
            showNoMoneyBarrier(player, view, slot, listing);
            return;
        }
        openPurchaseGui(player, listing);
    }

    void closeView(UUID viewerId, Inventory inventory) {
        if (inventory.getHolder() instanceof PurchaseView) purchaseViews.remove(viewerId);
        else if (inventory.getHolder() instanceof SellerView) sellerViews.remove(viewerId);
        else if (inventory.getHolder() instanceof MyItemsView) myItemsViews.remove(viewerId);
        else if (inventory.getHolder() instanceof FavoritesView) favoritesViews.remove(viewerId);
        else if (inventory.getHolder() instanceof NotificationCatalogView) notificationCatalogViews.remove(viewerId);
        else if (inventory.getHolder() instanceof BundleCreateView) bundleCreateViews.remove(viewerId);
        else if (inventory.getHolder() instanceof AuctionView) auctionViews.remove(viewerId);
    }

    public void removeViewer(UUID viewerId) {
        purchaseViews.remove(viewerId);
        myItemsViews.remove(viewerId);
        auctionViews.remove(viewerId);
        sellerViews.remove(viewerId);
        favoritesViews.remove(viewerId);
        notificationCatalogViews.remove(viewerId);
        bundleCreateViews.remove(viewerId);
    }

    ItemStack hideAttributes(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyDisplayFlags(meta);
            if (MarketBundle.isBundle(plugin, item)) {
                if (!meta.hasDisplayName()) setDisplayName(meta, "§6Набор");
                try {
                    meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
                } catch (IllegalArgumentException ignored) {
                    // Legacy server versions do not expose this item flag.
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyDisplayFlags(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    }

    /**
     * Auction items must expose their real enchantments. This only changes the cloned GUI item,
     * so the stored lot and the item delivered to the buyer keep their original metadata.
     */
    private void applyListingDisplayFlags(ItemMeta meta) {
        Component name = meta.displayName();
        if (name != null) meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    }

    private MarketListing loadListingById(String id) {
        return sync().byId(id).orElse(null);
    }

    private <T> void storageAsync(Callable<T> operation, Consumer<T> success,
                                  Consumer<Throwable> failure) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result;
            try {
                result = operation.call();
            } catch (Throwable throwable) {
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> failure.accept(throwable));
                }
                return;
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> success.accept(result));
        });
    }
}
