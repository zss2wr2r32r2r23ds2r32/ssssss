package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.abilities.AbilitiesShopModule;
import com.sharded.core.modules.backpack.BackpackModule;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.settings.SettingsModule;
import com.sharded.core.modules.tempranks.TempranksModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Prefix;
import com.sharded.core.util.Text;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class GuiManager {
   private final ShardedCore plugin;
   private final Map<String, GuiMenu> menus = new HashMap<>();
   private final Map<String, Consumer<Player>> customActions = new HashMap<>();
   private final Map<String, Function<Player, Map<String, String>>> menuExtras = new HashMap<>();
   private String noPermissionMessage = "%prefix%&cYou don't have permission.";

   public GuiManager(ShardedCore plugin) {
      this.plugin = plugin;
   }

   public void setNoPermissionMessage(String message) {
      this.noPermissionMessage = message;
   }

   public String noPermissionMessage() {
      return this.noPermissionMessage;
   }

   public void registerAction(String id, Consumer<Player> action) {
      this.customActions.put(id.toLowerCase(), action);
   }

   public void registerMenuExtras(String menuId, Function<Player, Map<String, String>> provider) {
      this.menuExtras.put(menuId.toLowerCase(), provider);
   }

   public void unregisterActions() {
      this.customActions.clear();
      this.menuExtras.clear();
   }

   public void loadMenu(File file, String id) {
      if (file.exists()) {
         this.menus.put(id, new GuiMenu(id, YamlConfiguration.loadConfiguration(file), this.plugin.guiNavigation()));
      }
   }

   public void loadFolder(File folder, String prefix) {
      if (!folder.exists()) {
         folder.mkdirs();
      }

      File[] afile = folder.listFiles((dir, name) -> name.endsWith(".yml"));
      if (afile != null) {
         for (File file1 : afile) {
            YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
            if (yamlconfiguration.contains("menu_title")) {
               String s = prefix.isEmpty() ? file1.getName().replace(".yml", "") : prefix + file1.getName().replace(".yml", "");
               this.menus.put(s, new GuiMenu(s, yamlconfiguration, this.plugin.guiNavigation()));
            }
         }
      }
   }

   public void clearMenus() {
      this.menus.clear();
   }

   public GuiMenu menu(String menuId) {
      return this.resolveMenu(menuId);
   }

   private GuiMenu resolveMenu(String menuId) {
      if (menuId != null && !menuId.isBlank()) {
         GuiMenu guimenu = this.menus.get(menuId);
         return guimenu != null ? guimenu : this.menus.get(menuId.toLowerCase(Locale.ROOT));
      } else {
         return null;
      }
   }

   public void open(Player player, String menuId) {
      this.open(player, menuId, Map.of());
   }

   public void open(Player player, String menuId, Map<String, String> extra) {
      GuiMenu guimenu = this.resolveMenu(menuId);
      if (guimenu == null) {
         player.sendMessage(Text.c(Prefix.get() + "&cUnknown menu: &f" + menuId));
      } else {
         Map<String, String> map = new HashMap<>();
         Function<Player, Map<String, String>> function = this.menuExtras.get(menuId.toLowerCase());
         if (function != null) {
            Map<String, String> map1 = function.apply(player);
            if (map1 != null) {
               map.putAll(map1);
            }
         }

         if (extra != null) {
            map.putAll(extra);
         }

         guimenu.open(player, this, map);
      }
   }

   public void handleClick(Player player, String menuId, int slot) {
      GuiMenu guimenu = this.menus.get(menuId);
      if (guimenu != null) {
         GuiMenu.GuiItem guimenu$guiitem = guimenu.itemAt(slot);
         if (guimenu$guiitem != null) {
            if (guimenu$guiitem.permission() != null && !guimenu$guiitem.permission().isBlank()) {
               String s = guimenu$guiitem.permission().startsWith("sharded.") ? guimenu$guiitem.permission() : "sharded." + guimenu$guiitem.permission();
               if (!player.hasPermission(s)) {
                  this.message(player, this.noPermissionMessage(), false);
                  return;
               }
            }

            List<String> list = guimenu$guiitem.clickCommands();
            if (list.isEmpty()) {
               list = guimenu$guiitem.leftClickCommands();
            }

            this.runCommands(player, menuId, list, Map.of());
         }
      }
   }

   public void runCommands(Player player, String menuId, List<String> commands, Map<String, String> extra) {
      if (commands != null) {
         for (String s : commands) {
            if (!this.runCommand(player, menuId, s, extra)) {
               break;
            }
         }
      }
   }

   public void runCommands(Player player, List<String> commands, Map<String, String> extra) {
      this.runCommands(player, "", commands, extra);
   }

   private boolean runCommand(Player player, String menuId, String line, Map<String, String> extra) {
      if (line != null && !line.isBlank()) {
         line = GuiMenu.apply(line, player, extra, this).trim();
         if (!line.startsWith("[")) {
            return true;
         } else {
            int i = line.indexOf(93);
            if (i <= 1) {
               return true;
            } else {
               String s = line.substring(1, i).toLowerCase();
               String s1 = line.substring(i + 1).trim();
               boolean flag26;
               switch (s) {
                  case "message":
                     player.sendMessage(Text.c(s1));
                     boolean flag25 = true;
                     flag26 = flag25;
                     break;
                  case "actionbar":
                     player.sendActionBar(Text.c(s1));
                     boolean flag24 = true;
                     flag26 = flag24;
                     break;
                  case "close":
                     player.closeInventory();
                     boolean flag23 = true;
                     flag26 = flag23;
                     break;
                  case "player":
                     player.performCommand(s1);
                     boolean flag22 = true;
                     flag26 = flag22;
                     break;
                  case "console":
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), GuiMenu.apply(s1, player, extra, this));
                     boolean flag21 = true;
                     flag26 = flag21;
                     break;
                  case "sound":
                     try {
                        player.playSound(player.getLocation(), Sound.valueOf(s1.toUpperCase()), 1.0F, 1.0F);
                     } catch (IllegalArgumentException illegalargumentexception) {
                     }

                     boolean flag20 = true;
                     flag26 = flag20;
                     break;
                  case "openguimenu":
                  case "opendeluxemenu":
                     this.open(player, s1);
                     boolean flag19 = true;
                     flag26 = flag19;
                     break;
                  case "refresh":
                     String s3 = s1.isBlank() ? menuId : s1;
                     this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.open(player, s3));
                     boolean flag18 = true;
                     flag26 = flag18;
                     break;
                  case "tokens_take":
                     TokenService tokenservice = this.plugin.modules().tokens();
                     if (tokenservice == null) {
                        boolean flag15 = false;
                        flag26 = flag15;
                     } else {
                        long j = this.parseLong(s1, 0L);
                        if (j > 0L && tokenservice.take(player.getUniqueId(), j)) {
                           boolean flag17 = true;
                           flag26 = flag17;
                        } else {
                           this.message(player, this.tokenPrefix() + "&#FF2727You don't have enough tokens!", false);
                           boolean flag16 = false;
                           flag26 = flag16;
                        }
                     }
                     break;
                  case "deny_if_permission":
                     String s2 = s1.startsWith("sharded.") ? s1 : s1;
                     if (player.hasPermission(s2)) {
                        this.message(player, this.tokenPrefix() + "&#FF2727You already own this!", false);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                        boolean flag13 = false;
                        flag26 = flag13;
                     } else {
                        boolean flag14 = true;
                        flag26 = flag14;
                     }
                     break;
                  case "temprank_buy":
                     String[] astring2 = s1.split("\\s+");
                     if (astring2.length < 3) {
                        boolean flag10 = false;
                        flag26 = flag10;
                     } else {
                        TempranksModule tempranksmodule = this.plugin.modules().get(TempranksModule.class);
                        if (tempranksmodule == null) {
                           boolean flag11 = false;
                           flag26 = flag11;
                        } else {
                           boolean flag12 = tempranksmodule.tryPurchase(
                              player, astring2[0], (int)this.parseLong(astring2[1], 0L), this.parseLong(astring2[2], 0L)
                           );
                           flag26 = flag12;
                        }
                     }
                     break;
                  case "wardrobe_unlock":
                     WardrobeModule wardrobemodule = this.plugin.modules().get(WardrobeModule.class);
                     if (wardrobemodule == null) {
                        boolean flag8 = false;
                        flag26 = flag8;
                     } else {
                        boolean flag9 = wardrobemodule.unlock(player, s1.trim());
                        flag26 = flag9;
                     }
                     break;
                  case "backpack_buy":
                     String[] astring1 = s1.split("\\s+");
                     if (astring1.length < 2) {
                        boolean flag5 = false;
                        flag26 = flag5;
                     } else {
                        BackpackModule backpackmodule = this.plugin.modules().get(BackpackModule.class);
                        if (backpackmodule == null) {
                           boolean flag6 = false;
                           flag26 = flag6;
                        } else {
                           boolean flag7 = backpackmodule.tryPurchaseSlot(player, (int)this.parseLong(astring1[0], 0L), this.parseLong(astring1[1], 0L));
                           flag26 = flag7;
                        }
                     }
                     break;
                  case "ability_buy":
                     String[] astring = s1.split("\\s+", 3);
                     if (astring.length < 3) {
                        boolean flag2 = false;
                        flag26 = flag2;
                     } else {
                        AbilitiesShopModule abilitiesshopmodule = this.plugin.modules().get(AbilitiesShopModule.class);
                        if (abilitiesshopmodule == null) {
                           boolean flag3 = false;
                           flag26 = flag3;
                        } else {
                           boolean flag4 = abilitiesshopmodule.tryPurchase(
                              player, astring[0], (int)this.parseLong(astring[1], 0L), this.parseLong(astring[2], 0L)
                           );
                           flag26 = flag4;
                        }
                     }
                     break;
                  case "action":
                     Consumer<Player> consumer1 = this.customActions.get(s1.toLowerCase());
                     if (consumer1 != null) {
                        consumer1.accept(player);
                     }

                     boolean flag1 = true;
                     flag26 = flag1;
                     break;
                  default:
                     Consumer<Player> consumer = this.customActions.get(s);
                     if (consumer != null) {
                        consumer.accept(player);
                     }

                     boolean flag = true;
                     flag26 = flag;
               }

               return flag26;
            }
         }
      } else {
         return true;
      }
   }

   public void message(CommandSender sender, String message, boolean actionBar) {
      String s = this.applyPlaceholders(sender instanceof Player player ? player : null, message);
      MessageUtil.Delivery messageutil$delivery = actionBar ? MessageUtil.Delivery.ACTIONBAR : this.plugin.globalDelivery();
      MessageUtil.deliver(sender, s, messageutil$delivery);
   }

   public String tokenPrefix() {
      TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);
      return tokensmodule == null ? Prefix.get() : tokensmodule.tokenPrefix();
   }

   public String applyPlaceholders(Player player, String input) {
      if (input == null) {
         return "";
      } else {
         String s = input.replace("%prefix%", Prefix.get()).replace("%token_prefix%", this.tokenPrefix());
         if (player != null) {
            SettingsModule settingsmodule = this.plugin.modules().get(SettingsModule.class);
            if (settingsmodule != null && settingsmodule.isEnabled()) {
               for (Entry<String, String> entry : settingsmodule.placeholders(player).entrySet()) {
                  s = s.replace("%" + entry.getKey() + "%", (CharSequence)(entry.getValue() == null ? "" : entry.getValue()));
               }
            }

            TokenService tokenservice = this.plugin.modules().tokens();
            if (tokenservice != null) {
               long i = tokenservice.getBalance(player.getUniqueId());
               s = s.replace("%tokens%", String.valueOf(i))
                  .replace("%tokens_formatted%", Numbers.format(i))
                  .replace("%playerpoints_points%", String.valueOf(i));
            }

            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
               s = PlaceholderAPI.setPlaceholders(player, s);
            }
         }

         return this.applyLeaderboardPlaceholders(player, s);
      }
   }

   public String applyLeaderboardPlaceholders(Player player, String input) {
      if (input == null) {
         return "";
      } else {
         String s = input;
         boolean flag = input.contains("%tokens_top_");
         boolean flag1 = input.contains("%killstreak_top_");
         if (flag) {
            TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);
            List<TokenDatabase.LeaderEntry> list = tokensmodule != null && tokensmodule.database() != null ? tokensmodule.database().top(10) : List.of();

            for (int i = 1; i <= 10; i++) {
               s = s.replace("%tokens_top_" + i + "_name%", this.tokenTopName(list, i))
                  .replace("%tokens_top_" + i + "_amount%", this.tokenTopAmount(list, i))
                  .replace("%tokens_top_" + i + "_formatted%", this.tokenTopFormatted(list, i));
            }
         }

         if (flag1) {
            KillstreaksModule killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
            List<KillstreakDatabase.LeaderEntry> list1 = killstreaksmodule != null && killstreaksmodule.database() != null
               ? killstreaksmodule.database().topBest(10)
               : List.of();

            for (int j = 1; j <= 10; j++) {
               s = s.replace("%killstreak_top_" + j + "_name%", this.killstreakTopName(list1, j))
                  .replace("%killstreak_top_" + j + "_amount%", this.killstreakTopAmount(list1, j));
            }
         }

         if (player != null) {
            KillstreaksModule killstreaksmodule1 = this.plugin.modules().get(KillstreaksModule.class);
            if (killstreaksmodule1 != null && killstreaksmodule1.database() != null) {
               s = s.replace("%killstreak%", String.valueOf(killstreaksmodule1.database().getCurrent(player.getUniqueId())))
                  .replace("%killstreak_best%", String.valueOf(killstreaksmodule1.database().getBest(player.getUniqueId())));
            }
         }

         return s;
      }
   }

   private String tokenTopName(List<TokenDatabase.LeaderEntry> top, int rank) {
      return rank > top.size() ? "---" : OfflinePlayers.name(top.get(rank - 1).uuid());
   }

   private String tokenTopAmount(List<TokenDatabase.LeaderEntry> top, int rank) {
      return rank > top.size() ? "0" : String.valueOf(top.get(rank - 1).value());
   }

   private String tokenTopFormatted(List<TokenDatabase.LeaderEntry> top, int rank) {
      return Numbers.format(Long.parseLong(this.tokenTopAmount(top, rank)));
   }

   private String killstreakTopName(List<KillstreakDatabase.LeaderEntry> top, int rank) {
      return rank > top.size() ? "---" : OfflinePlayers.name(top.get(rank - 1).uuid());
   }

   private String killstreakTopAmount(List<KillstreakDatabase.LeaderEntry> top, int rank) {
      return rank > top.size() ? "0" : String.valueOf(top.get(rank - 1).value());
   }

   private long parseLong(String raw, long def) {
      try {
         return Long.parseLong(raw.trim());
      } catch (NumberFormatException numberformatexception) {
         return def;
      }
   }
}
