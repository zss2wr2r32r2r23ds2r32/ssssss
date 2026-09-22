package com.sharded.core.modules.wardrobe;

import com.sharded.core.modules.tags.TagMenuTitles;
import java.util.Locale;

public final class WardrobeMenuTitles {
   private WardrobeMenuTitles() {
   }

   public static boolean isWardrobeMenu(String title) {
      String s = TagMenuTitles.plain(title);
      return s.isEmpty()
         ? false
         : s.equalsIgnoreCase("Wardrobe")
            || s.toLowerCase(Locale.ROOT).startsWith("wardrobe |")
            || s.equalsIgnoreCase("Token Shop | Cosmetics")
            || s.equalsIgnoreCase("Token Shop | Limited")
            || s.equalsIgnoreCase("Token Shop | Favourites");
   }
}
