package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.abilities.AbilitiesShopModule;
import com.sharded.core.modules.announce.AnnounceModule;
import com.sharded.core.modules.armortrims.ArmorTrimsModule;
import com.sharded.core.modules.autosmelt.AutoSmeltModule;
import com.sharded.core.modules.backpack.BackpackModule;
import com.sharded.core.modules.bundles.BundlesModule;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.chatformat.ChatFormatModule;
import com.sharded.core.modules.chatcolor.ChatColorModule;
import com.sharded.core.modules.chatmoderation.ChatModerationModule;
import com.sharded.core.modules.client.ClientModule;
import com.sharded.core.modules.collisions.CollisionsModule;
import com.sharded.core.modules.combat.CombatModule;
import com.sharded.core.modules.coreprotect.CoreProtectModule;
import com.sharded.core.modules.craft.CraftModule;
import com.sharded.core.modules.crates.CratesModule;
import com.sharded.core.modules.dailyrewards.DailyRewardsModule;
import com.sharded.core.modules.deathmessages.DeathMessagesModule;
import com.sharded.core.modules.duel.DuelModule;
import com.sharded.core.modules.eglow.EGlowModule;
import com.sharded.core.modules.fix.FixModule;
import com.sharded.core.modules.fly.FlyModule;
import com.sharded.core.modules.graves.GravesModule;
import com.sharded.core.modules.guide.GuideModule;
import com.sharded.core.modules.homes.HomesModule;
import com.sharded.core.modules.kits.KitsModule;
import com.sharded.core.modules.stats.StatsModule;
import com.sharded.core.modules.invrollback.InvRollbackModule;
import com.sharded.core.modules.joinmessages.JoinMessagesModule;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.modules.media.MediaModule;
import com.sharded.core.modules.multiverse.MultiverseModule;
import com.sharded.core.modules.namecolor.NameColorModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.modules.pets.PetsModule;
import com.sharded.core.modules.pickupmobs.PickupMobsModule;
import com.sharded.core.modules.pickupspawners.PickupSpawnersModule;
import com.sharded.core.modules.portalrtp.PortalRtpModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.modules.requeststaff.RequestStaffModule;
import com.sharded.core.modules.rules.RulesModule;
import com.sharded.core.modules.rewards.RewardsModule;
import com.sharded.core.modules.roles.RolesModule;
import com.sharded.core.modules.screenshare.ScreenshareModule;
import com.sharded.core.modules.settings.SettingsModule;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.modules.staff.StaffModule;
import com.sharded.core.modules.staffchat.StaffChatModule;
import com.sharded.core.modules.tags.TagsModule;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tempranks.TempranksModule;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.modules.toolname.ToolNameModule;
import com.sharded.core.modules.trash.TrashModule;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.modules.weeklyrewards.WeeklyRewardsModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
   private final ShardedCore plugin;
   private final Map<String, Module> modules = new LinkedHashMap<>();

   public ModuleManager(ShardedCore plugin) {
      this.plugin = plugin;
      this.register(new CraftModule(plugin));
      this.register(new FixModule(plugin));
      this.register(new TrashModule(plugin));
      this.register(new ChatToggleModule(plugin));
      this.register(new ChatFormatModule(plugin));
      this.register(new PrivateMessagesModule(plugin));
      this.register(new NightVisionModule(plugin));
      this.register(new DeathMessagesModule(plugin));
      this.register(new JoinMessagesModule(plugin));
      this.register(new BackpackModule(plugin));
      this.register(new GravesModule(plugin));
      this.register(new ArmorTrimsModule(plugin));
      this.register(new FlyModule(plugin));
      this.register(new AutoSmeltModule(plugin));
      this.register(new PortalRtpModule(plugin));
      this.register(new SpawnSelectModule(plugin));
      this.register(new MultiverseModule(plugin));
      this.register(new SettingsModule(plugin));
      this.register(new KillstreaksModule(plugin));
      this.register(new PetsModule(plugin));
      this.register(new TempranksModule(plugin));
      this.register(new AbilitiesShopModule(plugin));
      this.register(new PickupMobsModule(plugin));
      this.register(new PickupSpawnersModule(plugin));
      this.register(new TokensModule(plugin));
      this.register(new EGlowModule(plugin));
      this.register(new TagsModule(plugin));
      this.register(new ChatColorModule(plugin));
      this.register(new NameColorModule(plugin));
      this.register(new WardrobeModule(plugin));
      this.register(new com.sharded.core.modules.cold.ColdModule(plugin));
      this.register(new com.sharded.core.modules.itemshop.ItemShopModule(plugin));
      this.register(new ToolNameModule(plugin));
      this.register(new BundlesModule(plugin));
      this.register(new StaffModule(plugin));
      this.register(new PunishmentsModule(plugin));
      this.register(new ChatModerationModule(plugin));
      this.register(new AnnounceModule(plugin));
      this.register(new InvRollbackModule(plugin));
      this.register(new ScreenshareModule(plugin));
      this.register(new StaffChatModule(plugin));
      this.register(new RequestStaffModule(plugin));
      this.register(new GuideModule(plugin));
      this.register(new RulesModule(plugin));
      this.register(new ClientModule(plugin));
      this.register(new CollisionsModule(plugin));
      this.register(new TeamsModule(plugin));
      this.register(new DailyRewardsModule(plugin));
      this.register(new WeeklyRewardsModule(plugin));
      this.register(new RewardsModule(plugin));
      this.register(new LeaderboardsModule(plugin));
      this.register(new MediaModule(plugin));
      this.register(new CratesModule(plugin));
      this.register(new StatsModule(plugin));
      this.register(new HomesModule(plugin));
      this.register(new KitsModule(plugin));
      this.register(new RolesModule(plugin));
      this.register(new OutpostModule(plugin));
      this.register(new KothModule(plugin));
      this.register(new CoreProtectModule(plugin));
      this.register(new DuelModule(plugin));
      this.register(new CombatModule(plugin));
   }

   public void register(Module module) {
      this.modules.put(module.id(), module);
   }

   public Collection<Module> allModules() {
      return this.modules.values();
   }

   public void enableModules() {
      for (Module module : this.modules.values()) {
         if (!this.isModuleEnabled(module.id())) {
            this.plugin.getLogger().info("Module '" + module.id() + "' is disabled in config.yml.");
         } else {
            try {
               module.enable();
               this.plugin.getLogger().info("Enabled module '" + module.id() + "'.");
            } catch (Exception exception) {
               this.plugin.getLogger().severe("Failed to enable module '" + module.id() + "': " + exception);
               exception.printStackTrace();
            }
         }
      }
   }

   public void disableModules() {
      List<Module> list = new ArrayList<>(this.modules.values());
      Collections.reverse(list);

      for (Module module : list) {
         try {
            module.disable();
         } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to disable module '" + module.id() + "': " + exception);
         }
      }
   }

   public void reload() {
      for (Module module : new ArrayList<>(this.modules.values())) {
         boolean want = this.isModuleEnabled(module.id());
         try {
            if (want) {
               if (!module.isEnabled()) {
                  module.enable();
                  this.plugin.getLogger().info("Enabled module '" + module.id() + "'.");
               } else {
                  module.reload();
               }
            } else if (module.isEnabled()) {
               module.disable();
               this.plugin.getLogger().info("Disabled module '" + module.id() + "'.");
            }
         } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to reload module '" + module.id() + "': " + exception);
            exception.printStackTrace();
         }
      }
   }

   public int enabledCount() {
      return (int)this.modules.values().stream().filter(Module::isEnabled).count();
   }

   public <T extends Module> T get(Class<T> type) {
      for (Module module : this.modules.values()) {
         if (type.isInstance(module)) {
            return (T)module;
         }
      }

      return null;
   }

   public TokenService tokens() {
      TokensModule tokensmodule = this.get(TokensModule.class);
      return tokensmodule == null ? null : tokensmodule.service();
   }

   private boolean isModuleEnabled(String id) {
      if ("coreprotect".equals(id)) {
         return this.plugin.getConfig().contains("modules.coreprotect")
            ? this.plugin.getConfig().getBoolean("modules.coreprotect")
            : this.plugin.getConfig().getBoolean("modules.protect", true) || this.plugin.getConfig().getBoolean("modules.arena", true);
      } else {
         return this.plugin.getConfig().getBoolean("modules." + id, true);
      }
   }

   public boolean isConfiguredEnabled(String id) {
      return this.isModuleEnabled(id);
   }
}
