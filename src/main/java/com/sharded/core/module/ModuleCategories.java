package com.sharded.core.module;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps module ids to config folder categories for /module GUI and disk layout. */
public final class ModuleCategories {

    private static final Map<String, String> MAP = new LinkedHashMap<>();

    static {
        category("staff", "staffchat", "requeststaff", "punishments",
                "chatmoderation", "client", "invrollback", "screenshare", "modulesadmin");
        category("settings", "deathmessages", "nightvision", "privatemessages", "chat", "joinmessages", "settings");
        category("tokens", "tokens", "eglow", "chatcolor", "namecolor", "wardrobe");
        category("perks", "trash", "fly", "pickupmobs", "pickupspawners", "autosmelt", "craft", "fix",
                "portalrtp", "abilities", "bundles", "armortrims", "toolname", "backpack", "tempranks");
    }

    private static void category(String cat, String... ids) {
        for (String id : ids) MAP.put(id, cat);
    }

    public static String categoryOf(String moduleId) {
        return MAP.getOrDefault(moduleId, "core");
    }

    private ModuleCategories() {
    }
}
