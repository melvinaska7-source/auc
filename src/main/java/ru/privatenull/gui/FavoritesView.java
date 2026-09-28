package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class FavoritesView implements InventoryHolder {
    MarketGuiController controller;
    Inventory inventory;
    Map<Integer, String> slotToFavoriteId;
    int page;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
