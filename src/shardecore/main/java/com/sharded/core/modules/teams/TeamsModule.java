package com.sharded.core.modules.teams;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.combat.CombatModule;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.EnglishInputUtil;
import com.sharded.core.util.GuiFooters;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class TeamsModule extends Module implements CommandExecutor, TabCompleter {
   private TeamDatabase database;
   private TeamGuiHandler guiHandler;
   private NamespacedKey teamItemKey;
   private NamespacedKey pageItemKey;
   private final Set<UUID> teamChatMode = ConcurrentHashMap.newKeySet();
   private final Set<UUID> awaitingTeamName = ConcurrentHashMap.newKeySet();
   private final Set<UUID> awaitingRename = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();
   private final Map<UUID, Long> emergencyCooldown = new ConcurrentHashMap<>();
   private final Map<UUID, BukkitTask> pendingHomes = new ConcurrentHashMap<>();

   public TeamsModule(ShardedCore plugin) {
      super(plugin, "teams");
   }

   ShardedCore plugin() {
      return this.plugin;
   }

   YamlConfiguration teamConfig() {
      return this.config;
   }

   boolean isTeamChat(UUID uuid) {
      return this.teamChatMode.contains(uuid);
   }

   String formatPlaytime(long ms) {
      return Text.formatPlaytime(ms / 60000L);
   }

   String roleName(int role) {
      return switch (role) {
         case 0 -> this.raw("role-leader", new String[0]);
         case 1 -> this.raw("role-officer", new String[0]);
         default -> this.raw("role-member", new String[0]);
      };
   }

   public TeamDatabase database() {
      return this.database;
   }

   public String notInTeamPlaceholder() {
      return this.config.getString("placeholders.not-in-team", "N/A");
   }

   public String getTeamName(java.util.UUID uuid) {
      if (this.database == null || uuid == null) {
         return null;
      }
      Integer teamId = this.database.getTeamId(uuid);
      if (teamId == null) {
         return null;
      }
      TeamDatabase.Team team = this.database.getTeamById(teamId);
      if (team == null) {
         return null;
      }
      return team.display() != null && !team.display().isBlank() ? team.display() : team.name();
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new TeamDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open teams database", exception);
      }

      this.teamItemKey = new NamespacedKey(this.plugin, "team_id");
      this.pageItemKey = new NamespacedKey(this.plugin, "team_page");
      this.guiHandler = new TeamGuiHandler(this);
      this.registerCommand("team", this);
      this.registerCommand("teams", this);
   }

   String guiRaw(String key, String... replacements) {
      String s = this.config.getString("gui.click-footer", GuiFooters.view());
      String s1 = this.config.getString("gui.click-footer-confirm", GuiFooters.confirm());
      String s2 = this.config.getString("gui.click-footer-cancel", GuiFooters.cancel());
      String s3 = this.config.getString("gui.click-footer-create", GuiFooters.create());
      String s4 = this.config.getString("gui." + key, "");
      s4 = s4.replace("%click%", s).replace("%click_confirm%", s1).replace("%click_cancel%", s2).replace("%click_create%", s3);
      return Text.apply(s4, replacements);
   }

   List<String> guiRawList(String key, String... replacements) {
      String s = this.config.getString("gui.click-footer", GuiFooters.view());
      String s1 = this.config.getString("gui.click-footer-confirm", GuiFooters.confirm());
      String s2 = this.config.getString("gui.click-footer-cancel", GuiFooters.cancel());
      String s3 = this.config.getString("gui.click-footer-create", GuiFooters.create());
      ArrayList<String> arraylist = new ArrayList<>(this.config.getStringList("gui." + key));
      if (arraylist.isEmpty()) {
         String s4 = this.config.getString("gui." + key);
         if (s4 != null && !s4.isEmpty()) {
            arraylist.add(s4);
         }
      }

      ArrayList<String> arraylist1 = new ArrayList<>(arraylist.size());

      for (String s5 : arraylist) {
         arraylist1.add(
            Text.apply(s5.replace("%click%", s).replace("%click_confirm%", s1).replace("%click_cancel%", s2).replace("%click_create%", s3), replacements)
         );
      }

      return arraylist1;
   }

   List<String> guiRawList(Player player, String key, String... replacements) {
      return PlaceholderUtil.applyList(player, this.guiRawList(key, replacements));
   }

   String guiRaw(Player player, String key, String... replacements) {
      return PlaceholderUtil.apply(player, this.guiRaw(key, replacements));
   }

   ItemStack tagTeamId(ItemStack item, int teamId) {
      item.editMeta(meta -> meta.getPersistentDataContainer().set(this.teamItemKey, PersistentDataType.INTEGER, teamId));
      return item;
   }

   Integer teamIdFromItem(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? (Integer)item.getItemMeta().getPersistentDataContainer().get(this.teamItemKey, PersistentDataType.INTEGER)
         : null;
   }

   ItemStack tagPage(ItemStack item, int page) {
      item.editMeta(meta -> meta.getPersistentDataContainer().set(this.pageItemKey, PersistentDataType.INTEGER, page));
      return item;
   }

   @Override
   protected void onDisable() {
      this.awaitingTeamName.clear();
      this.awaitingRename.clear();

      for (BukkitTask bukkittask : this.pendingHomes.values()) {
         bukkittask.cancel();
      }

      this.pendingHomes.clear();
      this.flushPlaytimeAll();
      this.teamChatMode.clear();
      this.onlineSince.clear();
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
      this.guiHandler = null;
   }

   void beginCreateNameInput(Player player) {
      if (this.database.getTeamId(player.getUniqueId()) != null) {
         this.send(player, "already-in-team", new String[0]);
      } else {
         this.awaitingRename.remove(player.getUniqueId());
         this.awaitingTeamName.add(player.getUniqueId());
         player.closeInventory();
         this.send(player, "type-name", new String[0]);
      }
   }

   void beginRenameInput(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         if (!this.isLeader(player, integer)) {
            this.send(player, "not-leader", new String[0]);
         } else {
            this.awaitingTeamName.remove(player.getUniqueId());
            this.awaitingRename.add(player.getUniqueId());
            player.closeInventory();
            this.send(player, "type-rename", new String[0]);
         }
      }
   }

   void confirmCreate(Player player, String name) {
      if (this.validateTeamName(player, name, false)) {
         TeamDatabase.Team teamdatabase$team = this.database.createTeam(name, player.getUniqueId());
         if (teamdatabase$team == null) {
            this.send(player, "failed", new String[0]);
         } else {
            this.send(player, "created", new String[]{"%team%", teamdatabase$team.name()});
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
         }
      }
   }

   boolean validateTeamName(Player player, String name, boolean renaming) {
      if (!renaming && this.database.getTeamId(player.getUniqueId()) != null) {
         this.send(player, "already-in-team", new String[0]);
         return false;
      } else {
         name = name.trim();
         int i = this.config.getInt("creation.min-name-length", 3);
         int j = this.config.getInt("creation.max-name-length", 16);
         if (name.length() < i || name.length() > j) {
            this.send(player, "invalid-name", new String[0]);
            return false;
         } else if (name.matches("[a-zA-Z0-9_]+") && EnglishInputUtil.isEnglishLettersOnly(name.replace("_", "").replaceAll("[0-9]", ""))) {
            for (String s : this.config.getStringList("creation.banned-keywords")) {
               if (name.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT))) {
                  this.send(player, "banned-name", new String[0]);
                  return false;
               }
            }

            for (String s1 : this.config.getStringList("creation.blacklisted-names")) {
               if (name.equalsIgnoreCase(s1)) {
                  this.send(player, "banned-name", new String[0]);
                  return false;
               }
            }

            TeamDatabase.Team teamdatabase$team = this.database.getTeamByName(name);
            if (teamdatabase$team != null) {
               if (!renaming) {
                  this.send(player, "name-taken", new String[0]);
                  return false;
               }

               Integer integer = this.database.getTeamId(player.getUniqueId());
               if (integer == null || teamdatabase$team.id() != integer) {
                  this.send(player, "name-taken", new String[0]);
                  return false;
               }
            }

            return true;
         } else {
            this.send(player, "invalid-name", new String[0]);
            return false;
         }
      }
   }

   void handleMemberHeadClick(Player player, UUID targetId) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         TeamDatabase.Member teamdatabase$member1 = this.database.getMember(integer, targetId);
         if (teamdatabase$member != null && teamdatabase$member1 != null) {
            boolean flag = this.isLeader(player, integer);
            if (flag) {
               if (teamdatabase$member1.role() == 2) {
                  this.database.setRole(integer, targetId, 1);
                  this.send(player, "promoted", new String[]{"%player%", OfflinePlayers.name(targetId)});
               } else if (teamdatabase$member1.role() == 1) {
                  this.database.setRole(integer, targetId, 2);
                  this.send(player, "demoted", new String[]{"%player%", OfflinePlayers.name(targetId)});
               }
            } else {
               if (teamdatabase$member.role() <= 1 && teamdatabase$member1.role() > 1) {
                  this.database.removeMember(integer, targetId);
                  this.send(player, "kicked", new String[]{"%player%", OfflinePlayers.name(targetId)});
                  Player playerx = Bukkit.getPlayer(targetId);
                  if (playerx != null) {
                     this.send(playerx, "kicked-target", new String[0]);
                  }
               } else {
                  this.send(player, "not-officer", new String[0]);
               }
            }
         }
      }
   }

   @EventHandler
   public void onGuiClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = TrackedInventories.lookup(
            event.getView().getTopInventory(), TeamGuiHandler.TeamGuiHolder.class
         );
         if (teamguihandler$teamguiholder != null) {
            if (teamguihandler$teamguiholder.type != TeamGuiHandler.MenuType.ENDERCHEST) {
               event.setCancelled(true);
               if (event.getClickedInventory() == event.getView().getTopInventory()) {
                  this.guiHandler.handleClick(player, teamguihandler$teamguiholder, event.getSlot(), event.getCurrentItem());
               }
            }
         }
      }
   }

   @EventHandler
   public void onGuiClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player) {
         TeamGuiHandler.TeamGuiHolder teamguihandler$teamguiholder = TrackedInventories.lookup(event.getInventory(), TeamGuiHandler.TeamGuiHolder.class);
         if (teamguihandler$teamguiholder != null
            && teamguihandler$teamguiholder.type == TeamGuiHandler.MenuType.ENDERCHEST
            && teamguihandler$teamguiholder.teamId > 0) {
            this.database.setEnderchest(teamguihandler$teamguiholder.teamId, event.getInventory().getContents());
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.teams.use")) {
            this.send(player, "not-officer", new String[0]);
            return true;
         } else if (args.length == 0) {
            this.openGui(player);
            return true;
         } else {
            String s = args[0].toLowerCase(Locale.ROOT);

            return switch (s) {
               case "gui", "menu" -> {
                  this.openGui(player);
                  yield true;
               }
               case "create" -> {
                  if (args.length < 2) {
                     this.beginCreateNameInput(player);
                     yield true;
                  } else {
                     this.handleCreate(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                     yield true;
                  }
               }
               case "invite" -> {
                  if (args.length < 2) {
                     this.send(player, "usage-invite", new String[0]);
                     yield true;
                  } else {
                     this.handleInvite(player, args[1]);
                     yield true;
                  }
               }
               case "accept" -> {
                  this.handleAccept(player, args.length >= 2 ? args[1] : null);
                  yield true;
               }
               case "kick" -> {
                  if (args.length < 2) {
                     this.send(player, "usage-kick", new String[0]);
                     yield true;
                  } else {
                     this.handleKick(player, args[1]);
                     yield true;
                  }
               }
               case "leave" -> {
                  this.handleLeave(player);
                  yield true;
               }
               case "disband" -> {
                  this.handleDisband(player);
                  yield true;
               }
               case "promote" -> {
                  if (args.length < 2) {
                     this.send(player, "usage-promote", new String[0]);
                     yield true;
                  } else {
                     this.handlePromote(player, args[1]);
                     yield true;
                  }
               }
               case "demote" -> {
                  if (args.length < 2) {
                     this.send(player, "usage-demote", new String[0]);
                     yield true;
                  } else {
                     this.handleDemote(player, args[1]);
                     yield true;
                  }
               }
               case "members" -> {
                  this.handleMembers(player);
                  yield true;
               }
               case "stats" -> {
                  this.handleStats(player);
                  yield true;
               }
               case "leaderboard", "top" -> {
                  this.handleLeaderboard(player);
                  yield true;
               }
               case "emergency", "help", "sos" -> {
                  this.handleEmergency(player);
                  yield true;
               }
               case "ally" -> {
                  if (args.length < 2) {
                     this.send(player, "usage-ally", new String[0]);
                     yield true;
                  } else {
                     if (args[1].equalsIgnoreCase("accept")) {
                        this.handleAllyAccept(player);
                     } else {
                        this.handleAllyRequest(player, args[1]);
                     }

                     yield true;
                  }
               }
               case "chat" -> {
                  this.toggleTeamChat(player);
                  yield true;
               }
               case "home" -> {
                  this.handleHome(player);
                  yield true;
               }
               case "sethome" -> {
                  this.handleSetHome(player);
                  yield true;
               }
               case "enderchest", "ec" -> {
                  this.handleEnderchest(player);
                  yield true;
               }
               case "pvp" -> {
                  this.handlePvpToggle(player);
                  yield true;
               }
               case "name", "rename" -> {
                  if (args.length >= 2) {
                     this.handleRename(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                  } else {
                     this.beginRenameInput(player);
                  }

                  yield true;
               }
               default -> {
                  this.send(player, "usage", new String[0]);
                  yield true;
               }
            };
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void openGui(Player player) {
      this.guiHandler.openFor(player);
   }

   void openEnderchestGui(Player player) {
      this.guiHandler.openEnderchest(player);
   }

   void openSettingsGui(Player player) {
      this.guiHandler.openSettings(player);
   }

   private void handleCreate(Player player, String name) {
      if (this.validateTeamName(player, name.trim(), false)) {
         this.guiHandler.openCreateConfirm(player, name.trim());
      }
   }

   private void handleInvite(Player player, String targetName) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member == null || teamdatabase$member.role() > 1) {
            this.send(player, "not-officer", new String[0]);
         } else if (this.database.getMembers(integer).size() >= this.config.getInt("creation.max-members", 18)) {
            this.send(player, "team-full", new String[0]);
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(targetName);
            if (offlineplayer == null || offlineplayer.getUniqueId() == null) {
               this.send(player, "player-missing", new String[0]);
            } else if (this.database.getTeamId(offlineplayer.getUniqueId()) != null) {
               this.send(player, "target-in-team", new String[]{"%player%", this.name(offlineplayer)});
            } else {
               long i = System.currentTimeMillis() + this.config.getLong("invites.lifetime-seconds", 120L) * 1000L;
               this.database.addInvite(integer, offlineplayer.getUniqueId(), player.getUniqueId(), i);
               TeamDatabase.Team teamdatabase$team = this.database.getTeamById(integer);
               this.send(player, "invited", new String[]{"%player%", this.name(offlineplayer)});
               if (offlineplayer.isOnline() && offlineplayer.getPlayer() != null && teamdatabase$team != null) {
                  this.sendInviteMessage(offlineplayer.getPlayer(), teamdatabase$team.name(), player.getName());
               }
            }
         }
      }
   }

   private void sendInviteMessage(Player target, String teamName, String inviterName) {
      Component component = Text.c(this.raw("invite-received", new String[]{"%team%", teamName, "%player%", inviterName}));
      Component component1 = Text.c(this.raw("usage-accept", new String[0]))
         .clickEvent(ClickEvent.runCommand("/team accept " + teamName))
         .hoverEvent(HoverEvent.showText(Text.c(this.raw("accepted", new String[]{"%team%", teamName}))));
      target.sendMessage(((TextComponent)((TextComponent)Component.empty().append(component)).append(Component.newline())).append(component1));
   }

   private void handleAccept(Player player, String teamName) {
      TeamDatabase.Invite teamdatabase$invite = null;
      if (teamName != null && !teamName.isBlank()) {
         TeamDatabase.Team teamdatabase$team = this.database.getTeamByName(teamName);
         if (teamdatabase$team != null) {
            teamdatabase$invite = this.database.getInvite(player.getUniqueId(), teamdatabase$team.id());
         }
      } else {
         teamdatabase$invite = this.database.getLatestInvite(player.getUniqueId());
      }

      if (teamdatabase$invite == null || teamdatabase$invite.expiresAt() < System.currentTimeMillis()) {
         Integer integer = this.database.getTeamId(player.getUniqueId());
         if (integer == null) {
            this.send(player, "no-invite", new String[0]);
         } else {
            TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
            if (teamdatabase$member != null && teamdatabase$member.role() <= 1) {
               this.handleAllyAccept(player);
            } else {
               this.send(player, "not-officer", new String[0]);
            }
         }
      } else if (this.database.getTeamId(player.getUniqueId()) != null) {
         this.send(player, "already-in-team", new String[0]);
      } else {
         TeamDatabase.Team teamdatabase$team1 = this.database.getTeamById(teamdatabase$invite.teamId());
         if (teamdatabase$team1 == null) {
            this.send(player, "no-invite", new String[0]);
         } else if (this.database.getMembers(teamdatabase$invite.teamId()).size() >= this.config.getInt("creation.max-members", 18)) {
            this.send(player, "team-full", new String[0]);
         } else {
            this.database.addMember(teamdatabase$invite.teamId(), player.getUniqueId(), 2);
            this.database.removeInvite(teamdatabase$invite.teamId(), player.getUniqueId());
            this.send(player, "accepted", new String[]{"%team%", teamdatabase$team1.name()});
         }
      }
   }

   private void handleKick(Player player, String targetName) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member != null && teamdatabase$member.role() <= 1) {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(targetName);
            TeamDatabase.Member teamdatabase$member1 = this.database.getMember(integer, offlineplayer.getUniqueId());
            if (teamdatabase$member1 == null) {
               this.send(player, "not-member", new String[0]);
            } else if (teamdatabase$member1.role() <= teamdatabase$member.role()
               && !player.getUniqueId().equals(this.database.getTeamById(integer).leaderUuid())) {
               this.send(player, "not-officer", new String[0]);
            } else if (offlineplayer.getUniqueId().equals(player.getUniqueId())) {
               this.send(player, "not-member", new String[0]);
            } else {
               this.database.removeMember(integer, offlineplayer.getUniqueId());
               this.send(player, "kicked", new String[]{"%player%", this.name(offlineplayer)});
               if (offlineplayer.isOnline() && offlineplayer.getPlayer() != null) {
                  this.send(offlineplayer.getPlayer(), "kicked-target", new String[0]);
               }
            }
         } else {
            this.send(player, "not-officer", new String[0]);
         }
      }
   }

   void handleLeave(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Team teamdatabase$team = this.database.getTeamById(integer);
         if (teamdatabase$team != null && teamdatabase$team.leaderUuid().equals(player.getUniqueId())) {
            this.send(player, "leader-leave", new String[0]);
         } else {
            this.database.removeMember(integer, player.getUniqueId());
            this.teamChatMode.remove(player.getUniqueId());
            this.send(player, "left", new String[0]);
         }
      }
   }

   void handleDisband(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Team teamdatabase$team = this.database.getTeamById(integer);
         if (teamdatabase$team != null && teamdatabase$team.leaderUuid().equals(player.getUniqueId())) {
            for (TeamDatabase.Member teamdatabase$member : this.database.getMembers(integer)) {
               this.teamChatMode.remove(teamdatabase$member.uuid());
            }

            this.database.deleteTeam(integer);
            this.send(player, "disbanded", new String[]{"%team%", teamdatabase$team.name()});
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7F, 0.9F);
         } else {
            this.send(player, "not-leader", new String[0]);
         }
      }
   }

   private void handlePromote(Player player, String targetName) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         if (!this.isLeader(player, integer)) {
            this.send(player, "not-leader", new String[0]);
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(targetName);
            TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, offlineplayer.getUniqueId());
            if (teamdatabase$member == null) {
               this.send(player, "not-member", new String[0]);
            } else if (teamdatabase$member.role() <= 1) {
               this.send(player, "failed", new String[0]);
            } else {
               this.database.setRole(integer, offlineplayer.getUniqueId(), 1);
               this.send(player, "promoted", new String[]{"%player%", this.name(offlineplayer)});
            }
         }
      }
   }

   private void handleDemote(Player player, String targetName) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         if (!this.isLeader(player, integer)) {
            this.send(player, "not-leader", new String[0]);
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(targetName);
            TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, offlineplayer.getUniqueId());
            if (teamdatabase$member == null) {
               this.send(player, "not-member", new String[0]);
            } else if (teamdatabase$member.role() == 0) {
               this.send(player, "not-leader", new String[0]);
            } else if (teamdatabase$member.role() == 2) {
               this.send(player, "not-member", new String[0]);
            } else {
               this.database.setRole(integer, offlineplayer.getUniqueId(), 2);
               this.send(player, "demoted", new String[]{"%player%", this.name(offlineplayer)});
            }
         }
      }
   }

   private void handleMembers(Player player) {
      this.guiHandler.openMembers(player);
   }

   void handleStats(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Team teamdatabase$team = this.database.getTeamById(integer);
         long i = 0L;
         int j = 0;
         long k = 0L;
         TokenService tokenservice = this.plugin.modules().tokens();

         for (TeamDatabase.Member teamdatabase$member : this.database.getMembers(integer)) {
            j += teamdatabase$member.kills();
            k += teamdatabase$member.playtimeMs();
            if (tokenservice != null) {
               i += tokenservice.getBalance(teamdatabase$member.uuid());
            }
         }

         if (teamdatabase$team != null) {
            this.send(player, "created", new String[]{"%team%", teamdatabase$team.name()});
            player.sendMessage(
               Text.c(
                  this.raw("prefix", new String[0])
                     + " &fTokens: &#BEFF00"
                     + i
                     + " &8| &fKills: &#FF0000"
                     + j
                     + " &8| &fPlaytime: &#FF007C"
                     + this.formatPlaytime(k)
               )
            );
         }
      }
   }

   private void handleLeaderboard(Player player) {
      List<TeamDatabase.Team> list = this.database.listTeams();
      if (list.isEmpty()) {
         player.sendMessage(Text.c(this.raw("prefix", new String[0]) + " &fNo teams yet."));
      } else {
         list.sort((a, b) -> Integer.compare(this.database.getMembers(b.id()).size(), this.database.getMembers(a.id()).size()));
         player.sendMessage(Text.c(this.raw("prefix", new String[0]) + " &#FF0067&lTEAM TOP"));
         int i = Math.min(10, list.size());

         for (int j = 0; j < i; j++) {
            TeamDatabase.Team teamdatabase$team = list.get(j);
            player.sendMessage(
               Text.c(
                  "&8• &#FF0067#"
                     + (j + 1)
                     + " &f"
                     + teamdatabase$team.name()
                     + " &8— &f"
                     + this.database.getMembers(teamdatabase$team.id()).size()
                     + " members"
               )
            );
         }
      }
   }

   void handleEmergency(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         long i = this.config.getLong("emergency.cooldown-seconds", 60L) * 1000L;
         long j = System.currentTimeMillis();
         Long olong = this.emergencyCooldown.get(player.getUniqueId());
         if (olong != null && j - olong < i) {
            this.send(player, "emergency-cooldown", new String[0]);
         } else {
            this.emergencyCooldown.put(player.getUniqueId(), j);
            Location location = player.getLocation();
            String s = (location.getWorld() == null ? "?" : location.getWorld().getName())
               + " "
               + location.getBlockX()
               + " "
               + location.getBlockY()
               + " "
               + location.getBlockZ();
            String s1 = this.raw("emergency", new String[]{"%player%", player.getName(), "%location%", s});

            for (TeamDatabase.Member teamdatabase$member : this.database.getMembers(integer)) {
               Player playerx = Bukkit.getPlayer(teamdatabase$member.uuid());
               if (playerx != null && !playerx.equals(player)) {
                  playerx.sendMessage(Text.c(s1));
               }
            }
         }
      }
   }

   private void handleAllyRequest(Player player, String teamName) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member == null || teamdatabase$member.role() > 1) {
            this.send(player, "not-officer", new String[0]);
         } else if (this.database.allyCount(integer) >= this.config.getInt("ally.max-allies", 1)) {
            this.send(player, "ally-limit", new String[0]);
         } else {
            TeamDatabase.Team teamdatabase$team = this.database.getTeamByName(teamName);
            if (teamdatabase$team == null) {
               this.send(player, "unknown-team", new String[0]);
            } else if (teamdatabase$team.id() == integer) {
               this.send(player, "already-allied", new String[0]);
            } else if (this.database.getAllies(integer).contains(teamdatabase$team.id())) {
               this.send(player, "already-allied", new String[0]);
            } else if (this.database.allyCount(teamdatabase$team.id()) >= this.config.getInt("ally.max-allies", 1)) {
               this.send(player, "ally-limit", new String[0]);
            } else {
               long i = System.currentTimeMillis() + this.config.getLong("ally.request-lifetime-seconds", 120L) * 1000L;
               this.database.addAllyRequest(integer, teamdatabase$team.id(), i);
               this.send(player, "ally-sent", new String[]{"%team%", teamdatabase$team.name()});
            }
         }
      }
   }

   private void handleAllyAccept(Player player) {
      Integer integer = this.database.getTeamId(player.getUniqueId());
      if (integer == null) {
         this.send(player, "not-in-team", new String[0]);
      } else {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member != null && teamdatabase$member.role() <= 1) {
            TeamDatabase.AllyRequest teamdatabase$allyrequest = this.database.getIncomingAllyRequest(integer);
            if (teamdatabase$allyrequest == null) {
               this.send(player, "no-invite", new String[0]);
            } else if (this.database.allyCount(integer) < this.config.getInt("ally.max-allies", 1)
               && this.database.allyCount(teamdatabase$allyrequest.fromTeamId()) < this.config.getInt("ally.max-allies", 1)) {
               this.database.addAllyPair(integer, teamdatabase$allyrequest.fromTeamId());
               this.database.removeAllyRequest(teamdatabase$allyrequest.fromTeamId(), teamdatabase$allyrequest.toTeamId());
               TeamDatabase.Team teamdatabase$team = this.database.getTeamById(teamdatabase$allyrequest.fromTeamId());
               this.send(player, "ally-accepted", new String[]{"%team%", teamdatabase$team == null ? "?" : teamdatabase$team.name()});
            } else {
               this.send(player, "ally-limit", new String[0]);
               this.database.removeAllyRequest(teamdatabase$allyrequest.fromTeamId(), teamdatabase$allyrequest.toTeamId());
            }
         } else {
            this.send(player, "not-officer", new String[0]);
         }
      }
   }

   void toggleTeamChat(Player player) {
      if (this.database.getTeamId(player.getUniqueId()) == null) {
         this.send(player, "not-in-team", new String[0]);
      } else {
         if (this.teamChatMode.contains(player.getUniqueId())) {
            this.teamChatMode.remove(player.getUniqueId());
            this.send(player, "chat-off", new String[0]);
         } else {
            this.teamChatMode.add(player.getUniqueId());
            this.send(player, "chat-on", new String[0]);
         }
      }
   }

   void handleHome(final Player player) {
      final Integer integer = this.requireTeam(player);
      if (integer != null) {
         Location location = this.database.getHome(integer);
         if (location == null) {
            this.send(player, "no-home", new String[0]);
         } else if (this.pendingHomes.containsKey(player.getUniqueId())) {
            this.send(player, "already-teleporting", new String[0]);
         } else {
            CombatModule combatmodule = this.plugin.modules().get(CombatModule.class);
            if (combatmodule != null && combatmodule.isTagged(player)) {
               this.send(player, "in-combat", new String[0]);
            } else {
               final int i = Math.max(0, this.config.getInt("home.delay-seconds", 5));
               if (i == 0) {
                  player.teleport(location);
                  this.send(player, "home-teleport", new String[0]);
               } else {
                  this.send(player, "home-countdown", new String[]{"%seconds%", String.valueOf(i)});
                  BukkitRunnable bukkitrunnable = new BukkitRunnable() {
                     private int remaining = i;

                     public void run() {
                        if (!player.isOnline()) {
                           TeamsModule.this.pendingHomes.remove(player.getUniqueId());
                           this.cancel();
                        } else if (this.remaining <= 0) {
                           TeamsModule.this.pendingHomes.remove(player.getUniqueId());
                           this.cancel();
                           Location location1 = TeamsModule.this.database.getHome(integer);
                           if (location1 == null) {
                              TeamsModule.this.send(player, "no-home", new String[0]);
                           } else {
                              player.teleport(location1);
                              TeamsModule.this.send(player, "home-teleport", new String[0]);
                           }
                        } else {
                           this.remaining--;
                        }
                     }
                  };
                  BukkitTask bukkittask = bukkitrunnable.runTaskTimer(this.plugin, 20L, 20L);
                  this.pendingHomes.put(player.getUniqueId(), bukkittask);
               }
            }
         }
      }
   }

   void handleSetHome(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member != null && teamdatabase$member.role() <= 1) {
            this.database.setHome(integer, player.getLocation());
            this.send(player, "home-set", new String[0]);
         } else {
            this.send(player, "not-officer", new String[0]);
         }
      }
   }

   void handleEnderchest(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         this.guiHandler.openEnderchest(player);
      }
   }

   void handlePvpToggle(Player player) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         TeamDatabase.Member teamdatabase$member = this.database.getMember(integer, player.getUniqueId());
         if (teamdatabase$member != null && teamdatabase$member.role() <= 1) {
            boolean flag = !this.database.isPvp(integer);
            this.database.setPvp(integer, flag);
            this.send(player, flag ? "pvp-on" : "pvp-off", new String[0]);
         } else {
            this.send(player, "not-officer", new String[0]);
         }
      }
   }

   void handleRename(Player player, String name) {
      Integer integer = this.requireTeam(player);
      if (integer != null) {
         if (!this.isLeader(player, integer)) {
            this.send(player, "not-leader", new String[0]);
         } else if (this.validateTeamName(player, name, true)) {
            if (this.database.renameTeam(integer, name.trim())) {
               this.send(player, "renamed", new String[]{"%team%", name.trim()});
            } else {
               this.send(player, "failed", new String[0]);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      if (this.awaitingTeamName.contains(player.getUniqueId())) {
         event.setCancelled(true);
         String s4 = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
         this.awaitingTeamName.remove(player.getUniqueId());
         if (s4.equalsIgnoreCase("cancel")) {
            this.send(player, "usage", new String[0]);
         } else {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
               if (this.validateTeamName(player, s4, false)) {
                  this.guiHandler.openCreateConfirm(player, s4);
               }
            });
         }
      } else if (this.awaitingRename.contains(player.getUniqueId())) {
         event.setCancelled(true);
         String s3 = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
         this.awaitingRename.remove(player.getUniqueId());
         if (s3.equalsIgnoreCase("cancel")) {
            this.send(player, "usage", new String[0]);
         } else {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.handleRename(player, s3));
         }
      } else if (this.teamChatMode.contains(player.getUniqueId())) {
         Integer integer = this.database.getTeamId(player.getUniqueId());
         if (integer == null) {
            this.teamChatMode.remove(player.getUniqueId());
         } else {
            event.setCancelled(true);
            String s = PlainTextComponentSerializer.plainText().serialize(event.message());
            if (!s.isBlank()) {
               String s1 = this.config.getString("chat.team-format", "&#FCFF00&lTEAM &8▷ &r&f%player% &8» &7%message%");
               String s2 = Text.apply(s1, "%player%", player.getName(), "%message%", s);
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                  for (TeamDatabase.Member teamdatabase$member : this.database.getMembers(integer)) {
                     Player player1 = Bukkit.getPlayer(teamdatabase$member.uuid());
                     if (player1 != null) {
                        player1.sendMessage(Text.c(s2));
                     }
                  }

                  for (int i : this.database.getAllies(integer)) {
                     for (TeamDatabase.Member teamdatabase$member1 : this.database.getMembers(i)) {
                        Player player2 = Bukkit.getPlayer(teamdatabase$member1.uuid());
                        if (player2 != null) {
                           String s5 = this.config.getString("chat.ally-format", "&#0098FF&lALLIES &8▷ &r&f%player% &8» &7%message%");
                           player2.sendMessage(Text.c(Text.apply(s5, "%player%", player.getName(), "%message%", s)));
                        }
                     }
                  }
               });
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFriendlyFire(EntityDamageByEntityEvent event) {
      if (this.database != null) {
         if (event.getEntity() instanceof Player player) {
            Player player1 = this.damagingPlayer(event.getDamager());
            if (player1 != null && !player1.equals(player)) {
               Integer integer = this.database.getTeamId(player.getUniqueId());
               Integer integer1 = this.database.getTeamId(player1.getUniqueId());
               if (integer != null && integer1 != null && integer.equals(integer1)) {
                  if (!this.database.isPvp(integer)) {
                     event.setCancelled(true);
                  }
               }
            }
         }
      }
   }

   private Player damagingPlayer(Entity damager) {
      if (damager instanceof Player) {
         return (Player)damager;
      } else {
         if (damager instanceof Projectile projectile) {
            ProjectileSource projectilesource = projectile.getShooter();
            if (projectilesource instanceof Player) {
               return (Player)projectilesource;
            }
         }

         return null;
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHomeMove(PlayerMoveEvent event) {
      if (this.config.getBoolean("home.cancel-on-move", true)) {
         if (this.pendingHomes.containsKey(event.getPlayer().getUniqueId()) && event.getTo() != null) {
            Location location = event.getFrom();
            Location location1 = event.getTo();
            if (location.getBlockX() != location1.getBlockX() || location.getBlockY() != location1.getBlockY() || location.getBlockZ() != location1.getBlockZ()
               )
             {
               BukkitTask bukkittask = this.pendingHomes.remove(event.getPlayer().getUniqueId());
               if (bukkittask != null) {
                  bukkittask.cancel();
                  this.send(event.getPlayer(), "home-cancel", new String[0]);
               }
            }
         }
      }
   }

   @EventHandler
   public void onKill(PlayerDeathEvent event) {
      Player player = event.getEntity().getKiller();
      if (player != null && this.database != null) {
         if (this.database.getTeamId(player.getUniqueId()) != null) {
            this.database.incrementKills(player.getUniqueId());
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.onlineSince.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.flushPlaytime(uuid);
      this.teamChatMode.remove(uuid);
      this.awaitingTeamName.remove(uuid);
      this.awaitingRename.remove(uuid);
      this.onlineSince.remove(uuid);
      this.emergencyCooldown.remove(uuid);
      BukkitTask bukkittask = this.pendingHomes.remove(uuid);
      if (bukkittask != null) {
         bukkittask.cancel();
      }
   }

   private void flushPlaytime(UUID uuid) {
      Long olong = this.onlineSince.get(uuid);
      if (olong != null && this.database != null) {
         if (this.database.getTeamId(uuid) != null) {
            this.database.addPlaytime(uuid, System.currentTimeMillis() - olong);
         }
      }
   }

   private void flushPlaytimeAll() {
      long i = System.currentTimeMillis();

      for (Entry<UUID, Long> entry : new HashMap<>(this.onlineSince).entrySet()) {
         if (this.database.getTeamId(entry.getKey()) != null) {
            this.database.addPlaytime(entry.getKey(), i - entry.getValue());
         }
      }
   }

   private Integer requireTeam(Player player) {
      Integer integer = this.database.getTeamId(player.getUniqueId());
      if (integer == null) {
         this.send(player, "not-in-team", new String[0]);
      }

      return integer;
   }

   private boolean isLeader(Player player, int teamId) {
      TeamDatabase.Team teamdatabase$team = this.database.getTeamById(teamId);
      return teamdatabase$team != null && teamdatabase$team.leaderUuid().equals(player.getUniqueId());
   }

   private String name(OfflinePlayer player) {
      return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!(sender instanceof Player player) || !player.hasPermission("sharded.teams.use")) {
         return List.of();
      }

      if (args.length == 1) {
         return TabCompleteHelper.filter(
            args[0],
            "create",
            "invite",
            "accept",
            "kick",
            "leave",
            "disband",
            "promote",
            "demote",
            "members",
            "stats",
            "leaderboard",
            "emergency",
            "ally",
            "chat",
            "gui",
            "home",
            "sethome",
            "enderchest",
            "pvp",
            "name"
         );
      } else {
         if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("invite") || s.equals("kick") || s.equals("promote") || s.equals("demote")) {
               return TabCompleteHelper.onlinePlayers(args[1]);
            }

            if (s.equals("ally")) {
               List<String> list = this.database.listTeams().stream().map(TeamDatabase.Team::name).collect(Collectors.toCollection(ArrayList::new));
               list.add("accept");
               return TabCompleteHelper.filter(args[1], list);
            }

            if (s.equals("accept")) {
               return TabCompleteHelper.filter(args[1], this.database.listTeams().stream().map(TeamDatabase.Team::name).collect(Collectors.toList()));
            }

            if (s.equals("create") || s.equals("name") || s.equals("rename")) {
               return List.of("<name>");
            }
         }

         return List.of();
      }
   }
}
