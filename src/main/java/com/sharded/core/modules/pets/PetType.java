package com.sharded.core.modules.pets;

import org.bukkit.entity.EntityType;

import java.util.Locale;

public enum PetType {
    PARROT("parrot", EntityType.PARROT, 0.35, true),
    WARDEN("warden", EntityType.WARDEN, 0.18, false),
    ENDER_DRAGON("enderdragon", EntityType.ENDER_DRAGON, 0.12, false);

    private final String id;
    private final EntityType entityType;
    private final double scale;
    private final boolean shoulder;

    PetType(String id, EntityType entityType, double scale, boolean shoulder) {
        this.id = id;
        this.entityType = entityType;
        this.scale = scale;
        this.shoulder = shoulder;
    }

    public String id() {
        return id;
    }

    public EntityType entityType() {
        return entityType;
    }

    public double scale() {
        return scale;
    }

    public boolean shoulder() {
        return shoulder;
    }

    public String permission() {
        return "sharded.pets." + id;
    }

    public static PetType fromId(String raw) {
        if (raw == null) return null;
        String id = raw.toLowerCase(Locale.ROOT);
        for (PetType type : values()) {
            if (type.id.equals(id) || type.name().equalsIgnoreCase(id)) return type;
        }
        if (id.equals("dragon")) return ENDER_DRAGON;
        return null;
    }
}
