package ru.privatenull.command;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.pnlibrary.compat.ServerVersion;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.update.GitHubUpdater;

final class PnMarketCommandMenu {
    private final PnMarketPlugin plugin;

    PnMarketCommandMenu(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    void send(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String support = plugin.getSupportDiscord();
        sender.sendMessage(ColorUtil.component(plugin.messages().message("prefix")
                + " &8| &fПанель администратора"));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#9EFC65 «Состояние плагина»"));
        sender.sendMessage(ColorUtil.component(" &7- &fВерсия: &#FCA865" + version));
        sender.sendMessage(ColorUtil.component(" &7- &fСервер: &#FCA865" + ServerVersion.current()));
        sender.sendMessage(ColorUtil.component(" &7- &fОбновление: " + updateStatus(version)));
        sender.sendMessage(ColorUtil.component(" &7- &fПоддержка: ")
                .append(ColorUtil.component("&#65D1FC" + support).clickEvent(ClickEvent.openUrl(support))));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#65D1FC «Управление»"));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pnmarket reload &8— &7перезагрузить настройки"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pnmarket machine &8— &7открыть Machine"));
    }

    private String updateStatus(String currentVersion) {
        GitHubUpdater checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) return "&7проверяется";
        if (checker.isUpdateAvailable()) {
            String latest = checker.getLatestVersion();
            return "&eдоступно &f" + currentVersion + " &8→ &#D8DF9D"
                    + (latest == null || latest.isBlank() ? "неизвестно" : latest);
        }
        if (checker.getLastError() != null && !checker.getLastError().isBlank()) {
            return "&cпроверка недоступна";
        }
        return "&aактуальная версия";
    }
}
