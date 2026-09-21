package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LuckPermsHook {
   private final ShardedCore plugin;
   private boolean available;

   public LuckPermsHook(ShardedCore plugin) {
      this.plugin = plugin;

      try {
         Class.forName("net.luckperms.api.LuckPermsProvider");
         LuckPermsProvider.get();
         this.available = true;
         plugin.getLogger().info("Hooked into LuckPerms.");
      } catch (Throwable throwable) {
         this.available = false;
         plugin.getLogger().info("LuckPerms not found - rank placeholders will be empty.");
      }
   }

   public boolean isAvailable() {
      return this.available;
   }

   public String prefix(Player player) {
      return this.meta(player, true);
   }

   public String prefix(UUID uuid) {
      if (!this.available) {
         return "";
      } else {
         try {
            User user = this.user(uuid);
            if (user == null) {
               return "";
            } else {
               CachedMetaData cachedmetadata = user.getCachedData().getMetaData();
               String s = cachedmetadata.getPrefix();
               return s == null ? "" : s;
            }
         } catch (Throwable throwable) {
            return "";
         }
      }
   }

   public String suffix(Player player) {
      return this.meta(player, false);
   }

   public String primaryGroup(Player player) {
      if (!this.available) {
         return "";
      } else {
         try {
            User user = this.user(player.getUniqueId());
            return user == null ? "" : user.getPrimaryGroup();
         } catch (Throwable throwable) {
            return "";
         }
      }
   }

   public boolean hasPermanentGroup(UUID uuid, String group) {
      if (!this.available) {
         return false;
      } else {
         User user = this.user(uuid);
         if (user == null) {
            return false;
         } else {
            String s = group.toLowerCase(Locale.ROOT);
            return user.getNodes(NodeType.INHERITANCE).stream().anyMatch(node -> node.getGroupName().equalsIgnoreCase(s) && !node.hasExpiry());
         }
      }
   }

   public boolean hasActiveTempGroup(UUID uuid, String group) {
      if (!this.available) {
         return false;
      } else {
         User user = this.user(uuid);
         if (user == null) {
            return false;
         } else {
            String s = group.toLowerCase(Locale.ROOT);
            return user.getNodes(NodeType.INHERITANCE)
               .stream()
               .anyMatch(node -> node.getGroupName().equalsIgnoreCase(s) && node.hasExpiry() && !node.hasExpired());
         }
      }
   }

   public Optional<Duration> tempGroupTimeLeft(UUID uuid, String group) {
      if (!this.available) {
         return Optional.empty();
      } else {
         User user = this.user(uuid);
         if (user == null) {
            return Optional.empty();
         } else {
            String s = group.toLowerCase(Locale.ROOT);
            Instant instant = Instant.now();
            return user.getNodes(NodeType.INHERITANCE)
               .stream()
               .filter(node -> node.getGroupName().equalsIgnoreCase(s) && node.hasExpiry() && !node.hasExpired())
               .map(node -> Duration.between(instant, node.getExpiry()))
               .filter(d -> !d.isNegative() && !d.isZero())
               .findFirst();
         }
      }
   }

   public Optional<String> highestPermanentRank(UUID uuid, List<String> orderedRanks) {
      if (this.available && orderedRanks != null) {
         String s = null;

         for (String s1 : orderedRanks) {
            if (this.hasPermanentGroup(uuid, s1)) {
               s = s1;
            }
         }

         return Optional.ofNullable(s);
      } else {
         return Optional.empty();
      }
   }

   public int rankIndex(List<String> orderedRanks, String rank) {
      if (orderedRanks != null && rank != null) {
         for (int i = 0; i < orderedRanks.size(); i++) {
            if (orderedRanks.get(i).equalsIgnoreCase(rank)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public boolean runConsole(String command) {
      return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
   }

   private User user(UUID uuid) {
      try {
         LuckPerms luckperms = LuckPermsProvider.get();
         User user = luckperms.getUserManager().getUser(uuid);
         return user != null ? user : (User)luckperms.getUserManager().loadUser(uuid).join();
      } catch (Throwable throwable) {
         return null;
      }
   }

   private String meta(Player player, boolean prefix) {
      if (!this.available) {
         return "";
      } else {
         try {
            User user = this.user(player.getUniqueId());
            if (user == null) {
               return "";
            } else {
               CachedMetaData cachedmetadata = user.getCachedData().getMetaData();
               String s = prefix ? cachedmetadata.getPrefix() : cachedmetadata.getSuffix();
               return s == null ? "" : s;
            }
         } catch (Throwable throwable) {
            return "";
         }
      }
   }
}
