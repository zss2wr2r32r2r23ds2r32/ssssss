package com.shardedcore.module;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.commands.announce.AnnounceModule;
import com.shardedcore.modules.commands.guide.GuideModule;
import com.shardedcore.modules.commands.homes.HomesModule;
import com.shardedcore.modules.commands.rules.RulesModule;
import com.shardedcore.modules.commands.spawn.SpawnModule;
import com.shardedcore.modules.commands.tpa.TpaModule;
import com.shardedcore.modules.commands.trash.TrashModule;
import com.shardedcore.modules.commands.workstations.WorkstationsModule;
import com.shardedcore.modules.crates.CratesModule;
import com.shardedcore.modules.deathmessages.DeathMessagesModule;
import com.shardedcore.modules.dropfix.DropfixModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.joincounter.JoinCounterModule;
import com.shardedcore.modules.killrewards.KillRewardsModule;
import com.shardedcore.modules.links.LinksModule;
import com.shardedcore.modules.live.LiveModule;
import com.shardedcore.modules.media.MediaModule;
import com.shardedcore.modules.nametags.NametagsModule;
import com.shardedcore.modules.ping.PingModule;
import com.shardedcore.modules.playtimerewards.PlaytimeRewardsModule;
import com.shardedcore.modules.rtp.RtpModule;
import com.shardedcore.modules.staff.StaffModule;
import com.shardedcore.modules.team.TeamsModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ModuleManager {

    private final ShardedCore plugin;
    private final Map<String, Module> registered = new LinkedHashMap<>();
    private final Map<String, Module> enabled = new LinkedHashMap<>();

    public ModuleManager(ShardedCore plugin) {
        this.plugin = plugin;
        register(new EconomyModule(plugin));
        register(new LiveModule(plugin));
        register(new LinksModule(plugin));
        register(new PingModule(plugin));
        register(new TrashModule(plugin));
        register(new SpawnModule(plugin));
        register(new HomesModule(plugin));
        register(new TpaModule(plugin));
        register(new RulesModule(plugin));
        register(new GuideModule(plugin));
        register(new WorkstationsModule(plugin));
        register(new AnnounceModule(plugin));
        register(new TeamsModule(plugin));
        register(new RtpModule(plugin));
        register(new KillRewardsModule(plugin));
        register(new PlaytimeRewardsModule(plugin));
        register(new JoinCounterModule(plugin));
        register(new MediaModule(plugin));
        register(new CratesModule(plugin));
        register(new DeathMessagesModule(plugin));
        register(new DropfixModule(plugin));
        register(new NametagsModule(plugin));
        register(new StaffModule(plugin));
    }

    public void register(Module module) {
        registered.put(module.getId(), module);
    }

    public void loadAll() {
        disableAll();
        for (Module module : registered.values()) {
            if (!module.isEnabledInConfig()) {
                plugin.getLogger().info("Module disabled in config: " + module.getId());
                continue;
            }
            try {
                module.loadFiles();
                module.enable();
                enabled.put(module.getId(), module);
                plugin.getLogger().info("Enabled module: " + module.getId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getId(), ex);
            }
        }
    }

    public void reloadAll() {
        for (Module module : new ArrayList<>(enabled.values())) {
            try {
                module.reload();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload module: " + module.getId(), ex);
            }
        }
        loadAll();
    }

    public void disableAll() {
        List<Module> modules = new ArrayList<>(enabled.values());
        Collections.reverse(modules);
        for (Module module : modules) {
            try {
                module.disable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getId(), ex);
            }
        }
        enabled.clear();
    }

    public Collection<Module> getRegistered() {
        return Collections.unmodifiableCollection(registered.values());
    }

    public Collection<Module> getEnabled() {
        return Collections.unmodifiableCollection(enabled.values());
    }

    public Module getModule(String id) {
        return enabled.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : enabled.values()) {
            if (type.isInstance(module)) return (T) module;
        }
        return null;
    }

    public boolean isEnabled(String id) {
        return enabled.containsKey(id);
    }
}
