package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

final class BundleCreateView implements InventoryHolder {
    MarketGuiController controller;
    UUID viewer;
    Inventory inventory;
    Map<Integer, ItemStack> sourceSlots;
    String name;
    double totalPrice;
    int serializedSize;
    boolean processing;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
