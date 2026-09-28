package ru.privatenull.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface MarketPayment {
    boolean isAvailable();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);
}
