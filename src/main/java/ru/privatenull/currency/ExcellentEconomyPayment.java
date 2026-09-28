package ru.privatenull.currency;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** ExcellentEconomy API hook. Reflection keeps pnMarket compatible with servers without the optional plugin. */
public final class ExcellentEconomyPayment implements MarketPayment {
    private static final String API_CLASS = "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI";
    private final Object api;
    private final String currencyId;

    public ExcellentEconomyPayment(String currencyId) {
        this.currencyId = currencyId;
        this.api = findApi();
    }

    @Override
    public boolean isAvailable() {
        return api != null && invokeBoolean("hasCurrency", new Class<?>[]{String.class}, currencyId);
    }

    @Override
    public boolean has(Player player, double amount) {
        if (api == null || !valid(amount)) return false;
        try {
            Method method = api.getClass().getMethod("getBalance", Player.class, String.class);
            Object value = method.invoke(api, player, currencyId);
            return value instanceof Number number && number.doubleValue() >= amount;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return valid(amount) && invokeBoolean("withdraw",
                new Class<?>[]{Player.class, String.class, double.class}, player, currencyId, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return asyncOperation("withdrawAsync", player.getUniqueId(), amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return asyncOperation("depositAsync", player.getUniqueId(), amount);
    }

    private boolean asyncOperation(String methodName, UUID playerId, double amount) {
        if (api == null || !valid(amount)) return false;
        try {
            Method method = api.getClass().getMethod(methodName, UUID.class, String.class, double.class);
            Object value = method.invoke(api, playerId, currencyId, amount);
            if (!(value instanceof CompletableFuture<?> future)) return false;
            Object result = future.get(5, TimeUnit.SECONDS);
            if (result instanceof Boolean bool) return bool;
            if (result == null) return false;
            for (String resultMethod : new String[]{"isSuccess", "isSuccessful", "success"}) {
                try {
                    Method successful = result.getClass().getMethod(resultMethod);
                    return Boolean.TRUE.equals(successful.invoke(result));
                } catch (NoSuchMethodException ignored) {
                }
            }
            return "SUCCESS".equalsIgnoreCase(String.valueOf(result));
        } catch (ReflectiveOperationException | java.util.concurrent.TimeoutException
                 | java.util.concurrent.ExecutionException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean invokeBoolean(String methodName, Class<?>[] types, Object... arguments) {
        if (api == null) return false;
        try {
            return Boolean.TRUE.equals(api.getClass().getMethod(methodName, types).invoke(api, arguments));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findApi() {
        try {
            Class apiClass = Class.forName(API_CLASS);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    private static boolean valid(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }
}
