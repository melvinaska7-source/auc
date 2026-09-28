package ru.privatenull;

import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.command.*;
import ru.privatenull.delivery.DeliveryService;
import ru.privatenull.config.*;
import ru.privatenull.gui.*;
import ru.privatenull.gui.machine.MarketMachineService;
import ru.privatenull.market.*;
import ru.privatenull.model.*;
import ru.privatenull.notification.*;
import ru.privatenull.pnlibrary.compat.*;
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.pnlibrary.localization.MinecraftLocale;
import ru.privatenull.pnlibrary.lifecycle.PluginRuntime;
import ru.privatenull.pnlibrary.gui.GuiAnimationProfile;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.text.*;
import ru.privatenull.pnlibrary.update.GitHubUpdater;
import ru.privatenull.service.*;
import ru.privatenull.util.*;

import java.util.*;

public final class PnMarketPlugin extends JavaPlugin {
    private static final String SUPPORT_DISCORD = "https://discord.gg/SZxPP9surw";

    private MessagesConfig messages;
    private GuiConfig guiConfig;
    private SoundsConfig sounds;
    private ItemLocalization itemLocalization;
    private MarketRuntime runtime;
    private ListingService listings;
    private PriceFormatter prices;
    private CommissionService commissions;
    private PendingNotificationService notifications;
    private DeliveryService deliveries;
    private AutoBuyPriceInputService autoBuyPriceInput;
    private final Map<UUID, Long> notificationOfflineSince = new HashMap<>();
    private GitHubUpdater updateChecker;
    private MarketMachineService machine;
    private GuiUpdateService guiUpdates;
    private MarketStorageFactory storageFactory;
    private final Map<UUID, Integer> guiTransitionOrigins = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessagesConfig(this);
        guiConfig = new GuiConfig(this);
        sounds = new SoundsConfig(this);
        guiUpdates = GuiUpdateService.protocolLib(this);
        commissions = new CommissionService(this);
        loadItemLocalization();
        storageFactory = new MarketStorageFactory(this);
        if (!supportedServer() || !startRuntime()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        notifications = new PendingNotificationService(this);
        deliveries = new DeliveryService(this);
        getServer().getPluginManager().registerEvents(deliveries, this);
        autoBuyPriceInput = new AutoBuyPriceInputService(this);
        getServer().getPluginManager().registerEvents(autoBuyPriceInput, this);
        prices = new PriceFormatter(this);
        ListingPolicy policy = new ListingPolicy(this, runtime.currencies());
        listings = new ListingService(this, runtime, policy, messages);

        registerCommands();
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(this), this);
        machine = new MarketMachineService(this);
        getServer().getPluginManager().registerEvents(machine, this);
        startUpdateChecker();
        new Metrics(this, 32716);
    }

    @Override
    public void onDisable() {
        if (machine != null) machine.shutdown();
        if (notifications != null) notifications.close();
        if (runtime != null) runtime.close();
        if (deliveries != null) deliveries.close();
        if (storageFactory != null) storageFactory.close();
        if (guiUpdates != null) guiUpdates.close();
        if (updateChecker != null) updateChecker.cancel();
    }

    public void reloadRuntime() {
        reloadConfig();
        messages.reload();
        guiConfig.reload();
        sounds.reload();
        loadItemLocalization();
        runtime.reload();
        if (updateChecker != null) updateChecker.cancel();
        startUpdateChecker();
    }

    public void reloadGuiRuntime() {
        guiConfig.reload();
        runtime.reload();
    }

    public MessagesConfig messages() {
        return messages;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public GuiAnimationProfile guiAnimationProfile() {
        return GuiAnimationProfile.standard();
    }

    public GuiUpdateService guiUpdates() {
        return guiUpdates;
    }

    public MarketStorageFactory storageFactory() {
        return storageFactory;
    }

    public int guiTransitionOrigin(Player player) {
        return player == null ? -1 : guiTransitionOrigins.getOrDefault(player.getUniqueId(), -1);
    }

    public void withGuiTransition(Player player, int sourceSlot, Runnable action) {
        if (player == null || action == null) return;
        UUID playerId = player.getUniqueId();
        Integer previous = guiTransitionOrigins.put(playerId, sourceSlot);
        try {
            action.run();
        } finally {
            if (previous == null) guiTransitionOrigins.remove(playerId);
            else guiTransitionOrigins.put(playerId, previous);
        }
    }

    public void playSound(Player player, String path) {
        if (sounds != null) sounds.play(player, path);
    }

    public ItemLocalization itemLocalization() {
        return itemLocalization;
    }

    public void openMachine(Player player) {
        machine.open(player);
    }

    public GitHubUpdater getUpdateChecker() {
        return updateChecker;
    }

    public String getSupportDiscord() {
        return SUPPORT_DISCORD;
    }

    public FavoriteService favorites() {
        return runtime.favorites();
    }

    public MarketSync marketSync() {
        return runtime.sync(false);
    }

    public MarketGuiController gui() {
        return runtime.gui(false);
    }

    public List<MarketListing> activeListings(boolean donate) {
        return runtime.activeListings(donate);
    }

    public void openAuction(Player player) {
        runtime.openAuction(player, false);
    }

    public void openAuction(Player player, boolean donate) {
        runtime.openAuction(player, donate);
    }

    public void openAuctionSearch(Player player, String query) {
        runtime.openSearch(player, query, false);
    }

    public void openAuctionSearch(Player player, String query, boolean donate) {
        runtime.openSearch(player, query, donate);
    }

    public void openSellerGui(Player player, UUID seller, boolean donate) {
        runtime.openSeller(player, seller, donate);
    }

    public void openSellerGui(Player player, UUID seller) {
        runtime.openSeller(player, seller, false);
    }

    public void openFavorites(Player player, boolean donate) {
        runtime.openFavorites(player, donate);
    }

    public void openNotificationCatalog(Player player, boolean donate) {
        runtime.openNotificationCatalog(player, donate);
    }

    public void openListing(Player player, String listingId, boolean donate) {
        runtime.openListing(player, listingId, donate);
    }

    public void autoPurchase(Player player, MarketListing listing, boolean donate) {
        runtime.autoPurchase(player.getUniqueId(), listing, donate);
    }

    public void autoPurchase(UUID playerId, MarketListing listing, boolean donate) {
        runtime.autoPurchase(playerId, listing, donate);
    }

    public DeliveryService deliveries() {
        return deliveries;
    }

    public void openDeliveries(Player player) {
        deliveries.open(player);
    }

    public void requestAutoBuyPrice(Player player, String itemKey, boolean donate) {
        autoBuyPriceInput.begin(player, itemKey, donate);
    }

    public void requestAutoBuyPrice(Player player, FavoriteFilter filter, boolean donate) {
        autoBuyPriceInput.begin(player, filter, donate);
    }

    public void renderAllViews() {
        runtime.renderAll();
    }

    public void removeViewer(UUID viewer) {
        runtime.removeViewer(viewer);
    }

    public void sell(Player player, String price) {
        listings.sell(player, price, false);
    }

    public void sellPoints(Player player, String price) {
        listings.sell(player, price, true);
    }

    public void sellAuto(Player player, boolean donate) {
        listings.sellAuto(player, donate);
    }

    public void sellKit(Player player, String price, boolean donate) {
        listings.sellKit(player, price, donate, "Набор");
    }

    public void sellKit(Player player, String price, boolean donate, String name) {
        listings.sellKit(player, price, donate, name);
    }

    public boolean confirmKitListing(Player player, boolean donate, String name, double price,
                                     Map<Integer, ItemStack> source) {
        return listings.confirmKit(player, donate, name, price, source);
    }

    public void prepareJoinNotifications(Player player) {
        if (player == null) return;
        // PlayerLoginEvent runs before the joining Player's saved data is loaded.
        // Read the persisted profile while the player is still offline instead.
        UUID playerId = player.getUniqueId();
        OfflinePlayer savedPlayer = getServer().getOfflinePlayer(playerId);
        notificationOfflineSince.put(playerId,
                savedPlayer.hasPlayedBefore() ? savedPlayer.getLastSeen() : -1L);
    }

    public void notifyOnJoin(Player player) {
        if (updateChecker != null) updateChecker.notifyAdminOnJoin(player);
        if (notifications != null) notifications.deliver(player);
        if (deliveries != null) deliveries.deliverFitting(player);
        long offlineSince = notificationOfflineSince.getOrDefault(player.getUniqueId(), -1L);
        notificationOfflineSince.remove(player.getUniqueId());
        if (runtime == null || runtime.favorites() == null || offlineSince <= 0) return;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                long now = System.currentTimeMillis();
                List<MarketListing> regular = runtime.storage(false) == null
                        ? List.of() : runtime.storage(false).findActiveCreatedAfter(offlineSince, now);
                List<MarketListing> donate = runtime.storage(true) == null
                        ? List.of() : runtime.storage(true).findActiveCreatedAfter(offlineSince, now);
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline()) {
                        runtime.favorites().notifyAvailable(player, offlineSince, regular, donate);
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().warning("Не удалось проверить актуальные уведомления при входе: "
                        + exception.getMessage());
            }
        });
    }

    public void queueNotification(UUID playerId, String message) {
        Player online = getServer().getPlayer(playerId);
        if (online != null && online.isOnline()) online.sendMessage(message);
        else if (notifications != null) notifications.queue(playerId, message);
    }

    public void notifySellerSale(Player buyer, MarketListing listing, double price, boolean donate) {
        notifySellerSale(buyer.getName(), listing, price, donate);
    }

    public void notifySellerSale(String buyerName, MarketListing listing, double price, boolean donate) {
        OfflinePlayer seller = getServer().getOfflinePlayer(listing.sellerId());
        double commission = commissions.sale(seller, runtime.payment(donate), price);
        String message = commission > 0
                ? "notification.seller-sale-with-commission" : "notification.seller-sale";
        queueNotification(listing.sellerId(), messages.message(message, Map.of(
                "buyer", buyerName,
                "item", itemLocalization.getPlainName(listing.item()),
                "price", formatPrice(donate, price, null),
                "commission", formatPrice(donate, commission, null),
                "received", formatPrice(donate, Math.max(0, price - commission), null))));
    }

    public String formatPrice(boolean donate, double amount, String formatted) {
        return prices.format(amount, donate);
    }

    public CommissionService commissions() {
        return commissions;
    }

    public String commissionGroup(OfflinePlayer player) {
        var permission = runtime == null ? null : runtime.currencies().permission();
        if (permission == null || player == null) return "default";
        try {
            String group = permission.getPrimaryGroup(null, player);
            return group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return "default";
        }
    }

    public void relist(Player player, MarketListing listing, boolean donate) {
        listings.relist(player, listing, donate);
    }

    private boolean supportedServer() {
        ServerVersion version = ServerVersion.current();
        if (!version.isKnown() || !version.isBefore(1, 16, 5)) return true;
        getLogger().severe("pnMarket требует Minecraft 1.16.5 или новее; обнаружено " + version);
        return false;
    }

    private void loadItemLocalization() {
        String configured = getConfig().getString("localization.locale", "ru_ru");
        try {
            itemLocalization = ItemLocalization.load(configured);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Неизвестная локализация предметов '" + configured
                    + "', используется ru_ru.");
            itemLocalization = ItemLocalization.load(MinecraftLocale.RU_RU);
        }
    }

    private boolean startRuntime() {
        runtime = new MarketRuntime(this, messages, guiConfig, new GuiLabels(guiConfig));
        if (runtime.start()) return true;
        getLogger().severe("Не удалось инициализировать валюту или хранилище pnMarket.");
        return false;
    }

    private void registerCommands() {
        register("ah", false);
        register("dah", true);
        var command = Objects.requireNonNull(getCommand("pnmarket"),
                "Команда pnmarket отсутствует в plugin.yml");
        command.setExecutor(new PnMarketCommand(this));
        command.setTabCompleter(new PnMarketTabCompleter());
    }

    private void register(String name, boolean donate) {
        var command = Objects.requireNonNull(getCommand(name), "Команда " + name + " отсутствует в plugin.yml");
        var handler = new AuctionCommand(this, donate);
        command.setExecutor(handler);
        command.setTabCompleter(new AuctionTabCompleter(this, donate));
    }

    private void startUpdateChecker() {
        updateChecker = new GitHubUpdater(this, "pnFolder/pnMarket", "pnmarket.admin", SUPPORT_DISCORD);
        updateChecker.start();
    }
}
