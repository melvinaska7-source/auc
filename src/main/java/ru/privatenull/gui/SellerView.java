package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.market.MarketFilter.SortType;

import java.util.Map;
import java.util.UUID;

final class SellerView implements InventoryHolder {
    MarketGuiController controller;
    UUID sellerId;
    Inventory inventory;
    Map<Integer, String> slotToListingId;
    SortType sort = SortType.NEW_FIRST;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
