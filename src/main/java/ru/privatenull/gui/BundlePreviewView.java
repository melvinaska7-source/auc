package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.model.MarketListing;

final class BundlePreviewView implements InventoryHolder {
    MarketGuiController controller;
    MarketListing listing;
    Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
