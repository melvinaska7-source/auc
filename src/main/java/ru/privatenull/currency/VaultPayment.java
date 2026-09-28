package ru.privatenull.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class VaultPayment implements MarketPayment {
    private final Economy economy;

    public VaultPayment(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

}
