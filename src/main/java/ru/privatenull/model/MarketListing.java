package ru.privatenull.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class MarketListing {
    private final String id;
    private final UUID sellerId;
    private final ItemStack item;
    private final double pricePerUnit;
    private final int amount;
    private final long createdAt;
    private final long expiresAt;
    private final String status;

    public MarketListing(String id, UUID sellerId, ItemStack item, double pricePerUnit,
                         int amount, long createdAt, String status) {
        this(id, sellerId, item, pricePerUnit, amount, createdAt, Long.MAX_VALUE, status);
    }

    public MarketListing(String id, UUID sellerId, ItemStack item, double pricePerUnit,
                         int amount, long createdAt, long expiresAt, String status) {
        this.id = id;
        this.sellerId = sellerId;
        this.item = item;
        this.pricePerUnit = pricePerUnit;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String id() { return id; }
    public UUID sellerId() { return sellerId; }
    public ItemStack item() { return item; }
    public double pricePerUnit() { return pricePerUnit; }
    public int amount() { return amount; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public String status() { return status; }

    public MarketListing withAmount(int newAmount) {
        return new MarketListing(id, sellerId, item, pricePerUnit, newAmount, createdAt, expiresAt, status);
    }

    public MarketListing withStatus(String newStatus) {
        return new MarketListing(id, sellerId, item, pricePerUnit, amount, createdAt, expiresAt, newStatus);
    }

    public MarketListing relisted(long newCreatedAt, long newExpiresAt) {
        return new MarketListing(id, sellerId, item, pricePerUnit, amount,
                newCreatedAt, newExpiresAt, "ACTIVE");
    }
}
