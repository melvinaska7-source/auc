package ru.privatenull.command;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.*;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.market.MarketSearch;

import java.util.*;

public final class AuctionTabCompleter implements TabCompleter {
    private final PnMarketPlugin plugin;
    private final boolean donate;

    public AuctionTabCompleter(PnMarketPlugin plugin, boolean donate) {
        this.plugin = plugin;
        this.donate = donate;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return filter(List.of("sell", "kit", "notify", "delivery", "search", "show", "help"), args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (action.equals("search") || action.equals("notify"))) {
            return MarketSearch.tabComplete(plugin.activeListings(donate), args[1], plugin.itemLocalization());
        }
        if (args.length == 2 && action.equals("sell")) return filter(List.of("auto"), args[1]);
        if (args.length == 2 && (action.equals("show") || action.equals("snow"))) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
            return filter(players, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> values, String prefix) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, values, matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

}
