package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

final class ItemStackCodec {
    private ItemStackCodec() {
    }

    static String encode(ItemStack item) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    static ItemStack decode(String encoded) throws IOException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            Object value = input.readObject();
            if (value instanceof ItemStack item) return item;
            throw new IOException("delivery item has invalid type");
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException("cannot decode delivery item", exception);
        }
    }
}
