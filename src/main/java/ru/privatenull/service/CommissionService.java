package ru.privatenull.service;

import ru.privatenull.PnMarketPlugin;
import ru.privatenull.currency.*;
import org.bukkit.OfflinePlayer;

import java.math.*;

public final class CommissionService {
    private final PnMarketPlugin plugin;

    public CommissionService(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public double listing(OfflinePlayer player, MarketPayment payment, double price) {
        return calculate(player, payment, price, "listing");
    }

    public double sale(OfflinePlayer player, MarketPayment payment, double price) {
        return calculate(player, payment, price, "sale");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("commission.enabled", false);
    }

    private double calculate(OfflinePlayer player, MarketPayment payment, double price, String key) {
        if (!enabled() || !Double.isFinite(price) || price <= 0) return 0;
        String group = plugin.commissionGroup(player);
        String path = "commission.groups." + group + "." + key;
        String fallbackPath = "commission.groups.default." + key;
        double legacy = plugin.getConfig().getDouble("commission." + key + "-percent", 0);
        double percent = Math.max(0, Math.min(100,
                plugin.getConfig().contains(path)
                        ? plugin.getConfig().getDouble(path)
                        : plugin.getConfig().getDouble(fallbackPath, legacy)));
        if (percent == 0) return 0;
        BigDecimal raw = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        if (payment instanceof PlayerPointsPayment) return raw.setScale(0, RoundingMode.CEILING).doubleValue();
        return raw.setScale(2, RoundingMode.CEILING).doubleValue();
    }
}
