package com.sharded.core.modules.pets;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public enum PetType {
    PARROT("parrot", EntityType.PARROT, 0.55, true, false, false, null),
    AXOLOTL("axolotl", EntityType.AXOLOTL, 0.45, false, false, false, null),
    BEE("bee", EntityType.BEE, 0.45, false, false, false, null),
    WARDEN("warden", EntityType.WARDEN, 0.22, false, true, false, null),
    ENDER_DRAGON("enderdragon", null, 0.4, false, false, true, Material.DRAGON_HEAD);

    private final String id;
    private final EntityType entityType;
    private final double scale;
    private final boolean shoulder;
    private final boolean groundSnap;
    private final boolean armorStand;
    private final Material helmet;

    PetType(String id, EntityType entityType, double scale, boolean shoulder,
            boolean groundSnap, boolean armorStand, Material helmet) {
        this.id = id;
        this.entityType = entityType;
        this.scale = scale;
        this.shoulder = shoulder;
        this.groundSnap = groundSnap;
        this.armorStand = armorStand;
        this.helmet = helmet;
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

    public boolean groundSnap() {
        return groundSnap;
    }

    public boolean armorStand() {
        return armorStand;
    }

    public Material helmet() {
        return helmet;
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
