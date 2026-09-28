package ru.privatenull.notification;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.service.MarketStorageFactory;
import ru.privatenull.storage.MarketStorage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Durable chat notifications delivered on the player's next join. */
public final class PendingNotificationService implements AutoCloseable {
    private final PnMarketPlugin plugin;
    private final MarketStorage storage;
    private final ExecutorService executor;

    public PendingNotificationService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.storage = plugin.storageFactory().openNotifications();
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "pnMarket-notifications");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void queue(UUID playerId, String message) {
        if (playerId == null || message == null || message.isBlank()) return;
        submit(() -> {
            try {
                storage.queue(playerId, message);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Не удалось сохранить отложенное уведомление: "
                        + exception.getMessage());
            }
        });
    }

    public void deliver(Player player) {
        UUID playerId = player.getUniqueId();
        submit(() -> {
            List<String> messages;
            try {
                messages = storage.takeAll(playerId);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Не удалось получить отложенные уведомления: "
                        + exception.getMessage());
                return;
            }
            if (messages.isEmpty()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> deliver(player, messages));
            } catch (RuntimeException exception) {
                requeue(playerId, messages);
            }
        });
    }

    private void deliver(Player player, List<String> messages) {
        if (!player.isOnline()) {
            UUID playerId = player.getUniqueId();
            submit(() -> requeue(playerId, messages));
            return;
        }
        messages.forEach(player::sendMessage);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        storage.close();
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
            // The plugin is already shutting down.
        }
    }

    private void requeue(UUID playerId, List<String> messages) {
        for (String message : messages) storage.queue(playerId, message);
    }
}
