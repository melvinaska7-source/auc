package ru.privatenull.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record NotificationEntry(String key, ItemStack icon, String name) {
    Material material() {
        return icon.getType();
    }
}
