package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.model.MarketListing;

import java.util.UUID;

final class PurchaseView implements InventoryHolder {
    MarketGuiController controller;
    MarketListing listing;
    int maxAmount;
    int quantity;
    UUID sellerId;
    Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
