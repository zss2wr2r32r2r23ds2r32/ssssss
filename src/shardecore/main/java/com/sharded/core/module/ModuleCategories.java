package com.sharded.core.module;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleCategories {
   private static final Map<String, String> MAP = new LinkedHashMap<>();

   private static void category(String cat, String... ids) {
      for (String s : ids) {
         MAP.put(s, cat);
      }
   }

   public static String categoryOf(String moduleId) {
      return MAP.getOrDefault(moduleId, "core");
   }

   private ModuleCategories() {
   }

   static {
      category(
         "staff",
         "staffchat",
         "requeststaff",
         "punishments",
         "chatmoderation",
         "client",
         "invrollback",
         "screenshare",
         "modulesadmin",
         "staffpromote",
         "itemedit"
      );
      category("settings", "deathmessages", "nightvision", "privatemessages", "chat", "joinmessages", "settings");
      category("tokens", "tokens", "eglow", "chatcolor", "namecolor", "wardrobe", "cosmeticschat");
      category(
         "perks",
         "trash",
         "fly",
         "pickupmobs",
         "pickupspawners",
         "autosmelt",
         "craft",
         "fix",
         "portalrtp",
         "abilities",
         "bundles",
         "armortrims",
         "toolname",
         "backpack",
         "tempranks"
      );
   }
}
