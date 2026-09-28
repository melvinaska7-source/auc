package ru.privatenull.currency;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PlayerPointsPayment implements MarketPayment {
    private final PlayerPointsAPI api;

    public PlayerPointsPayment(PlayerPointsAPI api) {
        this.api = api;
    }

    @Override
    public boolean isAvailable() {
        return api != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        int points = toPoints(amount);
        return points > 0 && api != null && api.look(player.getUniqueId()) >= points;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        int points = toPoints(amount);
        return points > 0 && api != null && api.take(player.getUniqueId(), points);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        int points = toPoints(amount);
        return points > 0 && api != null && api.take(player.getUniqueId(), points);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        int points = toPoints(amount);
        return points > 0 && api != null && api.give(player.getUniqueId(), points);
    }

    public boolean supports(double amount) {
        return Double.isFinite(amount) && amount > 0 && amount <= Integer.MAX_VALUE
                && Math.rint(amount) == amount;
    }

    private int toPoints(double amount) {
        if (!supports(amount)) return -1;
        return (int) amount;
    }
}
