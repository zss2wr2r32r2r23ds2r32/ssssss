package dev.sharded.core.grave;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class Grave {
    private final UUID id;
    private final UUID owner;
    private final String ownerName;
    private Location location;
    private ItemStack[] contents;
    private UUID standId;
    private UUID clickId;
    private long createdAt;
    private boolean removed;

    public Grave(UUID id, UUID owner, String ownerName, Location location, ItemStack[] contents,
                 UUID standId, UUID clickId, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.location = location;
        this.contents = contents;
        this.standId = standId;
        this.clickId = clickId;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public Location location() {
        return location;
    }

    public void location(Location location) {
        this.location = location;
    }

    public ItemStack[] contents() {
        return contents;
    }

    public void contents(ItemStack[] contents) {
        this.contents = contents;
    }

    public UUID standId() {
        return standId;
    }

    public void standId(UUID standId) {
        this.standId = standId;
    }

    public UUID clickId() {
        return clickId;
    }

    public void clickId(UUID clickId) {
        this.clickId = clickId;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean removed() {
        return removed;
    }

    public void removed(boolean removed) {
        this.removed = removed;
    }

    public boolean empty() {
        if (contents == null) {
            return true;
        }
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
