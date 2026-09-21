package com.sharded.core.modules.teams;

import com.sharded.core.gui.GuiNavigation;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

final class TeamGuiHandler {
   private final TeamsModule module;

   TeamGuiHandler(TeamsModule module) {
      this.module = module;
   }

   private static void trackAndOpen(Player player, TeamGuiHandler.TeamGuiHolder holder, Inventory inv) {
      holder.inventory = inv;
      TrackedInventories.track(inv, holder);
      player.openInventory(inv);
   }

   void openFor(Player player) {
      Integer integer = this.module.database().getTeamId(player.getUniqueId());
      if (integer == null) {
         this.openCreateStart(player);
      } else {
         this.openMain(player);
      }
   }

   void openCreateStart(Player player) {
      TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.CREATE_START, null, 0, 0);
      Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, 27, Text.c(this.module.guiRaw("create-title")));
      fill(inventory);
      inventory.setItem(13, button(Material.ANVIL, this.module.guiRaw("create-anvil-name"), this.module.guiRawList("create-anvil-lore")));
      trackAndOpen(player, teamguihandler$teamguiholder, inventory);
   }

   void openCreateConfirm(Player player, String name) {
      TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.CREATE_CONFIRM, name, 0, 0);
      Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, 27, Text.c(this.module.guiRaw("confirm-title", "%team%", name)));
      fill(inventory);
      inventory.setItem(11, button(Material.RED_STAINED_GLASS_PANE, this.module.guiRaw("cancel-name"), this.module.guiRawList("cancel-lore")));
      inventory.setItem(13, new ItemBuilder(Material.NAME_TAG).name("&f" + name).lore(this.module.guiRawList("confirm-lore")).build());
      inventory.setItem(15, button(Material.LIME_STAINED_GLASS_PANE, this.module.guiRaw("confirm-name"), this.module.guiRawList("confirm-lore")));
      trackAndOpen(player, teamguihandler$teamguiholder, inventory);
   }

   void openDisbandConfirm(Player player, String teamName) {
      TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.DISBAND_CONFIRM, teamName, 0, 0);
      Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, 27, Text.c(this.module.guiRaw("disband-confirm-title", "%team%", teamName)));
      fill(inventory);
      inventory.setItem(11, button(Material.RED_STAINED_GLASS_PANE, this.module.guiRaw("cancel-name"), this.module.guiRawList("disband-cancel-lore")));
      inventory.setItem(13, button(Material.TNT, this.module.guiRaw("disband-name"), this.module.guiRawList("disband-lore")));
      inventory.setItem(
         15, button(Material.LIME_STAINED_GLASS_PANE, this.module.guiRaw("disband-confirm-name"), this.module.guiRawList("disband-confirm-lore"))
      );
      trackAndOpen(player, teamguihandler$teamguiholder, inventory);
   }

   void openMain(Player player) {
      Integer integer = this.module.database().getTeamId(player.getUniqueId());
      if (integer == null) {
         this.openCreateStart(player);
      } else {
         TeamDatabase.Team teamdatabase$team = this.module.database().getTeamById(integer);
         TeamDatabase.Member teamdatabase$member = this.module.database().getMember(integer, player.getUniqueId());
         boolean flag = teamdatabase$team != null && teamdatabase$team.leaderUuid().equals(player.getUniqueId());
         int i = this.guiSize("gui.rows", 4);
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.MAIN, null, integer, 0);
         Inventory inventory = Bukkit.createInventory(
            teamguihandler$teamguiholder, i, Text.c(this.module.guiRaw("main-title", "%team%", teamdatabase$team == null ? "?" : teamdatabase$team.name()))
         );
         fill(inventory, i);
         TeamGuiHandler.TeamStats teamguihandler$teamstats = this.computeStats(integer);
         int j = this.teamRank(integer);
         String s = j > 0 ? String.valueOf(j) : this.module.guiRaw("unranked");
         this.setConfiguredItem(
            inventory, "home-slot", 10, "home-material", Material.RED_BED, this.module.guiRaw("home-name"), this.module.guiRawList("home-lore")
         );
         this.setConfiguredItem(
            inventory,
            "emergency-slot",
            11,
            "emergency-material",
            Material.GOAT_HORN,
            this.module.guiRaw("emergency-name"),
            this.module.guiRawList("emergency-lore")
         );
         this.setConfiguredItem(
            inventory, "browse-slot", 12, "browse-material", Material.SPYGLASS, this.module.guiRaw("browse-name"), this.module.guiRawList("browse-lore")
         );
         int k = this.slot("members-slot", 13);
         UUID uuid = teamdatabase$team == null ? player.getUniqueId() : teamdatabase$team.leaderUuid();
         inventory.setItem(
            k,
            ownerHead(
               uuid,
               this.module.guiRaw("members-name"),
               this.module.guiRawList("members-lore", "%count%", String.valueOf(this.module.database().getMembers(integer).size()))
            )
         );
         this.setConfiguredItem(
            inventory,
            "chat-slot",
            14,
            "chat-material",
            Material.WRITABLE_BOOK,
            this.module.guiRaw("chat-name"),
            this.module.guiRawList("chat-lore", "%status%", this.module.isTeamChat(player.getUniqueId()) ? "&aON" : "&cOFF")
         );
         this.setConfiguredItem(
            inventory,
            "allies-slot",
            15,
            "allies-material",
            Material.SHIELD,
            this.module.guiRaw("allies-name"),
            this.module
               .guiRawList(
                  "allies-lore",
                  "%count%",
                  String.valueOf(this.module.database().allyCount(integer)),
                  "%max%",
                  String.valueOf(this.module.teamConfig().getInt("ally.max-allies", 1))
               )
         );
         this.setConfiguredItem(
            inventory,
            "leaderboard-slot",
            16,
            "leaderboard-material",
            Material.PINK_BANNER,
            this.module.guiRaw("leaderboard-name"),
            this.module
               .guiRawList(
                  "leaderboard-lore",
                  "%rank%",
                  s,
                  "%kills%",
                  String.valueOf(teamguihandler$teamstats.kills),
                  "%money%",
                  String.valueOf(teamguihandler$teamstats.tokens),
                  "%playtime%",
                  this.module.formatPlaytime(teamguihandler$teamstats.playtime)
               )
         );
         this.setConfiguredItem(
            inventory,
            "enderchest-slot",
            21,
            "enderchest-material",
            Material.ENDER_CHEST,
            this.module.guiRaw("enderchest-name"),
            this.module.guiRawList("enderchest-lore")
         );
         this.setConfiguredItem(
            inventory,
            "settings-slot",
            23,
            "settings-material",
            Material.REPEATER,
            this.module.guiRaw("settings-name"),
            this.module.guiRawList("settings-lore")
         );
         int l = this.slot("disband-slot", 22);
         if (flag) {
            inventory.setItem(
               l, button(this.material("disband-material", Material.BARRIER), this.module.guiRaw("disband-name"), this.module.guiRawList("disband-lore"))
            );
         } else if (teamdatabase$member != null) {
            inventory.setItem(
               l, button(this.material("disband-material", Material.OAK_DOOR), this.module.guiRaw("leave-name"), this.module.guiRawList("leave-lore"))
            );
         }

         trackAndOpen(player, teamguihandler$teamguiholder, inventory);
      }
   }

   void openSettings(Player player) {
      Integer integer = this.module.database().getTeamId(player.getUniqueId());
      if (integer != null) {
         TeamDatabase.Team teamdatabase$team = this.module.database().getTeamById(integer);
         boolean flag = this.module.database().isPvp(integer);
         String s = teamdatabase$team == null
            ? "?"
            : (teamdatabase$team.display() != null && !teamdatabase$team.display().isBlank() ? teamdatabase$team.display() : teamdatabase$team.name());
         int i = this.guiSize("gui.settings-rows", 3);
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.SETTINGS, null, integer, 0);
         Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, i, Text.c(this.module.guiRaw("settings-title")));
         Material material = this.material("settings-filler", Material.BLACK_STAINED_GLASS_PANE);
         ItemStack itemstack = new ItemBuilder(material).name(" ").build();

         for (int j = 0; j < i; j++) {
            inventory.setItem(j, itemstack);
         }

         this.setConfiguredItem(
            inventory, "tag-slot", 11, "tag-material", Material.NAME_TAG, this.module.guiRaw("tag-name"), this.module.guiRawList("tag-lore", "%tag%", s)
         );
         this.setConfiguredItem(
            inventory,
            "home-set-slot",
            13,
            "home-set-material",
            Material.RED_BANNER,
            this.module.guiRaw("home-set-name"),
            this.module.guiRawList("home-set-lore")
         );
         Material material1 = flag ? this.material("pvp-on-material", Material.LIME_WOOL) : this.material("pvp-off-material", Material.RED_WOOL);
         String s1 = flag ? this.module.guiRaw("pvp-name-on") : this.module.guiRaw("pvp-name-off");
         if (s1 == null || s1.isBlank()) {
            s1 = this.module.guiRaw("pvp-name");
         }

         inventory.setItem(this.slot("pvp-slot", 15), button(material1, s1, this.module.guiRawList("pvp-lore", "%status%", flag ? "&aON" : "&cOFF")));
         inventory.setItem(this.slot("settings-back-slot", 22), this.navButton("back"));
         trackAndOpen(player, teamguihandler$teamguiholder, inventory);
      }
   }

   void openEnderchest(Player player) {
      Integer integer = this.module.database().getTeamId(player.getUniqueId());
      if (integer != null) {
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.ENDERCHEST, null, integer, 0);
         Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, 27, Text.c(this.module.guiRaw("enderchest-title")));
         ItemStack[] aitemstack = this.module.database().getEnderchest(integer);

         for (int i = 0; i < Math.min(27, aitemstack.length); i++) {
            if (aitemstack[i] != null) {
               inventory.setItem(i, aitemstack[i]);
            }
         }

         trackAndOpen(player, teamguihandler$teamguiholder, inventory);
      }
   }

   void openMembers(Player player) {
      Integer integer = this.module.database().getTeamId(player.getUniqueId());
      if (integer != null) {
         TeamDatabase.Team teamdatabase$team = this.module.database().getTeamById(integer);
         ArrayList<TeamDatabase.Member> arraylist = new ArrayList<>(this.module.database().getMembers(integer));
         arraylist.sort(Comparator.comparingInt(TeamDatabase.Member::role));
         int i = 54;
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.MEMBERS, null, 0, 0);
         Inventory inventory = Bukkit.createInventory(
            teamguihandler$teamguiholder, i, Text.c(this.module.guiRaw("members-title", "%team%", teamdatabase$team == null ? "?" : teamdatabase$team.name()))
         );
         fill(inventory, i);
         if (teamdatabase$team != null) {
            inventory.setItem(4, this.memberHead(teamdatabase$team.leaderUuid(), 0, true));
         }

         int j = 10;

         for (TeamDatabase.Member teamdatabase$member : arraylist) {
            if (teamdatabase$team == null || !teamdatabase$member.uuid().equals(teamdatabase$team.leaderUuid())) {
               while (j < i && (j % 9 == 0 || j % 9 == 8)) {
                  j++;
               }

               if (j >= i - 9) {
                  break;
               }

               inventory.setItem(j++, this.memberHead(teamdatabase$member.uuid(), teamdatabase$member.role(), false));
            }
         }

         inventory.setItem(49, this.navButton("back"));
         trackAndOpen(player, teamguihandler$teamguiholder, inventory);
      }
   }

   void openBrowse(Player player, int page) {
      ArrayList<TeamDatabase.Team> arraylist = new ArrayList<>(this.module.database().listTeams());
      arraylist.sort(Comparator.comparing(TeamDatabase.Team::name, String.CASE_INSENSITIVE_ORDER));
      int i = this.module.teamConfig().getInt("gui.browse-per-page", 21);
      int j = Math.max(0, (arraylist.size() + i - 1) / i - 1);
      page = Math.max(0, Math.min(page, j));
      TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.BROWSE, null, 0, page);
      Inventory inventory = Bukkit.createInventory(teamguihandler$teamguiholder, 54, Text.c(this.module.guiRaw("browse-title")));
      fill(inventory, 54);
      int k = page * i;
      int l = 10;

      for (int i1 = k; i1 < Math.min(k + i, arraylist.size()); i1++) {
         while (l < 44 && (l % 9 == 0 || l % 9 == 8)) {
            l++;
         }

         if (l > 43) {
            break;
         }

         TeamDatabase.Team teamdatabase$team = arraylist.get(i1);
         inventory.setItem(l++, this.browseItem(teamdatabase$team));
      }

      if (page > 0) {
         inventory.setItem(45, this.navButton("previous"));
      }

      if (page < j) {
         inventory.setItem(52, this.navButton("next"));
      }

      inventory.setItem(53, this.navButton("back"));
      trackAndOpen(player, teamguihandler$teamguiholder, inventory);
   }

   void openProfile(Player player, int teamId) {
      TeamDatabase.Team teamdatabase$team = this.module.database().getTeamById(teamId);
      if (teamdatabase$team == null) {
         this.openBrowse(player, 0);
      } else {
         TeamGuiHandler.TeamStats teamguihandler$teamstats = this.computeStats(teamId);
         int i = this.teamRank(teamId);
         String s = i > 0 ? String.valueOf(i) : this.module.guiRaw("unranked");
         long j = this.teamScore(teamId, teamguihandler$teamstats);
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = new TeamGuiHandler.TeamGuiHolder(TeamGuiHandler.MenuType.PROFILE, null, teamId, 0);
         Inventory inventory = Bukkit.createInventory(
            teamguihandler$teamguiholder, 27, Text.c(this.module.guiRaw("profile-title", "%team%", teamdatabase$team.name()))
         );
         fill(inventory);
         inventory.setItem(
            13,
            ownerHead(
               teamdatabase$team.leaderUuid(),
               this.module.guiRaw("profile-item-name", "%team%", teamdatabase$team.name()),
               this.module
                  .guiRawList(
                     "profile-item-lore",
                     "%rank%",
                     s,
                     "%count%",
                     String.valueOf(this.module.database().getMembers(teamId).size()),
                     "%kills%",
                     String.valueOf(teamguihandler$teamstats.kills),
                     "%tokens%",
                     String.valueOf(teamguihandler$teamstats.tokens),
                     "%money%",
                     String.valueOf(teamguihandler$teamstats.tokens),
                     "%playtime%",
                     this.module.formatPlaytime(teamguihandler$teamstats.playtime),
                     "%score%",
                     String.valueOf(j)
                  )
            )
         );
         inventory.setItem(22, this.navButton("back"));
         trackAndOpen(player, teamguihandler$teamguiholder, inventory);
      }
   }

   void handleClick(Player player, TeamGuiHandler.TeamGuiHolder holder, int slot, ItemStack current) {
      switch (holder.type) {
         case CREATE_START:
            if (slot == 13) {
               this.module.beginCreateNameInput(player);
            }
            break;
         case CREATE_CONFIRM:
            if (slot == 11) {
               this.openCreateStart(player);
            } else if (slot == 15 && holder.pendingName != null) {
               this.module.confirmCreate(player, holder.pendingName);
               this.openMain(player);
            }
            break;
         case DISBAND_CONFIRM:
            if (slot == 11) {
               this.openMain(player);
            } else if (slot == 15) {
               player.closeInventory();
               this.module.handleDisband(player);
            }
            break;
         case MAIN:
            this.handleMainClick(player, slot);
            break;
         case SETTINGS:
            this.handleSettingsClick(player, slot);
            break;
         case MEMBERS:
            this.handleMembersClick(player, slot, current);
            break;
         case BROWSE:
            this.handleBrowseClick(player, holder, slot);
            break;
         case PROFILE:
            if (slot == 22) {
               this.openBrowse(player, 0);
            }
         case ENDERCHEST:
      }
   }

   private void handleMainClick(Player player, int slot) {
      if (slot == this.slot("disband-slot", 22)) {
         Integer integer = this.module.database().getTeamId(player.getUniqueId());
         if (integer != null) {
            TeamDatabase.Team teamdatabase$team = this.module.database().getTeamById(integer);
            if (teamdatabase$team != null && teamdatabase$team.leaderUuid().equals(player.getUniqueId())) {
               this.openDisbandConfirm(player, teamdatabase$team.name());
            } else {
               player.closeInventory();
               this.module.handleLeave(player);
            }
         }
      } else if (slot == this.slot("members-slot", 13)) {
         this.openMembers(player);
      } else if (slot == this.slot("emergency-slot", 11)) {
         player.closeInventory();
         this.module.handleEmergency(player);
      } else if (slot == this.slot("browse-slot", 12)) {
         this.openBrowse(player, 0);
      } else if (slot == this.slot("chat-slot", 14)) {
         this.module.toggleTeamChat(player);
         this.openMain(player);
      } else if (slot == this.slot("allies-slot", 15)) {
         this.module.send(player, "usage-ally", new String[0]);
      } else if (slot == this.slot("leaderboard-slot", 16)) {
         player.closeInventory();
         player.performCommand("leaderboard teams");
      } else if (slot == this.slot("home-slot", 10)) {
         player.closeInventory();
         this.module.handleHome(player);
      } else if (slot == this.slot("enderchest-slot", 21)) {
         this.openEnderchest(player);
      } else {
         if (slot == this.slot("settings-slot", 23)) {
            this.openSettings(player);
         }
      }
   }

   private void handleSettingsClick(Player player, int slot) {
      if (slot == this.slot("settings-back-slot", 22)) {
         this.openMain(player);
      } else if (slot == this.slot("tag-slot", 11)) {
         this.module.beginRenameInput(player);
      } else if (slot == this.slot("home-set-slot", 13)) {
         this.module.handleSetHome(player);
         this.openSettings(player);
      } else {
         if (slot == this.slot("pvp-slot", 15)) {
            this.module.handlePvpToggle(player);
            this.openSettings(player);
         }
      }
   }

   private void handleBrowseClick(Player player, TeamGuiHandler.TeamGuiHolder holder, int slot) {
      if (slot == 53) {
         this.openMain(player);
      } else if (slot == 45 && holder.page > 0) {
         this.openBrowse(player, holder.page - 1);
      } else if (slot == 52) {
         this.openBrowse(player, holder.page + 1);
      } else {
         ItemStack itemstack = player.getOpenInventory().getTopInventory().getItem(slot);
         if (itemstack != null && itemstack.hasItemMeta()) {
            Integer integer = this.module.teamIdFromItem(itemstack);
            if (integer != null) {
               this.openProfile(player, integer);
            }
         }
      }
   }

   private void handleMembersClick(Player player, int slot, ItemStack item) {
      if (slot == 49) {
         this.openMain(player);
      } else if (item != null && item.getType() == Material.PLAYER_HEAD) {
         if (item.getItemMeta() instanceof SkullMeta skullmeta && skullmeta.getOwningPlayer() != null) {
            UUID uuid = skullmeta.getOwningPlayer().getUniqueId();
            if (uuid.equals(player.getUniqueId())) {
               return;
            }

            player.closeInventory();
            this.module.handleMemberHeadClick(player, uuid);
            Bukkit.getScheduler().runTaskLater(this.module.plugin(), () -> this.openMembers(player), 2L);
            return;
         }
      }
   }

   private ItemStack browseItem(TeamDatabase.Team team) {
      int i = this.teamRank(team.id());
      String s = i > 0 ? String.valueOf(i) : this.module.guiRaw("unranked");
      ItemStack itemstack = ownerHead(
         team.leaderUuid(),
         this.module.guiRaw("browse-entry-name", "%team%", team.name()),
         this.module.guiRawList("browse-entry-lore", "%count%", String.valueOf(this.module.database().getMembers(team.id()).size()), "%rank%", s)
      );
      return this.module.tagTeamId(itemstack, team.id());
   }

   private ItemStack navButton(String type) {
      GuiNavigation guinavigation = this.module.plugin().guiNavigation();
      if (guinavigation == null) {
         return new ItemBuilder(Material.BARRIER).name("&c" + type).build();
      } else {
         ConfigurationSection configurationsection = this.module.teamConfig().getConfigurationSection("gui.navigation." + type);
         return guinavigation.build(type, configurationsection);
      }
   }

   private int teamRank(int teamId) {
      LeaderboardsModule leaderboardsmodule = this.module.plugin().modules().get(LeaderboardsModule.class);
      return leaderboardsmodule == null ? -1 : leaderboardsmodule.teamRank(teamId);
   }

   private long teamScore(int teamId, TeamGuiHandler.TeamStats stats) {
      long i = this.module.teamConfig().getLong("leaderboard.token-weight", 1L);
      long j = this.module.teamConfig().getLong("leaderboard.kill-weight", 100L);
      long k = this.module.teamConfig().getLong("leaderboard.playtime-hour-weight", 50L);
      long l = stats.playtime / 3600000L;
      return stats.tokens * i + (long)stats.kills * j + l * k;
   }

   private TeamGuiHandler.TeamStats computeStats(int teamId) {
      long i = 0L;
      int j = 0;
      long k = 0L;
      TokenService tokenservice = this.module.plugin().modules().tokens();

      for (TeamDatabase.Member teamdatabase$member : this.module.database().getMembers(teamId)) {
         j += teamdatabase$member.kills();
         k += teamdatabase$member.playtimeMs();
         if (tokenservice != null) {
            i += tokenservice.getBalance(teamdatabase$member.uuid());
         }
      }

      return new TeamGuiHandler.TeamStats(i, j, k);
   }

   private ItemStack memberHead(UUID uuid, int role, boolean leader) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta skullmeta = (SkullMeta)itemstack.getItemMeta();
      skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
      skullmeta.displayName(Text.c("&f" + OfflinePlayers.name(uuid)));
      String s = Bukkit.getPlayer(uuid) != null ? "&aOnline" : "&cOffline";
      List<String> list = leader
         ? this.module.guiRawList("leader-head-lore", "%status%", s)
         : this.module.guiRawList("member-head-lore", "%role%", this.module.roleName(role), "%status%", s);
      skullmeta.lore(list.stream().map(Text::c).toList());
      itemstack.setItemMeta(skullmeta);
      return itemstack;
   }

   private static ItemStack ownerHead(UUID ownerId, String name, List<String> lore) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta skullmeta = (SkullMeta)itemstack.getItemMeta();
      skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId));
      skullmeta.displayName(Text.c(name));
      skullmeta.lore(lore.stream().map(Text::c).toList());
      itemstack.setItemMeta(skullmeta);
      return itemstack;
   }

   private void setConfiguredItem(Inventory inv, String slotKey, int defaultSlot, String materialKey, Material fallback, String name, List<String> lore) {
      inv.setItem(this.slot(slotKey, defaultSlot), button(this.material(materialKey, fallback), name, lore));
   }

   private int slot(String key, int fallback) {
      return this.module.teamConfig().getInt("gui." + key, fallback);
   }

   private int guiSize(String key, int defaultRows) {
      int i = Math.max(1, Math.min(6, this.module.teamConfig().getInt(key, defaultRows)));
      return i * 9;
   }

   private Material material(String key, Material fallback) {
      String s = this.module.teamConfig().getString("gui." + key);
      if (s != null && !s.isBlank()) {
         Material material = Material.matchMaterial(s.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   private static void fill(Inventory inv) {
      fill(inv, inv.getSize());
   }

   private static void fill(Inventory inv, int size) {
      ItemStack itemstack = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

      for (int i = 0; i < size; i++) {
         inv.setItem(i, itemstack);
      }
   }

   private static ItemStack button(Material material, String name, List<String> lore) {
      return new ItemBuilder(material).name(name).lore(lore).build();
   }

   static enum MenuType {
      CREATE_START,
      CREATE_CONFIRM,
      DISBAND_CONFIRM,
      MAIN,
      SETTINGS,
      MEMBERS,
      BROWSE,
      PROFILE,
      ENDERCHEST;
   }

   static final class TeamGuiHolder implements InventoryHolder {
      final TeamGuiHandler.MenuType type;
      final String pendingName;
      final int teamId;
      final int page;
      Inventory inventory;

      TeamGuiHolder(TeamGuiHandler.MenuType type, String pendingName, int teamId, int page) {
         this.type = type;
         this.pendingName = pendingName;
         this.teamId = teamId;
         this.page = page;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record TeamStats(long tokens, int kills, long playtime) {
   }
}
