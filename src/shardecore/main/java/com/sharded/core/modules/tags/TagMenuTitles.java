package com.sharded.core.modules.tags;

public final class TagMenuTitles {
   private TagMenuTitles() {
   }

   public static String plain(String title) {
      if (title != null && !title.isBlank()) {
         String s = title.replaceAll("[§&][0-9A-FK-ORa-fk-orx]", "");
         return s.trim();
      } else {
         return "";
      }
   }

   public static boolean isEquipMenu(String title) {
      String s = plain(title);
      return s.equalsIgnoreCase("Name Tags") || s.equalsIgnoreCase("Limited Tags");
   }

   public static boolean isTokenShop(String title) {
      return "Tags".equalsIgnoreCase(plain(title));
   }
}
