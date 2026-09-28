package ru.privatenull.service;

import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.config.*;
import ru.privatenull.currency.MarketPayment;
import ru.privatenull.gui.MarketGuiController;
import ru.privatenull.market.*;
import ru.privatenull.model.MarketListing;
import ru.privatenull.storage.MarketStorage;

import java.util.*;

public final class MarketRuntime implements AutoCloseable {
    private final PnMarketPlugin plugin;
    private final MessagesConfig messages;
    private final GuiConfig gui;
    private final GuiLabels labels;
    private final CurrencyRegistry currencies;
    private final MarketStorageFactory storageFactory;
    private MarketStorage regularStorage;
    private MarketStorage donateStorage;
    private MarketSync regularSync;
    private MarketSync donateSync;
    private MarketGuiController regularGui;
    private MarketGuiController donateGui;
    private MarketCategories categories;
    private FavoriteService favorites;

    public MarketRuntime(PnMarketPlugin plugin, MessagesConfig messages, GuiConfig gui, GuiLabels labels) {
        this.plugin = plugin;
        this.messages = messages;
        this.gui = gui;
        this.labels = labels;
        this.currencies = new CurrencyRegistry(plugin);
        this.storageFactory = plugin.storageFactory();
    }

    public boolean start() {
        if (!currencies.reload()) return false;
        regularStorage = openStorage(false);
        donateStorage = openStorage(true);
        if (regularStorage == null && donateStorage == null) return false;
        categories = MarketCategories.load(gui.configuration(), plugin.getLogger());
        favorites = new FavoriteService(plugin);
        rebuildViews();
        return true;
    }

    public void reload() {
        shutdownViews();
        currencies.reload();
        categories = MarketCategories.load(gui.configuration(), plugin.getLogger());
        regularStorage = updateStorage(false, regularStorage);
        donateStorage = updateStorage(true, donateStorage);
        rebuildViews();
        if (regularSync != null) regularSync.refreshAsync();
        if (donateSync != null) donateSync.refreshAsync();
    }

    public MarketStorage storage(boolean donate) {
        return donate ? donateStorage : regularStorage;
    }

    public MarketSync sync(boolean donate) {
        return donate ? donateSync : regularSync;
    }

    public MarketGuiController gui(boolean donate) {
        return donate ? donateGui : regularGui;
    }

    public MarketPayment payment(boolean donate) {
        return currencies.payment(donate);
    }

    public CurrencyRegistry currencies() {
        return currencies;
    }

    public FavoriteService favorites() {
        return favorites;
    }

    public List<MarketListing> activeListings(boolean donate) {
        MarketGuiController controller = gui(donate);
        return controller == null ? List.of() : controller.activeListings();
    }

    public void openAuction(Player player, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller == null) {
            player.sendMessage("§cАукцион недоступен: настроенная валюта не найдена.");
            plugin.playSound(player, "error.default");
            return;
        }
        controller.openAuction(player);
    }

    public void openSearch(Player player, String query, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.openAuctionSearch(player, query);
        else openAuction(player, donate);
    }

    public void openSeller(Player player, UUID seller, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.openSellerGui(player, seller);
        else openAuction(player, donate);
    }

    public void openFavorites(Player player, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.openFavorites(player);
        else openAuction(player, donate);
    }

    public void openNotificationCatalog(Player player, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.openNotificationCatalog(player, 0);
        else openAuction(player, donate);
    }

    public void openListing(Player player, String listingId, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.openListing(player, listingId);
        else openAuction(player, donate);
    }

    public void autoPurchase(UUID playerId, MarketListing listing, boolean donate) {
        MarketGuiController controller = gui(donate);
        if (controller != null) controller.autoPurchase(playerId, listing);
    }

    public void renderAll() {
        if (regularGui != null) regularGui.renderAllViews();
        if (donateGui != null) donateGui.renderAllViews();
    }

    public void removeViewer(UUID viewer) {
        if (regularGui != null) regularGui.removeViewer(viewer);
        if (donateGui != null) donateGui.removeViewer(viewer);
    }

    private void rebuildViews() {
        if (regularStorage != null && currencies.payment(false) != null) {
            regularSync = new MarketSync(plugin, regularStorage);
            regularGui = controller(false, regularStorage, regularSync);
        }
        if (donateStorage != null && currencies.payment(true) != null) {
            donateSync = new MarketSync(plugin, donateStorage);
            donateGui = controller(true, donateStorage, donateSync);
        }
    }

    private MarketStorage updateStorage(boolean donate, MarketStorage current) {
        if (currencies.payment(donate) != null) return current == null ? openStorage(donate) : current;
        if (current != null) current.close();
        return null;
    }

    private MarketStorage openStorage(boolean donate) {
        if (currencies.payment(donate) == null) return null;
        try {
            return storageFactory.open(donate);
        } catch (RuntimeException exception) {
            String name = donate ? "донат-аукциона" : "обычного аукциона";
            plugin.getLogger().warning("Не удалось открыть хранилище " + name + ": " + exception.getMessage());
            return null;
        }
    }

    private MarketGuiController controller(boolean donate, MarketStorage storage, MarketSync sync) {
        return new MarketGuiController(plugin, storage, currencies.payment(donate),
                messages, gui, labels, categories, sync, donate);
    }

    private void shutdownViews() {
        if (regularGui != null) regularGui.shutdown();
        if (donateGui != null) donateGui.shutdown();
        if (regularSync != null) regularSync.cancel();
        if (donateSync != null) donateSync.cancel();
        regularGui = null;
        donateGui = null;
        regularSync = null;
        donateSync = null;
    }

    @Override
    public void close() {
        shutdownViews();
        if (favorites != null) favorites.close();
        if (regularStorage != null) regularStorage.close();
        if (donateStorage != null) donateStorage.close();
    }
}
