package ru.privatenull.pnlibrary.update;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Заглушка проверки обновлений. Не зависит от новых классов pnLibrary
 * (PluginBanner / PluginUpdateService), чтобы плагин собирался с pnLibrary
 * на коммите из README. Проверка обновлений отключена.
 */
public final class GitHubUpdater {

    public GitHubUpdater(JavaPlugin plugin, String repository, String permission, String supportUrl) {
    }

    public void start() {
    }

    public void cancel() {
    }

    public void notifyAdminOnJoin(Player player) {
    }

    public boolean isCheckCompleted() {
        return true;
    }

    public boolean isUpdateAvailable() {
        return false;
    }

    public String getLatestVersion() {
        return null;
    }

    public String getLastError() {
        return null;
    }
}
