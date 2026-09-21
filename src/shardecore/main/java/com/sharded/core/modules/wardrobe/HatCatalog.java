package com.sharded.core.modules.wardrobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HatCatalog {
   private static final Set<String> REMOVED = Set.of(
      "tophat",
      "hats:tophat",
      "crown",
      "hats:crown",
      "propeller_hat",
      "hats:propeller_hat",
      "clown_mask",
      "hats:clown_mask",
      "sstraw_hat",
      "hats:sstraw_hat",
      "hats:strawhat",
      "hats:straw_hat",
      "strawhat",
      "dimmadome",
      "hats:dimmadome",
      "pharaoh_hat",
      "pharaon_hat",
      "somehats:pharaoh_hat",
      "somehats:pharaon_hat"
   );

   private static final String[] COLORS = new String[]{
      "&#FF0053",
      "&#FFE300",
      "&#008DFF",
      "&#FF3EA5",
      "&#AD4EFF",
      "&#45FF17",
      "&#FFC42B",
      "&#26E07A",
      "&#5AA1D8",
      "&#FF5C8A",
      "&#FFBA00",
      "&#00D4FF",
      "&#D5A6FF",
      "&#AA00AA",
      "&#FF5500",
      "&#00FFAA",
      "&#FF6B9D",
      "&#7C4DFF"
   };

   private HatCatalog() {
   }

   public static List<HatCatalog.Seed> all() {
      List<HatCatalog.Seed> list = new ArrayList<>();
      list.add(new HatCatalog.Seed("adventure_hat", "somehats:adventure_hat", "&f&lAdventure Hat", 800));
      list.add(new HatCatalog.Seed("aquarium_hat", "somehats:aquarium_hat", "&f&lAquarium Hat", 800));
      list.add(new HatCatalog.Seed("axe_hat", "somehats:axe_hat", "&f&lAxe Hat", 800));
      list.add(new HatCatalog.Seed("axolotl_blue_hat", "somehats:axolotl_blue_hat", "&f&lAxolotl Blue Hat", 800));
      list.add(new HatCatalog.Seed("axolotl_cyan_hat", "somehats:axolotl_cyan_hat", "&f&lAxolotl Cyan Hat", 800));
      list.add(new HatCatalog.Seed("axolotl_gold_hat", "somehats:axolotl_gold_hat", "&f&lAxolotl Gold Hat", 800));
      list.add(new HatCatalog.Seed("axolotl_pink_hat", "somehats:axolotl_pink_hat", "&f&lAxolotl Pink Hat", 800));
      list.add(new HatCatalog.Seed("axolotl_tinted", "somehats:axolotl_tinted", "&f&lAxolotl Tinted", 800));
      list.add(new HatCatalog.Seed("axolotl_wild_hat", "somehats:axolotl_wild_hat", "&f&lAxolotl Wild Hat", 800));
      list.add(new HatCatalog.Seed("bear_hat", "somehats:bear_hat", "&f&lBear Hat", 800));
      list.add(new HatCatalog.Seed("bear_hat_tint", "somehats:bear_hat_tint", "&f&lBear Hat Tint", 800));
      list.add(new HatCatalog.Seed("bee_hat", "somehats:bee_hat", "&f&lBee Hat", 800));
      list.add(new HatCatalog.Seed("beret_red", "somehats:beret_red", "&f&lBeret Red", 800));
      list.add(new HatCatalog.Seed("beret_tint", "somehats:beret_tint", "&f&lBeret Tint", 800));
      list.add(new HatCatalog.Seed("butterfly_antenna", "somehats:butterfly_antenna", "&f&lButterfly Antenna", 800));
      list.add(new HatCatalog.Seed("cap", "somehats:cap", "&f&lCap", 800));
      list.add(new HatCatalog.Seed("cap_tint", "somehats:cap_tint", "&f&lCap Tint", 800));
      list.add(new HatCatalog.Seed("capitaine_hat", "somehats:capitaine_hat", "&f&lCapitaine Hat", 800));
      list.add(new HatCatalog.Seed("carrot_hat", "somehats:carrot_hat", "&f&lCarrot Hat", 800));
      list.add(new HatCatalog.Seed("cart_hat", "somehats:cart_hat", "&f&lCart Hat", 800));
      list.add(new HatCatalog.Seed("cat_headphone", "somehats:cat_headphone", "&f&lCat Headphone", 800));
      list.add(new HatCatalog.Seed("cat_headphone_tint", "somehats:cat_headphone_tint", "&f&lCat Headphone Tint", 800));
      list.add(new HatCatalog.Seed("cauldron_hat", "somehats:cauldron_hat", "&f&lCauldron Hat", 800));
      list.add(new HatCatalog.Seed("cauldron_hat_tinted", "somehats:cauldron_hat_tinted", "&f&lCauldron Hat Tinted", 800));
      list.add(new HatCatalog.Seed("chef_hat", "somehats:chef_hat", "&f&lChef Hat", 800));
      list.add(new HatCatalog.Seed("choclate_easter_egg", "somehats:choclate_easter_egg", "&f&lChoclate Easter Egg", 800));
      list.add(new HatCatalog.Seed("choclate_easter_egg_side_rubon", "somehats:choclate_easter_egg_side_rubon", "&f&lChoclate Easter Egg Side Rubon", 800));
      list.add(new HatCatalog.Seed("cone_hat", "somehats:cone_hat", "&f&lCone Hat", 800));
      list.add(new HatCatalog.Seed("somehats_crown", "somehats:crown", "&f&lCrown", 800));
      list.add(new HatCatalog.Seed("deer_horn", "somehats:deer_horn", "&f&lDeer Horn", 800));
      list.add(new HatCatalog.Seed("deer_horn_tinted", "somehats:deer_horn_tinted", "&f&lDeer Horn Tinted", 800));
      list.add(new HatCatalog.Seed("double_rubon", "somehats:double_rubon", "&f&lDouble Rubon", 800));
      list.add(new HatCatalog.Seed("duck_hat", "somehats:duck_hat", "&f&lDuck Hat", 800));
      list.add(new HatCatalog.Seed("easter_box", "somehats:easter_box", "&f&lEaster Box", 800));
      list.add(new HatCatalog.Seed("easter_box_tinted", "somehats:easter_box_tinted", "&f&lEaster Box Tinted", 800));
      list.add(new HatCatalog.Seed("eye_patch", "somehats:eye_patch", "&f&lEye Patch", 800));
      list.add(new HatCatalog.Seed("fancy_hat", "somehats:fancy_hat", "&f&lFancy Hat", 800));
      list.add(new HatCatalog.Seed("farmer_hat", "somehats:farmer_hat", "&f&lFarmer Hat", 800));
      list.add(new HatCatalog.Seed("farmer_hat_tint", "somehats:farmer_hat_tint", "&f&lFarmer Hat Tint", 800));
      list.add(new HatCatalog.Seed("feather_hat", "somehats:feather_hat", "&f&lFeather Hat", 800));
      list.add(new HatCatalog.Seed("flower", "somehats:flower", "&f&lFlower", 800));
      list.add(new HatCatalog.Seed("flower_hat", "somehats:flower_hat", "&f&lFlower Hat", 800));
      list.add(new HatCatalog.Seed("flower_head", "somehats:flower_head", "&f&lFlower Head", 800));
      list.add(new HatCatalog.Seed("frog_bob", "somehats:frog_bob", "&f&lFrog Bob", 800));
      list.add(new HatCatalog.Seed("ghost_hat", "somehats:ghost_hat", "&f&lGhost Hat", 800));
      list.add(new HatCatalog.Seed("ghost_hat_tinted", "somehats:ghost_hat_tinted", "&f&lGhost Hat Tinted", 800));
      list.add(new HatCatalog.Seed("gift_hat", "somehats:gift_hat", "&f&lGift Hat", 800));
      list.add(new HatCatalog.Seed("gift_hat_tint", "somehats:gift_hat_tint", "&f&lGift Hat Tint", 800));
      list.add(new HatCatalog.Seed("glasses", "somehats:glasses", "&f&lGlasses", 800));
      list.add(new HatCatalog.Seed("goat_horn", "somehats:goat_horn", "&f&lGoat Horn", 800));
      list.add(new HatCatalog.Seed("golden_easter_egg", "somehats:golden_easter_egg", "&f&lGolden Easter Egg", 800));
      list.add(new HatCatalog.Seed("golden_easter_egg_side_rubon", "somehats:golden_easter_egg_side_rubon", "&f&lGolden Easter Egg Side Rubon", 800));
      list.add(new HatCatalog.Seed("greek_crown", "somehats:greek_crown", "&f&lGreek Crown", 800));
      list.add(new HatCatalog.Seed("horns", "somehats:horns", "&f&lHorns", 800));
      list.add(new HatCatalog.Seed("lawyer_hat", "somehats:lawyer_hat", "&f&lLawyer Hat", 800));
      list.add(new HatCatalog.Seed("lawyer_hat_tint", "somehats:lawyer_hat_tint", "&f&lLawyer Hat Tint", 800));
      list.add(new HatCatalog.Seed("miner_hat", "somehats:miner_hat", "&f&lMiner Hat", 800));
      list.add(new HatCatalog.Seed("mushroom_hat", "somehats:mushroom_hat", "&f&lMushroom Hat", 800));
      list.add(new HatCatalog.Seed("mushroom_hat_tinted", "somehats:mushroom_hat_tinted", "&f&lMushroom Hat Tinted", 800));
      list.add(new HatCatalog.Seed("panda_hat", "somehats:panda_hat", "&f&lPanda Hat", 800));
      list.add(new HatCatalog.Seed("pirate_hat", "somehats:pirate_hat", "&f&lPirate Hat", 800));
      list.add(new HatCatalog.Seed("pirate_hat_tint", "somehats:pirate_hat_tint", "&f&lPirate Hat Tint", 800));
      list.add(new HatCatalog.Seed("poulpy_hat", "somehats:poulpy_hat", "&f&lPoulpy Hat", 800));
      list.add(new HatCatalog.Seed("pumpkin_hat", "somehats:pumpkin_hat", "&f&lPumpkin Hat", 800));
      list.add(new HatCatalog.Seed("pumpkin_hat2", "somehats:pumpkin_hat2", "&f&lPumpkin Hat2", 800));
      list.add(new HatCatalog.Seed("rabbit_hat_tint", "somehats:rabbit_hat_tint", "&f&lRabbit Hat Tint", 800));
      list.add(new HatCatalog.Seed("rabbit_headphone", "somehats:rabbit_headphone", "&f&lRabbit Headphone", 800));
      list.add(new HatCatalog.Seed("rabbit_headphone_tint", "somehats:rabbit_headphone_tint", "&f&lRabbit Headphone Tint", 800));
      list.add(new HatCatalog.Seed("rgb_headphone", "somehats:rgb_headphone", "&f&lRgb Headphone", 800));
      list.add(new HatCatalog.Seed("rgb_headphone_tint", "somehats:rgb_headphone_tint", "&f&lRgb Headphone Tint", 800));
      list.add(new HatCatalog.Seed("ruban_hat_red", "somehats:ruban_hat_red", "&f&lRuban Hat Red", 800));
      list.add(new HatCatalog.Seed("ruban_hat_rgb", "somehats:ruban_hat_rgb", "&f&lRuban Hat Rgb", 800));
      list.add(new HatCatalog.Seed("ruban_hat_tint", "somehats:ruban_hat_tint", "&f&lRuban Hat Tint", 800));
      list.add(new HatCatalog.Seed("rubon_2", "somehats:rubon_2", "&f&lRubon 2", 800));
      list.add(new HatCatalog.Seed("sailor_hat", "somehats:sailor_hat", "&f&lSailor Hat", 800));
      list.add(new HatCatalog.Seed("santa_elf_hat", "somehats:santa_elf_hat", "&f&lSanta Elf Hat", 800));
      list.add(new HatCatalog.Seed("santa_elf_hat_tint", "somehats:santa_elf_hat_tint", "&f&lSanta Elf Hat Tint", 800));
      list.add(new HatCatalog.Seed("santa_hat", "somehats:santa_hat", "&f&lSanta Hat", 800));
      list.add(new HatCatalog.Seed("santa_hat_tint", "somehats:santa_hat_tint", "&f&lSanta Hat Tint", 800));
      list.add(new HatCatalog.Seed("snail_eyes", "somehats:snail_eyes", "&f&lSnail Eyes", 800));
      list.add(new HatCatalog.Seed("sombreros", "somehats:sombreros", "&f&lSombreros", 800));
      list.add(new HatCatalog.Seed("sombreros_tint", "somehats:sombreros_tint", "&f&lSombreros Tint", 800));
      list.add(new HatCatalog.Seed("steam_glasses", "somehats:steam_glasses", "&f&lSteam Glasses", 800));
      list.add(new HatCatalog.Seed("sunglasses", "somehats:sunglasses", "&f&lSunglasses", 800));
      list.add(new HatCatalog.Seed("tinted_easter_egg_gold_rubon", "somehats:tinted_easter_egg_gold_rubon", "&f&lTinted Easter Egg Gold Rubon", 800));
      list.add(
         new HatCatalog.Seed("tinted_easter_egg_gold_side_rubon", "somehats:tinted_easter_egg_gold_side_rubon", "&f&lTinted Easter Egg Gold Side Rubon", 800)
      );
      list.add(new HatCatalog.Seed("tinted_easter_egg_red_rubon", "somehats:tinted_easter_egg_red_rubon", "&f&lTinted Easter Egg Red Rubon", 800));
      list.add(
         new HatCatalog.Seed("tinted_easter_egg_red_side_rubon", "somehats:tinted_easter_egg_red_side_rubon", "&f&lTinted Easter Egg Red Side Rubon", 800)
      );
      list.add(new HatCatalog.Seed("top_hat", "somehats:top_hat", "&x&F&F&D&5&4&A&lTOP HAT", 800));
      list.add(new HatCatalog.Seed("top_hat_tint", "somehats:top_hat_tint", "&f&lTop Hat Tint", 800));
      list.add(new HatCatalog.Seed("tv_hat", "somehats:tv_hat", "&f&lTv Hat", 800));
      list.add(new HatCatalog.Seed("witch_hat", "somehats:witch_hat", "&f&lWitch Hat", 800));
      list.add(new HatCatalog.Seed("witch_hat_tinted", "somehats:witch_hat_tinted", "&f&lWitch Hat Tinted", 800));
      list.add(new HatCatalog.Seed("wool_cap_hat", "somehats:wool_cap_hat", "&f&lWool Cap Hat", 800));
      list.add(new HatCatalog.Seed("wool_cap_hat_tint", "somehats:wool_cap_hat_tint", "&f&lWool Cap Hat Tint", 800));
      return list.stream().filter(seed -> !isRemoved(seed.id(), seed.itemsadderId())).toList();
   }

   public static boolean isRemoved(String... ids) {
      if (ids == null) {
         return false;
      }
      for (String raw : ids) {
         if (raw == null || raw.isBlank()) {
            continue;
         }
         if (REMOVED.contains(raw.toLowerCase(Locale.ROOT).trim())) {
            return true;
         }
      }
      return false;
   }

   public static String colorFor(String id) {
      if (id == null || id.isBlank()) {
         return COLORS[0];
      }
      return switch (id.toLowerCase()) {
         case "tophat" -> "&#FF0053";
         case "top_hat" -> "&#FFD54A";
         case "propeller_hat" -> "&#008DFF";
         case "clown_mask" -> "&#FF3A3A";
         case "sstraw_hat", "straw_hat" -> "&#B3FF00";
         case "dimmadome" -> "&#FFC42B";
         default -> COLORS[Math.floorMod(id.hashCode(), COLORS.length)];
      };
   }

   public static record Seed(String id, String itemsadderId, String displayName, int price) {
   }
}
