package ru.privatenull.market;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.util.NumberParser;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoBuyPriceInputService implements Listener {
    private final PnMarketPlugin plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public AutoBuyPriceInputService(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, String itemKey, boolean donate) {
        pending.put(player.getUniqueId(), new PendingInput(itemKey, null, donate));
        player.closeInventory();
        player.sendMessage(plugin.messages().message("notification.auto-buy-enter-price", Map.of(
                "item", plugin.itemLocalization().getItemName(itemKey))));
    }

    public void begin(Player player, FavoriteFilter filter, boolean donate) {
        pending.put(player.getUniqueId(), new PendingInput(filter.value(), filter.id(), donate));
        player.closeInventory();
        player.sendMessage(plugin.messages().message("notification.auto-buy-enter-price", Map.of(
                "item", plugin.favorites().displayValue(filter))));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String raw = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer(), input, raw));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player player, PendingInput input, String raw) {
        if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("отмена")) {
            player.sendMessage(plugin.messages().message("notification.auto-buy-input-cancelled"));
            return;
        }
        double price;
        try {
            price = NumberParser.parse(raw);
        } catch (NumberFormatException exception) {
            player.sendMessage(plugin.messages().message("notification.auto-buy-invalid-price"));
            return;
        }
        if (price <= 0) {
            player.sendMessage(plugin.messages().message("notification.auto-buy-invalid-price"));
            return;
        }
        if (input.filterId != null) {
            if (!plugin.favorites().configureAutoBuy(player.getUniqueId(), input.donate, input.filterId, price)) {
                player.sendMessage(plugin.messages().message("notification.auto-buy-invalid-price"));
                return;
            }
            player.sendMessage(plugin.messages().message("notification.auto-buy-price-set", Map.of(
                    "price", plugin.formatPrice(input.donate, price, null))));
            plugin.playSound(player, "action.favorite-added");
            return;
        }
        FavoriteService.AddResult result = plugin.favorites().addPrice(player.getUniqueId(), input.donate,
                input.itemKey, price);
        FavoriteFilter filter = plugin.favorites().priceFilter(player.getUniqueId(), input.donate, input.itemKey);
        if (filter == null || result == FavoriteService.AddResult.INVALID) {
            player.sendMessage(plugin.messages().message("notification.auto-buy-invalid-price"));
            return;
        }
        if (!filter.autoBuy()) plugin.favorites().toggleAutoBuy(player.getUniqueId(), input.donate, filter.id());
        player.sendMessage(plugin.messages().message("notification.auto-buy-price-set", Map.of(
                "price", plugin.formatPrice(input.donate, price, null))));
        plugin.playSound(player, "action.favorite-added");
    }

    private record PendingInput(String itemKey, String filterId, boolean donate) {
    }
}
