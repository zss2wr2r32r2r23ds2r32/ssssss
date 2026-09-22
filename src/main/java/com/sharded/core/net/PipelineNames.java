package com.sharded.core.net;

import java.util.List;
import java.util.Locale;

/**
 * Minecraft / Paper netty pipeline names for the play encoder.
 */
public final class PipelineNames {
    public static final String HANDLER = "shardedcore_add_entity_fix";

    private PipelineNames() {
    }

    public static String encoderName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        if (names.contains("encoder")) {
            return "encoder";
        }
        if (names.contains("outbound_config")) {
            return "outbound_config";
        }
        for (String name : names) {
            if (name != null && name.toLowerCase(Locale.ROOT).contains("encoder")) {
                return name;
            }
        }
        return null;
    }
}
