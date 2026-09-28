package ru.privatenull.model;

import org.bukkit.inventory.ItemStack;

public record DeliveryEntry(String id, ItemStack item, long createdAt) {
}
