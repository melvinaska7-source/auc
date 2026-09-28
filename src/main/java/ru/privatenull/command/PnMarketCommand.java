package ru.privatenull.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;

import java.util.Locale;

public final class PnMarketCommand implements CommandExecutor {
    private final PnMarketPlugin plugin;
    private final PnMarketCommandMenu menu;

    public PnMarketCommand(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.menu = new PnMarketCommandMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pnmarket.admin")) {
            sender.sendMessage(plugin.messages().message("command.no-permission"));
            return true;
        }
        if (args.length == 0) {
            menu.send(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "machine" -> machine(sender);
            default -> menu.send(sender);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        plugin.reloadRuntime();
        sender.sendMessage(plugin.messages().message("command.reloaded"));
    }

    private void machine(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().message("command.only-player"));
            return;
        }
        plugin.openMachine(player);
    }
}
