package com.sharded.core.modules.pets;

import org.bukkit.Material;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public enum PetType {
    PARROT("parrot", EntityType.PARROT, 0.55, false, false, false, true, null, null),
    AXOLOTL("axolotl", EntityType.AXOLOTL, 0.45, false, false, false, false, null, null),
    BEE("bee", EntityType.BEE, 0.45, false, false, false, false, null, null),
    WARDEN("warden", EntityType.WARDEN, 0.35, false, false, true, false, null,
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDdhMzY5ZmM1Y2FkYjM2Y2Y4NzE5ZjNjYzMzNGE0NzNjNDg5YjNmNDEyNDY3YzJkMDU5ZWE1MDMzODQ2In19fQ=="),
    ALLAY("allay", EntityType.ALLAY, 0.65, false, false, false, true, null, null);

    private final String id;
    private final EntityType entityType;
    private final double scale;
    private final boolean groundSnap;
    private final boolean armorStand;
    private final boolean flyOrbit;
    private final Material helmet;
    private final String headTexture;

    PetType(String id, EntityType entityType, double scale, boolean shoulder, boolean groundSnap,
            boolean armorStand, boolean flyOrbit, Material helmet, String headTexture) {
        this.id = id;
        this.entityType = entityType;
        this.scale = scale;
        this.groundSnap = groundSnap;
        this.armorStand = armorStand;
        this.flyOrbit = flyOrbit;
        this.helmet = helmet;
        this.headTexture = headTexture;
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

    public boolean groundSnap() {
        return groundSnap;
    }

    public boolean armorStand() {
        return armorStand;
    }

    public boolean flyOrbit() {
        return flyOrbit;
    }

    public Material helmet() {
        return helmet;
    }

    public String headTexture() {
        return headTexture;
    }

    public boolean supportsVariant() {
        return this == AXOLOTL;
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
        if (id.equals("dragon") || id.equals("enderdragon")) return ALLAY;
        return null;
    }

    public static Axolotl.Variant parseAxolotlVariant(String raw) {
        if (raw == null || raw.isBlank()) return Axolotl.Variant.LUCY;
        try {
            return Axolotl.Variant.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "pink" -> Axolotl.Variant.LUCY;
                case "brown" -> Axolotl.Variant.WILD;
                case "gold" -> Axolotl.Variant.GOLD;
                case "cyan", "teal" -> Axolotl.Variant.CYAN;
                case "blue" -> Axolotl.Variant.BLUE;
                default -> Axolotl.Variant.LUCY;
            };
        }
    }

    public static java.util.List<String> axolotlColorNames() {
        return java.util.List.of("lucy", "wild", "gold", "cyan", "blue", "pink", "brown", "teal");
    }

    public static boolean isValidAxolotlColor(String raw) {
        if (raw == null || raw.isBlank()) return true;
        String id = raw.toLowerCase(Locale.ROOT);
        return axolotlColorNames().contains(id);
    }
}
