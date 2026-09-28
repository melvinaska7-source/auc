package ru.privatenull.service;

import ru.privatenull.PnMarketPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.math.*;

public final class PriceFormatter {
    private final PnMarketPlugin plugin;

    public PriceFormatter(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public String format(double value, boolean donate) {
        String mode = plugin.getConfig().getString("price.mode", "short");
        String amount = mode.equalsIgnoreCase("full") ? full(value) : shortAmount(value);
        String currency = donate ? "donate" : "default";
        String fallback = donate ? "&d{amount} PP" : "&a{amount}⛃";
        String path = "currency." + currency;
        String template = plugin.getConfig().contains(path + ".format")
                ? plugin.getConfig().getString(path + ".format", fallback)
                : plugin.getConfig().getString(path + ".template", fallback);
        return ColorUtil.colorize(template.replace("{amount}", amount));
    }

    private String full(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String shortAmount(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000_000_000D) {
            return scaled(value, 1_000_000_000_000_000D, "quadrillion", "Q");
        }
        if (absolute >= 1_000_000_000_000D) {
            return scaled(value, 1_000_000_000_000D, "trillion", "T");
        }
        if (absolute >= 1_000_000_000D) return scaled(value, 1_000_000_000D, "billion", "B");
        if (absolute >= 1_000_000D) return scaled(value, 1_000_000D, "million", "M");
        if (absolute >= 1_000D) return scaled(value, 1_000D, "thousand", "K");
        return full(value);
    }

    private String scaled(double value, double divisor, String unit, String fallback) {
        int decimals = Math.max(0, Math.min(3, plugin.getConfig().getInt("price.short.decimals", 1)));
        String suffix = plugin.getConfig().getString("price.short." + unit, fallback);
        return BigDecimal.valueOf(value / divisor).setScale(decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffix;
    }
}
