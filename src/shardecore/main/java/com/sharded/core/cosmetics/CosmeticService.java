package com.sharded.core.cosmetics;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.GradientUtil;
import com.sharded.core.util.RainbowUtil;
import com.sharded.core.util.TagDisplayUtil;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent;
import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.awt.Color;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class CosmeticService implements Listener {
   private static final Pattern HEX_CHUNK = Pattern.compile("&#([0-9a-fA-F]{6})");
   private final ShardedCore plugin;
   private CosmeticDatabase database;
   private BukkitTask tabRefreshTask;
   private final Map<UUID, String> lastApplied = new ConcurrentHashMap<>();
   private Object tabApi;
   private Object tabListFormatManager;
   private Method tabGetPlayer;
   private Method tabSetName;
   private boolean tabHookLogged;

   public CosmeticService(ShardedCore plugin) {
      this.plugin = plugin;
   }

   public void enable() {
      try {
         this.database = new CosmeticDatabase(this.plugin, new File(this.plugin.getDataFolder(), "cosmetics"));
      } catch (Exception exception) {
         this.plugin.getLogger().severe("Could not open cosmetics database — tag/name/chat display disabled: " + exception.getMessage());
         exception.printStackTrace();
         this.database = null;
      }

      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.hookTab();
      CosmeticsPlaceholders.register(this.plugin, this);
      this.tabRefreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            this.applyDisplay(player);
         }
      }, 40L, 40L);
   }

   public void disable() {
      if (this.tabRefreshTask != null) {
         this.tabRefreshTask.cancel();
         this.tabRefreshTask = null;
      }

      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
      this.lastApplied.clear();
      this.tabApi = null;
      this.tabListFormatManager = null;
      this.tabGetPlayer = null;
      this.tabSetName = null;
   }

   public CosmeticDatabase database() {
      return this.database;
   }

   public void setTag(Player player, String tagId, String tagDisplay) {
      if (this.database != null) {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.database.get(player.getUniqueId());
         this.database.save(player.getUniqueId(), cosmeticdatabase$playercosmetics.withTag(tagId, tagDisplay));
         this.applyDisplay(player);
         this.reapplyLater(player);
      }
   }

   public void clearTag(Player player) {
      if (this.database != null) {
         this.database.save(player.getUniqueId(), this.database.get(player.getUniqueId()).withoutTag());
         this.applyDisplay(player);
         this.reapplyLater(player);
      }
   }

   public void setNameColor(Player player, String colorSpec) {
      if (this.database != null) {
         this.database.save(player.getUniqueId(), this.database.get(player.getUniqueId()).withNameColor(normalizeColorSpec(colorSpec)));
         this.applyDisplay(player);
         this.reapplyLater(player);
      }
   }

   public void clearNameColor(Player player) {
      if (this.database != null) {
         this.database.save(player.getUniqueId(), this.database.get(player.getUniqueId()).withoutNameColor());
         this.applyDisplay(player);
         this.reapplyLater(player);
      }
   }

   public void setChatColor(Player player, String colorSpec) {
      if (this.database != null) {
         this.database.save(player.getUniqueId(), this.database.get(player.getUniqueId()).withChatColor(normalizeColorSpec(colorSpec)));
      }
   }

   public void clearChatColor(Player player) {
      if (this.database != null) {
         this.database.save(player.getUniqueId(), this.database.get(player.getUniqueId()).withoutChatColor());
      }
   }

   public String tagDisplay(UUID uuid) {
      if (this.database == null) {
         return "";
      } else {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.database.get(uuid);
         return cosmeticdatabase$playercosmetics.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(cosmeticdatabase$playercosmetics.tagDisplay());
      }
   }

   public String formattedName(Player player) {
      return this.database == null ? player.getName() : colorizeName(player.getName(), this.database.get(player.getUniqueId()).nameColor());
   }

   public String tabNameLegacy(Player player) {
      if (this.database != null && player != null) {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.database.get(player.getUniqueId());
         String s = cosmeticdatabase$playercosmetics.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(cosmeticdatabase$playercosmetics.tagDisplay());
         String s1 = colorizeName(player.getName(), cosmeticdatabase$playercosmetics.nameColor());
         return s.isBlank() ? s1 : s1 + " " + s;
      } else {
         return player == null ? "" : player.getName();
      }
   }

   public String chatColorPrefix(UUID uuid) {
      if (this.database == null) {
         return "";
      } else {
         String s = this.database.get(uuid).chatColor();
         if (s == null || s.isBlank()) {
            return "";
         } else {
            return GradientUtil.isGradient(s) ? "" : ColorUtil.normalize(s);
         }
      }
   }

   public void applyDisplay(Player player) {
      if (this.database != null && player != null && player.isOnline()) {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.database.get(player.getUniqueId());
         String s = cosmeticdatabase$playercosmetics.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(cosmeticdatabase$playercosmetics.tagDisplay());
         String spec = cosmeticdatabase$playercosmetics.nameColor();
         String key = player.getName() + "|" + String.valueOf(spec) + "|" + s;
         if (key.equals(this.lastApplied.get(player.getUniqueId()))) {
            return;
         }
         this.lastApplied.put(player.getUniqueId(), key);
         Component component = nameComponent(player.getName(), spec);
         Component component1 = s.isBlank() ? component : Text.c(s).append(Component.space()).append(component);
         Component component2 = s.isBlank() ? component : component.append(Component.space()).append(Text.c(s));
         player.playerListName(component2);
         player.displayName(component1);
         this.applyTabPluginName(player, this.tabNameLegacy(player));
         if (s.isBlank()) {
            player.customName(null);
            player.setCustomNameVisible(false);
         } else {
            player.customName(component2);
            player.setCustomNameVisible(true);
         }
      }
   }

   private void reapplyLater(Player player) {
      for (long i : new long[]{1L, 5L, 20L, 40L, 100L}) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               this.applyDisplay(player);
            }
         }, i);
      }
   }

   public static Component nameComponent(String name, String spec) {
      if (name == null || name.isEmpty()) {
         return Component.empty();
      } else if (spec != null && !spec.isBlank()) {
         String s = spec.trim();
         if (isRainbow(s)) {
            return parseHexColored(RainbowUtil.apply(name));
         } else {
            if (GradientUtil.isGradient(s)) {
               String[] astring = GradientUtil.splitGradient(s);
               if (astring != null) {
                  return gradientName(name, astring);
               }
            }

            String s1 = ColorUtil.normalize(s);
            if (isRainbow(s1)) {
               return parseHexColored(RainbowUtil.apply(name));
            } else {
               if (GradientUtil.isGradient(s1)) {
                  String[] astring1 = GradientUtil.splitGradient(s1);
                  if (astring1 != null) {
                     return gradientName(name, astring1);
                  }
               }

               if (s1.startsWith("&#") && s1.length() >= 8) {
                  TextColor textcolor = TextColor.fromHexString("#" + s1.substring(2, 8));
                  if (textcolor != null) {
                     return Component.text(name, textcolor).decoration(TextDecoration.ITALIC, false);
                  }
               }

               if (s1.startsWith("#") && s1.length() >= 7) {
                  TextColor textcolor1 = TextColor.fromHexString(s1.substring(0, 7));
                  if (textcolor1 != null) {
                     return Component.text(name, textcolor1).decoration(TextDecoration.ITALIC, false);
                  }
               }

               return parseHexColored(s1 + name);
            }
         }
      } else {
         return Component.text(name).decoration(TextDecoration.ITALIC, false);
      }
   }

   private static Component gradientName(String name, String[] stops) {
      List<Color> list = new ArrayList<>();

      for (String s : stops) {
         Color color = parseAwt(s);
         if (color != null) {
            list.add(color);
         }
      }

      if (list.isEmpty()) {
         return Component.text(name).decoration(TextDecoration.ITALIC, false);
      } else if (list.size() == 1) {
         Color color2 = list.getFirst();
         return Component.text(name, TextColor.color(color2.getRed(), color2.getGreen(), color2.getBlue())).decoration(TextDecoration.ITALIC, false);
      } else {
         Component component = Component.empty();
         int i = name.length();

         for (int j = 0; j < i; j++) {
            double d0 = i == 1 ? 0.0 : (double)j / (double)(i - 1);
            Color color1 = mix(list, d0);
            component = component.append(Component.text(String.valueOf(name.charAt(j)), TextColor.color(color1.getRed(), color1.getGreen(), color1.getBlue())));
         }

         return component.decoration(TextDecoration.ITALIC, false);
      }
   }

   private static Component parseHexColored(String input) {
      if (input != null && !input.isEmpty()) {
         String s = input.replace('§', '&');
         Component component = Component.empty();
         TextColor textcolor = null;
         int i = 0;

         while (i < s.length()) {
            if (s.startsWith("&#", i) && i + 8 <= s.length()) {
               String s1 = s.substring(i + 2, i + 8);
               if (s1.matches("(?i)[0-9a-f]{6}")) {
                  textcolor = TextColor.fromHexString("#" + s1);
                  i += 8;
                  continue;
               }
            }

            if (s.charAt(i) == '&' && i + 1 < s.length()) {
               char c0 = Character.toLowerCase(s.charAt(i + 1));
               if ("0123456789abcdefklmnor".indexOf(c0) >= 0) {
                  i += 2;
                  continue;
               }
            }

            String s2 = String.valueOf(s.charAt(i));
            component = component.append(textcolor == null ? Component.text(s2) : Component.text(s2, textcolor));
            i++;
         }

         return component.decoration(TextDecoration.ITALIC, false);
      } else {
         return Component.empty();
      }
   }

   private static Color parseAwt(String raw) {
      if (raw != null && !raw.isBlank()) {
         Matcher matcher = Pattern.compile("(?i)#?([0-9a-f]{6})").matcher(raw.trim());
         if (!matcher.find()) {
            return null;
         } else {
            try {
               int i = Integer.parseInt(matcher.group(1), 16);
               return new Color(i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF);
            } catch (NumberFormatException numberformatexception) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static Color mix(List<Color> colors, double t) {
      double d0 = Math.max(0.0, Math.min(1.0, t)) * (double)(colors.size() - 1);
      int i = Math.min(colors.size() - 2, (int)Math.floor(d0));
      double d1 = d0 - (double)i;
      Color color = colors.get(i);
      Color color1 = colors.get(i + 1);
      int j = (int)Math.round((double)color.getRed() + d1 * (double)(color1.getRed() - color.getRed()));
      int k = (int)Math.round((double)color.getGreen() + d1 * (double)(color1.getGreen() - color.getGreen()));
      int l = (int)Math.round((double)color.getBlue() + d1 * (double)(color1.getBlue() - color.getBlue()));
      return new Color(clamp(j), clamp(k), clamp(l));
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }

   public static String colorizeName(String name, String spec) {
      if (spec != null && !spec.isBlank()) {
         String s = spec.trim();
         if (isRainbow(s)) {
            return RainbowUtil.apply(name);
         } else {
            if (GradientUtil.isGradient(s)) {
               String[] astring = GradientUtil.splitGradient(s);
               if (astring != null) {
                  return GradientUtil.apply(name, astring);
               }
            }

            String s1 = ColorUtil.normalize(s);
            if (isRainbow(s1)) {
               return RainbowUtil.apply(name);
            } else {
               if (GradientUtil.isGradient(s1)) {
                  String[] astring1 = GradientUtil.splitGradient(s1);
                  if (astring1 != null) {
                     return GradientUtil.apply(name, astring1);
                  }
               }

               if (!s1.startsWith("&#") && !s1.startsWith("&")) {
                  return s1.startsWith("#") && s1.length() == 7 ? "&#" + s1.substring(1) + name : s1 + name;
               } else {
                  return s1 + name;
               }
            }
         }
      } else {
         return name;
      }
   }

   public static String normalizeColorSpec(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (isRainbow(s)) {
            return "rainbow";
         } else if (GradientUtil.isGradient(s)) {
            String[] astring = GradientUtil.splitGradient(s);
            return astring == null ? s : String.join(" ", astring);
         } else {
            return s.startsWith("#") && s.length() == 7 ? "&#" + s.substring(1) : ColorUtil.normalize(s);
         }
      } else {
         return raw;
      }
   }

   public static boolean looksLikeCommand(String plain) {
      if (plain == null) {
         return false;
      } else {
         String s = plain.trim();
         return !s.isEmpty() && s.charAt(0) == '/';
      }
   }

   public static String colorizeChat(String plain, String spec) {
      if (spec == null || spec.isBlank() || plain == null || plain.isEmpty()) {
         return plain == null ? "" : plain;
      } else if (looksLikeCommand(plain)) {
         return plain;
      } else if (isRainbow(spec)) {
         return RainbowUtil.apply(plain);
      } else {
         if (GradientUtil.isGradient(spec)) {
            String[] astring = GradientUtil.splitGradient(spec);
            if (astring != null) {
               return GradientUtil.apply(plain, astring);
            }
         }

         return ColorUtil.normalize(spec) + plain;
      }
   }

   public static String toHashHexFormat(String legacy) {
      if (legacy != null && !legacy.isEmpty()) {
         Matcher matcher = HEX_CHUNK.matcher(legacy.replace('§', '&'));
         StringBuilder stringbuilder = new StringBuilder();

         while (matcher.find()) {
            matcher.appendReplacement(stringbuilder, Matcher.quoteReplacement("#" + matcher.group(1).toUpperCase(Locale.ROOT)));
         }

         matcher.appendTail(stringbuilder);
         return stringbuilder.toString();
      } else {
         return "";
      }
   }

   private void hookTab() {
      if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
         try {
            Class<?> oclass = Class.forName("me.neznamy.tab.api.TabAPI");
            this.tabApi = oclass.getMethod("getInstance").invoke(null);
            this.tabGetPlayer = oclass.getMethod("getPlayer", UUID.class);
            this.tabListFormatManager = oclass.getMethod("getTabListFormatManager").invoke(this.tabApi);
            if (this.tabListFormatManager != null) {
               for (Method method : this.tabListFormatManager.getClass().getMethods()) {
                  if ("setName".equals(method.getName()) && method.getParameterCount() == 2 && method.getParameterTypes()[1] == String.class) {
                     this.tabSetName = method;
                     break;
                  }
               }
            }

            if (this.tabSetName != null && !this.tabHookLogged) {
               this.plugin.getLogger().info("[cosmetics] Hooked TAB tab-list names for name gradients.");
               this.tabHookLogged = true;
            }
         } catch (Throwable throwable) {
            this.tabApi = null;
            this.tabListFormatManager = null;
            this.tabGetPlayer = null;
            this.tabSetName = null;
         }
      }
   }

   private void applyTabPluginName(Player player, String legacyColored) {
      if (this.tabSetName == null || this.tabApi == null) {
         if (Bukkit.getPluginManager().getPlugin("TAB") != null && this.tabApi == null) {
            this.hookTab();
         }

         if (this.tabSetName == null) {
            return;
         }
      }

      try {
         Object object = this.tabGetPlayer.invoke(this.tabApi, player.getUniqueId());
         if (object == null) {
            return;
         }

         String s = toHashHexFormat(legacyColored);
         this.tabSetName.invoke(this.tabListFormatManager, object, s);
      } catch (Throwable throwable) {
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.reapplyLater(player);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.lastApplied.remove(player.getUniqueId());
      player.setCustomNameVisible(false);
      player.customName(null);
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      if (this.database != null) {
         String s = PlainTextComponentSerializer.plainText().serialize(event.message());
         if (!looksLikeCommand(s)) {
            String s1 = this.database.get(event.getPlayer().getUniqueId()).chatColor();
            if (s1 != null && !s1.isBlank()) {
               event.message(Text.c(colorizeChat(s, s1)));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDecorate(AsyncChatDecorateEvent event) {
      if (event instanceof AsyncChatCommandDecorateEvent) {
         event.result(event.originalMessage());
      } else if (this.database != null && event.player() != null) {
         String s = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
         if (looksLikeCommand(s)) {
            event.result(event.originalMessage());
         } else {
            String s1 = this.database.get(event.player().getUniqueId()).chatColor();
            if (s1 != null && !s1.isBlank()) {
               event.result(Text.c(colorizeChat(s, s1)));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCommandDecorate(AsyncChatCommandDecorateEvent event) {
      event.result(event.originalMessage());
   }

   private static boolean isRainbow(String spec) {
      return spec != null && spec.equalsIgnoreCase("rainbow");
   }
}
