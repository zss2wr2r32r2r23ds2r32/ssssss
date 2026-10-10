package com.shardedcore.eventcore.event;

import org.bukkit.Material;

import java.util.Locale;

/** The two gamemodes the event core can run. */
public enum EventMode {

    CRYSTAL("crystal", Material.END_CRYSTAL),
    DIASMP("diasmp", Material.DIAMOND_SWORD);

    private final String id;
    private final Material defaultIcon;

    EventMode(String id, Material defaultIcon) {
        this.id = id;
        this.defaultIcon = defaultIcon;
    }

    public String id() {
        return id;
    }

    public Material defaultIcon() {
        return defaultIcon;
    }

    public static EventMode fromId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalised) {
            case "crystal", "crystals", "crystalpvp" -> CRYSTAL;
            case "diasmp", "diamondsmp", "diamond", "dia", "smp" -> DIASMP;
            default -> null;
        };
    }
}
