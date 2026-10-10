package com.shardedcore.eventcore.event;

import java.util.Locale;

/**
 * Every toggle that lives inside a mode settings menu.
 *
 * <p>The {@code id} is the key used in {@code settings.yml} and in the saved
 * state file, so renaming an enum constant never breaks an existing install.</p>
 */
public enum Setting {

    PVP("pvp"),
    LOCATOR_BAR("locator-bar"),
    SPAWN_PROTECTION("spawn-protection"),
    KITS("kits"),
    WORLD_BORDER("world-border"),
    REVIVE("revive"),
    BEDROCK_DROP("bedrock-drop"),
    SUPPLY_DROPS("supply-drops"),
    CLEAR_BLOCKS("clear-blocks"),
    COUNTDOWN("countdown");

    private final String id;

    Setting(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Setting fromId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        for (Setting setting : values()) {
            if (setting.id.equals(normalised)) {
                return setting;
            }
        }
        return null;
    }
}
