package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.market.MarketFilter.SortType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AuctionView implements InventoryHolder {
    MarketGuiController controller;
    UUID viewer;
    Inventory inventory;
    Map<Integer, String> slotToListingId = new HashMap<>();
    String category = "all";
    SortType sort = SortType.NEW_FIRST;
    String searchQuery;
    int page;
    boolean isSearch;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
