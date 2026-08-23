package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.bundles.BundlesModule;
import com.sharded.core.modules.armortrims.ArmorTrimsModule;
import com.sharded.core.modules.autosmelt.AutoSmeltModule;
import com.sharded.core.modules.backpack.BackpackModule;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.craft.CraftModule;
import com.sharded.core.modules.deathmessages.DeathMessagesModule;
import com.sharded.core.modules.fix.FixModule;
import com.sharded.core.modules.fly.FlyModule;
import com.sharded.core.modules.graves.GravesModule;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.modules.toolname.ToolNameModule;
import com.sharded.core.modules.trash.TrashModule;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.pickupmobs.PickupMobsModule;
import com.sharded.core.modules.pickupspawners.PickupSpawnersModule;
import com.sharded.core.modules.portalrtp.PortalRtpModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.modules.settings.SettingsModule;
import com.sharded.core.modules.joinmessages.JoinMessagesModule;
import com.sharded.core.modules.pets.PetsModule;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.modules.eglow.EGlowModule;
import com.sharded.core.modules.tags.TagsModule;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.modules.chatcolor.ChatColorModule;
import com.sharded.core.modules.namecolor.NameColorModule;
import com.sharded.core.modules.abilities.AbilitiesShopModule;
import com.sharded.core.modules.tempranks.TempranksModule;
import com.sharded.core.modules.announce.AnnounceModule;
import com.sharded.core.modules.chatmoderation.ChatModerationModule;
import com.sharded.core.modules.invrollback.InvRollbackModule;
import com.sharded.core.modules.staff.StaffModule;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.modules.requeststaff.RequestStaffModule;
import com.sharded.core.modules.screenshare.ScreenshareModule;
import com.sharded.core.modules.staffchat.StaffChatModule;
import com.sharded.core.modules.guide.GuideModule;
import com.sharded.core.modules.client.ClientModule;
import com.sharded.core.modules.collisions.CollisionsModule;
import com.sharded.core.modules.dailyrewards.DailyRewardsModule;
import com.sharded.core.modules.weeklyrewards.WeeklyRewardsModule;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.modules.media.MediaModule;
import com.sharded.core.modules.crates.CratesModule;
import com.sharded.core.modules.arena.ArenaModule;
import com.sharded.core.modules.combat.CombatModule;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.modules.protect.ProtectModule;
import com.sharded.core.modules.roles.RolesModule;
import com.sharded.core.modules.teams.TeamsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {

    private final ShardedCore plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(ShardedCore plugin) {
        this.plugin = plugin;
        register(new CraftModule(plugin));
        register(new FixModule(plugin));
        register(new TrashModule(plugin));
        register(new ChatToggleModule(plugin));
        register(new PrivateMessagesModule(plugin));
        register(new NightVisionModule(plugin));
        register(new DeathMessagesModule(plugin));
        register(new JoinMessagesModule(plugin));
        register(new BackpackModule(plugin));
        register(new GravesModule(plugin));
        register(new ArmorTrimsModule(plugin));
        register(new FlyModule(plugin));
        register(new AutoSmeltModule(plugin));
        register(new PortalRtpModule(plugin));
        register(new SpawnSelectModule(plugin));
        register(new SettingsModule(plugin));
        register(new KillstreaksModule(plugin));
        register(new PetsModule(plugin));
        register(new TempranksModule(plugin));
        register(new AbilitiesShopModule(plugin));
        register(new PickupMobsModule(plugin));
        register(new PickupSpawnersModule(plugin));
        register(new TokensModule(plugin));
        register(new EGlowModule(plugin));
        register(new TagsModule(plugin));
        register(new ChatColorModule(plugin));
        register(new NameColorModule(plugin));
        register(new WardrobeModule(plugin));
        register(new ToolNameModule(plugin));
        register(new BundlesModule(plugin));
        register(new StaffModule(plugin));
        register(new PunishmentsModule(plugin));
        register(new ChatModerationModule(plugin));
        register(new AnnounceModule(plugin));
        register(new InvRollbackModule(plugin));
        register(new ScreenshareModule(plugin));
        register(new StaffChatModule(plugin));
        register(new RequestStaffModule(plugin));
        register(new GuideModule(plugin));
        register(new ClientModule(plugin));
        register(new CollisionsModule(plugin));
        register(new TeamsModule(plugin));
        register(new DailyRewardsModule(plugin));
        register(new WeeklyRewardsModule(plugin));
        register(new LeaderboardsModule(plugin));
        register(new MediaModule(plugin));
        register(new CratesModule(plugin));
        register(new RolesModule(plugin));
        register(new OutpostModule(plugin));
        register(new KothModule(plugin));
        register(new ProtectModule(plugin));
        register(new CombatModule(plugin));
        register(new ArenaModule(plugin));
    }

    private void register(Module module) {
        modules.put(module.id(), module);
    }

    public Collection<Module> allModules() {
        return modules.values();
    }

    public void enableModules() {
        for (Module module : modules.values()) {
            if (!plugin.getConfig().getBoolean("modules." + module.id(), true)) {
                plugin.getLogger().info("Module '" + module.id() + "' is disabled in config.yml.");
                continue;
            }
            try {
                module.enable();
                plugin.getLogger().info("Enabled module '" + module.id() + "'.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to enable module '" + module.id() + "': " + e);
                e.printStackTrace();
            }
        }
    }

    public void disableModules() {
        List<Module> reversed = new ArrayList<>(modules.values());
        java.util.Collections.reverse(reversed);
        for (Module module : reversed) {
            try {
                module.disable();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to disable module '" + module.id() + "': " + e);
            }
        }
    }

    public void reload() {
        disableModules();
        enableModules();
    }

    public int enabledCount() {
        return (int) modules.values().stream().filter(Module::isEnabled).count();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules.values()) {
            if (type.isInstance(module)) return (T) module;
        }
        return null;
    }

    public TokenService tokens() {
        TokensModule module = get(TokensModule.class);
        return module == null ? null : module.service();
    }
}
