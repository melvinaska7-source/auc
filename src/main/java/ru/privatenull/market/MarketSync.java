package ru.privatenull.market;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.MarketSnapshot;
import ru.privatenull.storage.MarketStorage;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MarketSync {
    private final PnMarketPlugin plugin;
    private final MarketStorage repository;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final AtomicLong snapshotRevision = new AtomicLong();
    private volatile MarketSnapshot snapshot = MarketSnapshot.empty();
    private BukkitTask task;

    public MarketSync(PnMarketPlugin plugin, MarketStorage repository) {
        this.plugin = plugin;
        this.repository = repository;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshNow, 1L, 40L);
    }

    public void refreshAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshNow);
    }

    public List<MarketListing> all() {
        return snapshot.all();
    }

    public List<MarketListing> bySeller(UUID sellerId) {
        return snapshot.bySeller(sellerId);
    }

    public Optional<MarketListing> byId(String id) {
        return snapshot.byId(id);
    }

    public int activeCount(UUID sellerId) {
        return snapshot.activeCount(sellerId);
    }

    public void showListingBought(String listingId) {
        refreshAsync();
    }

    public void listingCreated(MarketListing listing) {
        replaceListing(listing);
    }

    public void listingUpdated(MarketListing listing) {
        replaceListing(listing);
    }

    public void listingRemoved(String listingId) {
        List<MarketListing> listings = new ArrayList<>(snapshot.all());
        listings.removeIf(listing -> listing.id().equals(listingId));
        snapshot = new MarketSnapshot(listings);
        snapshotRevision.incrementAndGet();
        queueRender();
    }

    private void replaceListing(MarketListing listing) {
        List<MarketListing> listings = new ArrayList<>(snapshot.all());
        listings.removeIf(current -> current.id().equals(listing.id()));
        listings.add(listing);
        snapshot = new MarketSnapshot(listings);
        snapshotRevision.incrementAndGet();
        queueRender();
    }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
    }

    private void refreshNow() {
        if (!loading.compareAndSet(false, true)) return;
        try {
            long revision = snapshotRevision.get();
            MarketSnapshot refreshed = new MarketSnapshot(repository.findAll());
            if (snapshotRevision.get() != revision) return;
            snapshot = refreshed;
            queueRender();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Не удалось обновить кэш аукциона: " + exception.getMessage());
        } finally {
            loading.set(false);
        }
    }

    private void queueRender() {
        if (!plugin.isEnabled()) return;
        if (!renderQueued.compareAndSet(false, true)) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    plugin.renderAllViews();
                } finally {
                    renderQueued.set(false);
                }
            });
        } catch (RuntimeException exception) {
            renderQueued.set(false);
        }
    }
}
