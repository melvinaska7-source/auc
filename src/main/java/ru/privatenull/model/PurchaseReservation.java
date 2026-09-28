package ru.privatenull.model;

public record PurchaseReservation(MarketListing listing, int quantity, int remainingAmount) {
}
