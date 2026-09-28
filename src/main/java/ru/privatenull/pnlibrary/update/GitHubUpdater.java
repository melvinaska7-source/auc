package ru.privatenull.pnlibrary.update;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.banner.PluginBanner;

/**
 * Compatibility adapter for pnMarket's legacy updater calls.
 * Bridges the old GitHubUpdater surface to the current pnLibrary update service.
 */
public final class GitHubUpdater {
    private final PluginUpdateService service;

    public GitHubUpdater(JavaPlugin plugin, String repository, String permission, String supportUrl) {
        String[] parts = repository == null ? new String[0] : repository.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("GitHub repository must use owner/name format");
        }

        PluginBanner.Identity identity = new PluginBanner.Identity(plugin, "pnFolder")
                .github(parts[0], parts[1])
                .supportUrl(supportUrl)
                .notifyAdministrators(true)
                .notifyOnlineAdministrators(false)
                .notifyAdministratorsOnJoin(true)
                .notificationPermission(permission)
                .showUpToDateMessage(false)
                .autoDownloadUpdates(false);

        this.service = new PluginUpdateService(identity, new UpdateReporter() {
            @Override
            public void updateAvailable(String latestVersion, String releaseUrl) {
                plugin.getLogger().info("Доступна новая версия pnMarket: " + latestVersion + " - " + releaseUrl);
            }

            @Override
            public void upToDate() {
                // Intentionally quiet; the old updater did not need a startup message here.
            }

            @Override
            public void updateDownloaded(String latestVersion, String stagedFile) {
                // Automatic downloading is disabled for pnMarket.
            }

            @Override
            public void downloadFailed(String reason) {
                plugin.getLogger().warning("Не удалось скачать обновление pnMarket: " + reason);
            }

            @Override
            public void checkFailed(String reason) {
                plugin.getLogger().warning("Не удалось проверить обновления pnMarket: " + reason);
            }
        });
    }

    public void start() {
        service.start();
    }

    public void cancel() {
        service.close();
    }

    public void notifyAdminOnJoin(Player player) {
        if (player != null && !service.isClosed()) service.notifyAdministrator(player);
    }

    public boolean isCheckCompleted() {
        return service.isCheckCompleted();
    }

    public boolean isUpdateAvailable() {
        return service.isUpdateAvailable();
    }

    public String getLatestVersion() {
        return service.latestVersion();
    }

    public String getLastError() {
        return service.lastError();
    }
}
