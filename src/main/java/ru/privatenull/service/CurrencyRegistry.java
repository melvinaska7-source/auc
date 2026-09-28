package ru.privatenull.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.black_ixx.playerpoints.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.currency.*;

import java.util.Locale;

public final class CurrencyRegistry {
    private final PnMarketPlugin plugin;
    private Economy economy;
    private PlayerPointsAPI playerPoints;
    private Permission permission;
    private MarketPayment regular;
    private MarketPayment donate;

    public CurrencyRegistry(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean reload() {
        economy = service(Economy.class);
        permission = service(Permission.class);
        playerPoints = null;
        if (plugin.getServer().getPluginManager().getPlugin("PlayerPoints") instanceof PlayerPoints points) {
            playerPoints = points.getAPI();
        }
        regular = create("default", "vault");
        donate = create("donate", "playerpoints");
        if (regular != null && !regular.isAvailable()) {
            plugin.getLogger().warning("Валюта currency.default недоступна; /ah отключён.");
            regular = null;
        }
        if (donate != null && !donate.isAvailable()) {
            plugin.getLogger().warning("Валюта currency.donate недоступна; /dah отключён.");
            donate = null;
        }
        if (regular == null && donate == null) {
            plugin.getLogger().severe("Обе валюты отключены или недоступны.");
            return false;
        }
        return true;
    }

    public MarketPayment payment(boolean donateAuction) {
        return donateAuction ? donate : regular;
    }

    public Permission permission() {
        return permission;
    }

    private MarketPayment create(String id, String fallback) {
        String path = "currency." + id;
        String legacy = "currencies." + id;
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            plugin.getLogger().info("Валюта " + path + " отключена в config.yml.");
            return null;
        }
        String provider = first(path + ".type", path + ".provider", legacy + ".provider", fallback)
                .trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "vault" -> new VaultPayment(economy);
            case "playerpoints", "points" -> new PlayerPointsPayment(playerPoints);
            case "excellent", "excellenteconomy" -> new ExcellentEconomyPayment(
                    currencyId(path, legacy));
            case "disabled", "none" -> null;
            default -> {
                plugin.getLogger().warning("Неизвестный type валюты " + path + ": " + provider);
                yield null;
            }
        };
    }

    private String string(String path, String legacy, String fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getString(path, fallback)
                : plugin.getConfig().getString(legacy, fallback);
    }

    private String first(String primary, String secondary, String legacy, String fallback) {
        if (plugin.getConfig().contains(primary)) return plugin.getConfig().getString(primary, fallback);
        return string(secondary, legacy, fallback);
    }

    private String currencyId(String path, String legacy) {
        if (plugin.getConfig().contains(path + ".excellent-id")) {
            return plugin.getConfig().getString(path + ".excellent-id", "coins");
        }
        return first(path + ".excellent.id", path + ".id", legacy + ".currency-id", "coins");
    }

    private <T> T service(Class<T> type) {
        RegisteredServiceProvider<T> registration = plugin.getServer().getServicesManager().getRegistration(type);
        return registration == null ? null : registration.getProvider();
    }
}
