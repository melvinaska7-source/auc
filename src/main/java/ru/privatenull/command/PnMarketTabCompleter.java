package ru.privatenull.command;

import org.bukkit.command.*;
import org.bukkit.util.StringUtil;

import java.util.*;

public final class PnMarketTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pnmarket.admin") || args.length != 1) return List.of();
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], List.of("machine", "reload"), matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }
}
