package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticDatabase;
import com.sharded.core.modules.crates.CratesModule;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.modules.teams.TeamDatabase;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.util.EventPlaceholders;
import com.sharded.core.util.EventTabScoreboard;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TagDisplayUtil;
import com.sharded.core.util.TimeFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderHook implements Listener {
   private final ShardedCore plugin;
   private final List<PlaceholderExpansion> expansions = new ArrayList<>();
   private volatile TokensModule cachedTokens;
   private volatile TeamsModule cachedTeams;
   private volatile KillstreaksModule cachedKillstreaks;
   private volatile OutpostModule cachedOutpost;
   private volatile KothModule cachedKoth;
   private volatile LeaderboardsModule cachedLeaderboards;
   private volatile CratesModule cachedCrates;

   public PlaceholderHook(ShardedCore plugin) {
      this.plugin = plugin;
   }

   public void tryRegister() {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
         this.plugin.getLogger().info("PlaceholderAPI not found — placeholders disabled.");
      } else {
         for (PlaceholderExpansion placeholderexpansion : this.expansions) {
            try {
               placeholderexpansion.unregister();
            } catch (Exception exception) {
            }
         }

         this.expansions.clear();
         this.expansions.add(new PlaceholderHook.ShardedCoreExpansion());
         this.expansions.add(new PlaceholderHook.TokenExpansion());
         this.expansions.add(new PlaceholderHook.XKillstreakExpansion());
         this.expansions.add(new PlaceholderHook.PlayerPointsExpansion());
         this.expansions.add(new PlaceholderHook.KothExpansion());
         this.expansions.add(new PlaceholderHook.OutpostExpansion());
         this.claimBarePlaceholder("time");
         this.expansions.add(new PlaceholderHook.BareEventExpansion("x"));
         this.expansions.add(new PlaceholderHook.BareEventExpansion("y"));
         this.expansions.add(new PlaceholderHook.BareEventExpansion("z"));
         this.expansions.add(new PlaceholderHook.BareEventExpansion("time"));
         this.expansions.add(new PlaceholderHook.BareEventExpansion("points"));
         this.expansions.add(new PlaceholderHook.BareEventExpansion("next"));

         for (PlaceholderExpansion placeholderexpansion1 : this.expansions) {
            if (placeholderexpansion1.register()) {
               this.plugin.getLogger().info("Registered PlaceholderAPI expansion: %" + placeholderexpansion1.getIdentifier() + "_%");
            }
         }

         this.refreshModuleCache();
      }
   }

   private void claimBarePlaceholder(String identifier) {
      try {
         me.clip.placeholderapi.expansion.manager.LocalExpansionManager manager = me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance()
            .getLocalExpansionManager();
         PlaceholderExpansion existing = manager.getExpansion(identifier);
         if (existing != null && !(existing instanceof PlaceholderHook.BareEventExpansion)) {
            manager.unregister(existing);
         }
      } catch (Exception exception) {
         this.plugin.getLogger().warning("Could not replace PlaceholderAPI %" + identifier + "%: " + exception.getMessage());
      }
   }

   private void refreshModuleCache() {
      this.cachedTokens = this.plugin.modules().get(TokensModule.class);
      this.cachedTeams = this.plugin.modules().get(TeamsModule.class);
      this.cachedKillstreaks = this.plugin.modules().get(KillstreaksModule.class);
      this.cachedOutpost = this.plugin.modules().get(OutpostModule.class);
      this.cachedKoth = this.plugin.modules().get(KothModule.class);
      this.cachedLeaderboards = this.plugin.modules().get(LeaderboardsModule.class);
      this.cachedCrates = this.plugin.modules().get(CratesModule.class);
   }

   private CratesModule cratesModule() {
      CratesModule crates = this.cachedCrates;
      return crates != null ? crates : this.plugin.modules().get(CratesModule.class);
   }

   private String crateKeyPlaceholder(OfflinePlayer player, String spec) {
      CratesModule crates = this.cratesModule();
      if (crates == null || player == null || player.getUniqueId() == null) {
         return null;
      }
      if (spec.equals("keys") || spec.equals("keys_total") || spec.equals("crate_keys") || spec.equals("crate_keys_total")) {
         return String.valueOf(crates.totalKeys(player.getUniqueId()));
      }
      String crateId;
      if (spec.startsWith("crate_keys_")) {
         crateId = spec.substring("crate_keys_".length());
      } else if (spec.startsWith("crate_key_")) {
         crateId = spec.substring("crate_key_".length());
      } else if (spec.startsWith("keys_")) {
         crateId = spec.substring("keys_".length());
      } else if (spec.startsWith("key_")) {
         crateId = spec.substring("key_".length());
      } else {
         return null;
      }
      if (crateId.isBlank()) {
         return "0";
      }
      if (crateId.matches("\\d+_(name|value|uuid|head|head_uuid)")
            || crateId.matches("(name|value|uuid|head|head_uuid)_\\d+")) {
         return null;
      }
      crateId = switch (crateId) {
         case "kill", "kill_key", "killcrate", "kill_crate" -> "killkey";
         case "shard_key", "shardcrate", "shard_crate", "cosmetic", "cosmetic_key" -> "shard";
         case "astral_key", "astralcrate", "astral_crate" -> "astral";
         default -> crateId;
      };
      return String.valueOf(crates.getKeys(player.getUniqueId(), crateId));
   }

   private TokensModule tokensModule() {
      TokensModule tokensmodule = this.cachedTokens;
      return tokensmodule != null ? tokensmodule : this.plugin.modules().get(TokensModule.class);
   }

   private TeamsModule teamsModule() {
      TeamsModule teamsmodule = this.cachedTeams;
      return teamsmodule != null ? teamsmodule : this.plugin.modules().get(TeamsModule.class);
   }

   private KillstreakDatabase killstreakDb() {
      KillstreaksModule killstreaksmodule = this.cachedKillstreaks;
      if (killstreaksmodule == null) {
         killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
      }

      return killstreaksmodule == null ? null : killstreaksmodule.database();
   }

   @EventHandler
   public void onPluginEnable(PluginEnableEvent event) {
      if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
         this.tryRegister();
      }
   }

   private static final Pattern LB_POS_FIELD = Pattern.compile(
         "^(?:lb_|topper_)?([a-z]+)_(?:top_)?(\\d+)_(name|value|uuid|head|head_uuid)$");
   private static final Pattern LB_FIELD_POS = Pattern.compile(
         "^(?:lb_|topper_)?([a-z]+)_(name|value|uuid|head|head_uuid)_(\\d+)$");

   private String leaderboardPlaceholder(String spec) {
      Matcher matcher = LB_POS_FIELD.matcher(spec);
      String board;
      String field;
      int rank;
      if (matcher.matches()) {
         board = matcher.group(1);
         rank = Integer.parseInt(matcher.group(2));
         field = matcher.group(3);
      } else {
         matcher = LB_FIELD_POS.matcher(spec);
         if (!matcher.matches()) {
            return null;
         }
         board = matcher.group(1);
         field = matcher.group(2);
         rank = Integer.parseInt(matcher.group(3));
      }
      LeaderboardsModule module = this.cachedLeaderboards != null
            ? this.cachedLeaderboards
            : this.plugin.modules().get(LeaderboardsModule.class);
      return module == null ? "" : module.placeholder(board, rank, field);
   }

   private String leaderboardValue(String spec, boolean tokens) {
      String[] astring = spec.split("_", 2);
      if (astring.length == 0) {
         return "";
      } else {
         int i;
         try {
            i = Integer.parseInt(astring[0]);
         } catch (NumberFormatException numberformatexception) {
            return "";
         }

         if (i >= 1 && i <= 10) {
            String s = astring.length > 1 ? astring[1] : "name";
            if (s.equals("line") || s.equals("list")) {
               return this.topLine(i, tokens);
            } else if (tokens) {
               TokensModule tokensmodule = this.tokensModule();
               if (tokensmodule != null && tokensmodule.database() != null) {
                  List<TokenDatabase.LeaderEntry> list1 = tokensmodule.database().top(10);
                  if (i <= list1.size()) {
                     TokenDatabase.LeaderEntry tokendatabase$leaderentry = list1.get(i - 1);

                     return switch (s) {
                        case "amount", "value" -> String.valueOf(tokendatabase$leaderentry.value());
                        case "formatted" -> Numbers.format(tokendatabase$leaderentry.value());
                        default -> OfflinePlayers.name(tokendatabase$leaderentry.uuid());
                     };
                  } else {
                     return !s.equals("amount") && !s.equals("value") ? "---" : "0";
                  }
               } else {
                  return "---";
               }
            } else {
               KillstreakDatabase killstreakdatabase = this.killstreakDb();
               if (killstreakdatabase == null) {
                  return "---";
               } else {
                  List<KillstreakDatabase.LeaderEntry> list = killstreakdatabase.topBest(10);
                  if (i <= list.size()) {
                     KillstreakDatabase.LeaderEntry killstreakdatabase$leaderentry = list.get(i - 1);

                     return switch (s) {
                        case "amount", "value" -> String.valueOf(killstreakdatabase$leaderentry.value());
                        default -> OfflinePlayers.name(killstreakdatabase$leaderentry.uuid());
                     };
                  } else {
                     return !s.equals("amount") && !s.equals("value") ? "---" : "0";
                  }
               }
            }
         } else {
            return "";
         }
      }
   }

   private String outpostTime() {
      return TimeFormat.hms(this.outpostDisplayMillis());
   }

   private long outpostDisplayMillis() {
      OutpostModule outpostmodule = this.cachedOutpost != null ? this.cachedOutpost : this.plugin.modules().get(OutpostModule.class);
      return outpostmodule == null ? 0L : outpostmodule.displayTimeMs();
   }

   private String blockCoord(Location location, int axis) {
      if (location == null || location.getWorld() == null) {
         return "0";
      }
      int value = switch (axis) {
         case 0 -> location.getBlockX();
         case 1 -> location.getBlockY();
         default -> location.getBlockZ();
      };
      return String.valueOf(value);
   }

   private String kothTime() {
      return TimeFormat.hms(this.kothMillis());
   }

   private long outpostMillis() {
      OutpostModule outpostmodule = this.cachedOutpost != null ? this.cachedOutpost : this.plugin.modules().get(OutpostModule.class);
      return outpostmodule == null ? 0L : outpostmodule.millisUntilStart();
   }

   private long kothMillis() {
      KothModule kothmodule = this.cachedKoth != null ? this.cachedKoth : this.plugin.modules().get(KothModule.class);
      return kothmodule == null ? 0L : kothmodule.millisUntilStart();
   }

   private String topLine(int rank, boolean tokens) {
      LeaderboardsModule leaderboardsmodule = this.cachedLeaderboards != null ? this.cachedLeaderboards : this.plugin.modules().get(LeaderboardsModule.class);
      String s = leaderboardsmodule == null
         ? "&a#%rank% &f%name% &7— &f%value% %label%"
         : leaderboardsmodule.hologramLineTemplate(tokens ? "token" : "killstreak");
      String s1 = tokens ? "Tokens" : "Streak";
      if (tokens) {
         TokensModule tokensmodule = this.tokensModule();
         if (tokensmodule != null && tokensmodule.database() != null) {
            List<TokenDatabase.LeaderEntry> list1 = tokensmodule.database().top(10);
            if (rank > list1.size()) {
               return "---";
            } else {
               TokenDatabase.LeaderEntry tokendatabase$leaderentry = list1.get(rank - 1);
               return ColorUtil.normalize(
                  s.replace("%rank%", String.valueOf(rank))
                     .replace("%name%", OfflinePlayers.name(tokendatabase$leaderentry.uuid()))
                     .replace("%value%", Numbers.format(tokendatabase$leaderentry.value()))
                     .replace("%label%", s1)
               );
            }
         } else {
            return "---";
         }
      } else {
         KillstreakDatabase killstreakdatabase = this.killstreakDb();
         if (killstreakdatabase == null) {
            return "---";
         } else {
            List<KillstreakDatabase.LeaderEntry> list = killstreakdatabase.topBest(10);
            if (rank > list.size()) {
               return "---";
            } else {
               KillstreakDatabase.LeaderEntry killstreakdatabase$leaderentry = list.get(rank - 1);
               return ColorUtil.normalize(
                  s.replace("%rank%", String.valueOf(rank))
                     .replace("%name%", OfflinePlayers.name(killstreakdatabase$leaderentry.uuid()))
                     .replace("%value%", String.valueOf(killstreakdatabase$leaderentry.value()))
                     .replace("%label%", s1)
               );
            }
         }
      }
   }

   private final class BareEventExpansion extends PlaceholderExpansion {
      private final String identifier;

      private BareEventExpansion(String identifier) {
         this.identifier = identifier;
      }

      @NotNull
      public String getIdentifier() {
         return this.identifier;
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
         return switch (this.identifier) {
            case "x" -> "outpost".equals(key) ? EventPlaceholders.outpostCoord(0) : EventPlaceholders.kothCoord(0);
            case "y" -> "outpost".equals(key) ? EventPlaceholders.outpostCoord(1) : EventPlaceholders.kothCoord(1);
            case "z" -> "outpost".equals(key) ? EventPlaceholders.outpostCoord(2) : EventPlaceholders.kothCoord(2);
            case "time" -> "outpost".equals(key) ? EventPlaceholders.outpostTime() : EventPlaceholders.kothTime();
            case "points" -> "outpost".equals(key) ? EventPlaceholders.outpostPoints() : EventPlaceholders.kothPoints();
            case "next" -> key.startsWith("outpost") ? EventPlaceholders.outpostNext() : EventPlaceholders.kothNext();
            default -> null;
         };
      }
   }

   private final class KothExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "koth";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         KothModule kothmodule = PlaceholderHook.this.plugin.modules().get(KothModule.class);
         if (kothmodule == null) {
            return "";
         } else {
            String s = params.toLowerCase(Locale.ROOT);

            return switch (s) {
               case "time", "countdown" -> PlaceholderHook.this.kothTime();
               case "next_koth", "next" -> TimeFormat.hms(kothmodule.millisUntilNext());
               case "x" -> PlaceholderHook.this.blockCoord(kothmodule.regionCenter(), 0);
               case "y" -> PlaceholderHook.this.blockCoord(kothmodule.regionCenter(), 1);
               case "z" -> PlaceholderHook.this.blockCoord(kothmodule.regionCenter(), 2);
               case "active" -> kothmodule.isActive() ? "true" : "false";
               case "leader", "koth_player", "player" -> kothmodule.leaderName();
               case "leader_points", "points" -> String.format(Locale.US, "%.0f", kothmodule.leaderPoints());
               case "percent" -> String.format(Locale.US, "%.0f", kothmodule.eventPercent());
               case "bar", "progress" -> kothmodule.progressBar();
               default -> null;
            };
         }
      }
   }

   private final class OutpostExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "outpost";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         OutpostModule outpostmodule = PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
         if (outpostmodule == null) {
            return "";
         } else {
            String s = params.toLowerCase(Locale.ROOT);

            return switch (s) {
               case "time" -> PlaceholderHook.this.outpostTime();
               case "countdown", "next_outpost", "next" -> TimeFormat.hms(outpostmodule.millisUntilStart());
               case "x" -> PlaceholderHook.this.blockCoord(outpostmodule.regionCenter(), 0);
               case "y" -> PlaceholderHook.this.blockCoord(outpostmodule.regionCenter(), 1);
               case "z" -> PlaceholderHook.this.blockCoord(outpostmodule.regionCenter(), 2);
               case "active" -> outpostmodule.isActive() ? "true" : "false";
               case "capturer", "capturing", "contesting", "outpost_player", "player" -> outpostmodule.capturerName();
               case "percent", "percentage" -> String.format(Locale.US, "%.0f", outpostmodule.capturePercent());
               case "points" -> String.format(Locale.US, "%.0f", outpostmodule.capturePercent());
               case "bar", "progress" -> outpostmodule.progressBar();
               case "contested" -> outpostmodule.isContested() ? "Contested" : "Uncontested";
               default -> null;
            };
         }
      }
   }

   private final class PlayerPointsExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "playerpoints";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         if (player == null) {
            return "0";
         } else {
            TokensModule tokensmodule = PlaceholderHook.this.tokensModule();
            if (tokensmodule != null && tokensmodule.service() != null) {
               long i = tokensmodule.service().getBalance(player.getUniqueId());
               String s = params.toLowerCase(Locale.ROOT);

               return switch (s) {
                  case "points", "balance" -> String.valueOf(i);
                  case "points_formatted", "balance_formatted" -> Numbers.format(i);
                  case "points_shorthand" -> Numbers.format(i);
                  default -> null;
               };
            } else {
               return "0";
            }
         }
      }
   }

   private final class ShardedCoreExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "shardedcore";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         String s = params.toLowerCase(Locale.ROOT);
         if (player != null) {
            TokensModule tokensmodule = PlaceholderHook.this.tokensModule();
            if (tokensmodule != null && tokensmodule.service() != null) {
               if (s.equals("tokens") || s.equals("tokens_amount")) {
                  return String.valueOf(tokensmodule.service().getBalance(player.getUniqueId()));
               }

               if (s.equals("tokens_formatted")) {
                  return Numbers.format(tokensmodule.service().getBalance(player.getUniqueId()));
               }
            }

            KillstreakDatabase killstreakdatabase = PlaceholderHook.this.killstreakDb();
            if (killstreakdatabase != null) {
               if (s.equals("killstreak") || s.equals("killstreak_current")) {
                  return String.valueOf(killstreakdatabase.getCurrent(player.getUniqueId()));
               }

               if (s.equals("killstreak_best")) {
                  return String.valueOf(killstreakdatabase.getBest(player.getUniqueId()));
               }
            }

            TeamsModule teamsmodule = PlaceholderHook.this.teamsModule();
            if (teamsmodule != null && teamsmodule.database() != null && (s.equals("team") || s.equals("team_name") || s.equals("teamname"))) {
               Integer integer = teamsmodule.database().getTeamId(player.getUniqueId());
               if (integer == null) {
                  return teamsmodule.notInTeamPlaceholder();
               }

               TeamDatabase.Team teamdatabase$team = teamsmodule.database().getTeamById(integer);
               return teamdatabase$team == null ? teamsmodule.notInTeamPlaceholder() : teamdatabase$team.name();
            }

            com.sharded.core.modules.itemshop.ItemShopModule itemshop = PlaceholderHook.this.plugin.modules().get(
               com.sharded.core.modules.itemshop.ItemShopModule.class
            );
            if (itemshop != null) {
               if (s.equals("hat")) {
                  return itemshop.placeholderHat(player.getUniqueId());
               }
               if (s.equals("tag") || s.equals("tag_display")) {
                  String shopTag = itemshop.placeholderTag(player.getUniqueId());
                  if (!shopTag.isEmpty()) {
                     return shopTag;
                  }
               }
            }
            if (PlaceholderHook.this.plugin.cosmetics() != null && PlaceholderHook.this.plugin.cosmetics().database() != null) {
               CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = PlaceholderHook.this.plugin.cosmetics().database().get(player.getUniqueId());
               if (s.equals("tag") || s.equals("tag_display") || s.equals("eternaltags_tag") || s.equals("eternaltags_tag_display")) {
                  return cosmeticdatabase$playercosmetics.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(cosmeticdatabase$playercosmetics.tagDisplay());
               }
            }
            String keys = PlaceholderHook.this.crateKeyPlaceholder(player, s);
            if (keys != null) {
               return keys;
            }
         }

         String lb = PlaceholderHook.this.leaderboardPlaceholder(s);
         if (lb != null) {
            return lb;
         }
         if (s.startsWith("tokens_top_") || s.startsWith("token_top_")) {
            String s1 = s.startsWith("tokens_top_") ? s.substring("tokens_top_".length()) : s.substring("token_top_".length());
            return PlaceholderHook.this.leaderboardValue(s1, true);
         } else if (s.startsWith("killstreak_top_")) {
            return PlaceholderHook.this.leaderboardValue(s.substring("killstreak_top_".length()), false);
         } else if (s.equals("outpost_time") || s.equals("outpost_countdown")) {
            return PlaceholderHook.this.outpostTime();
         } else if (s.equals("koth_time") || s.equals("koth_countdown")) {
            return PlaceholderHook.this.kothTime();
         } else if (s.equals("outpost_active")) {
            OutpostModule outpostmodule2 = PlaceholderHook.this.cachedOutpost != null
               ? PlaceholderHook.this.cachedOutpost
               : PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
            return outpostmodule2 != null && outpostmodule2.isActive() ? "true" : "false";
         } else if (s.equals("koth_active")) {
            KothModule kothmodule2 = PlaceholderHook.this.cachedKoth != null
               ? PlaceholderHook.this.cachedKoth
               : PlaceholderHook.this.plugin.modules().get(KothModule.class);
            return kothmodule2 != null && kothmodule2.isActive() ? "true" : "false";
         } else if (s.equals("outpost_capturer") || s.equals("outpost_contesting")) {
            OutpostModule outpostmodule1 = PlaceholderHook.this.cachedOutpost != null
               ? PlaceholderHook.this.cachedOutpost
               : PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
            return outpostmodule1 == null ? "N/A" : outpostmodule1.contestingName();
         } else if (s.equals("outpost_percent")) {
            OutpostModule outpostmodule = PlaceholderHook.this.cachedOutpost != null
               ? PlaceholderHook.this.cachedOutpost
               : PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
            return outpostmodule == null ? "0" : String.format(Locale.US, "%.0f", outpostmodule.capturePercent());
         } else if (s.equals("outpost_bar") || s.equals("outpost_progress")) {
            OutpostModule outpostmodule = PlaceholderHook.this.cachedOutpost != null
               ? PlaceholderHook.this.cachedOutpost
               : PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
            return outpostmodule == null ? EventTabScoreboard.bar(0) : outpostmodule.progressBar();
         } else if (s.equals("outpost_contested")) {
            OutpostModule outpostmodule = PlaceholderHook.this.cachedOutpost != null
               ? PlaceholderHook.this.cachedOutpost
               : PlaceholderHook.this.plugin.modules().get(OutpostModule.class);
            return outpostmodule != null && outpostmodule.isContested() ? "Contested" : "Uncontested";
         } else if (s.equals("koth_leader")) {
            KothModule kothmodule1 = PlaceholderHook.this.cachedKoth != null
               ? PlaceholderHook.this.cachedKoth
               : PlaceholderHook.this.plugin.modules().get(KothModule.class);
            return kothmodule1 == null ? "None" : kothmodule1.leaderName();
         } else if (s.equals("koth_leader_points") || s.equals("koth_points")) {
            KothModule kothmodule = PlaceholderHook.this.cachedKoth != null
               ? PlaceholderHook.this.cachedKoth
               : PlaceholderHook.this.plugin.modules().get(KothModule.class);
            return kothmodule == null ? "0" : String.format(Locale.US, "%.0f", kothmodule.leaderPoints());
         } else if (s.equals("koth_percent")) {
            KothModule kothmodule = PlaceholderHook.this.cachedKoth != null
               ? PlaceholderHook.this.cachedKoth
               : PlaceholderHook.this.plugin.modules().get(KothModule.class);
            return kothmodule == null ? "0" : String.format(Locale.US, "%.0f", kothmodule.eventPercent());
         } else if (s.equals("koth_bar") || s.equals("koth_progress")) {
            KothModule kothmodule = PlaceholderHook.this.cachedKoth != null
               ? PlaceholderHook.this.cachedKoth
               : PlaceholderHook.this.plugin.modules().get(KothModule.class);
            return kothmodule == null ? EventTabScoreboard.bar(0) : kothmodule.progressBar();
         } else if (s.equals("outpost_hours") || s.equals("outpost_minutes") || s.equals("outpost_seconds")) {
            long i = PlaceholderHook.this.outpostMillis();
            return TimeFormat.replacePlaceholders("%" + s.substring("outpost_".length()) + "%", i);
         } else if (!s.equals("koth_hours") && !s.equals("koth_minutes") && !s.equals("koth_seconds")) {
            return null;
         } else {
            long j = PlaceholderHook.this.kothMillis();
            return TimeFormat.replacePlaceholders("%" + s.substring("koth_".length()) + "%", j);
         }
      }
   }

   private final class TokenExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "token";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         String s = params.toLowerCase(Locale.ROOT);
         if (player != null) {
            TokensModule tokensmodule = PlaceholderHook.this.tokensModule();
            if (tokensmodule != null && tokensmodule.service() != null) {
               if (s.equals("amount") || s.equals("balance") || s.equals("tokens")) {
                  return String.valueOf(tokensmodule.service().getBalance(player.getUniqueId()));
               }

               if (s.equals("formatted")) {
                  return Numbers.format(tokensmodule.service().getBalance(player.getUniqueId()));
               }
            }
         }

         return s.startsWith("top_") ? PlaceholderHook.this.leaderboardValue(s.substring("top_".length()), true) : null;
      }
   }

   private final class XKillstreakExpansion extends PlaceholderExpansion {
      @NotNull
      public String getIdentifier() {
         return "xkillstreak";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return PlaceholderHook.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onRequest(OfflinePlayer player, @NotNull String params) {
         if (player == null) {
            return "0";
         } else {
            KillstreakDatabase killstreakdatabase = PlaceholderHook.this.killstreakDb();
            if (killstreakdatabase == null) {
               return "0";
            } else {
               String s = params.toLowerCase(Locale.ROOT);

               return switch (s) {
                  case "current", "streak" -> String.valueOf(killstreakdatabase.getCurrent(player.getUniqueId()));
                  case "best", "max" -> String.valueOf(killstreakdatabase.getBest(player.getUniqueId()));
                  default -> null;
               };
            }
         }
      }
   }
}
