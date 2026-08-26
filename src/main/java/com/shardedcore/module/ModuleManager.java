package com.shardedcore.module;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.modules.announce.AnnounceModule;
import com.shardedcore.modules.chatfilter.ChatFilterModule;
import com.shardedcore.modules.chatformat.ChatFormatModule;
import com.shardedcore.modules.coinflip.CoinflipModule;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.modules.commands.CommandsModule;
import com.shardedcore.modules.crates.CratesModule;
import com.shardedcore.modules.deathmessages.DeathMessagesModule;
import com.shardedcore.modules.dropfix.DropFixModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.guide.GuideModule;
import com.shardedcore.modules.homes.HomesModule;
import com.shardedcore.modules.joinmessages.JoinMessagesModule;
import com.shardedcore.modules.killrewards.KillRewardsModule;
import com.shardedcore.modules.kits.KitsModule;
import com.shardedcore.modules.live.LiveModule;
import com.shardedcore.modules.nametags.NametagsModule;
import com.shardedcore.modules.ping.PingModule;
import com.shardedcore.modules.playtimerewards.PlaytimeRewardsModule;
import com.shardedcore.modules.rtp.RtpModule;
import com.shardedcore.modules.rules.RulesModule;
import com.shardedcore.modules.sell.SellModule;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.modules.shop.ShopModule;
import com.shardedcore.modules.spawn.SpawnModule;
import com.shardedcore.modules.tpa.TpaModule;
import com.shardedcore.modules.welcome.WelcomeModule;
import com.shardedcore.modules.workstations.WorkstationsModule;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

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
        register(new AnnounceModule(plugin));
        register(new CommandsModule(plugin));
        register(new ChatFilterModule(plugin));
        register(new ChatFormatModule(plugin));
        register(new SettingsModule(plugin));
        register(new EconomyModule(plugin));
        register(new CoinflipModule(plugin));
        register(new CombatModule(plugin));
        register(new CratesModule(plugin));
        register(new DeathMessagesModule(plugin));
        register(new DropFixModule(plugin));
        register(new GuideModule(plugin));
        register(new HomesModule(plugin));
        register(new JoinMessagesModule(plugin));
        register(new WelcomeModule(plugin));
        register(new KitsModule(plugin));
        register(new LiveModule(plugin));
        register(new NametagsModule(plugin));
        register(new PingModule(plugin));
        register(new RtpModule(plugin));
        register(new SellModule(plugin));
        register(new ShopModule(plugin));
        register(new RulesModule(plugin));
        register(new KillRewardsModule(plugin));
        register(new PlaytimeRewardsModule(plugin));
        register(new SpawnModule(plugin));
        register(new TpaModule(plugin));
        register(new WorkstationsModule(plugin));
    }

    private void register(Module module) {
        registered.put(module.id(), module);
    }

    public void loadAll() {
        disableAll();
        for (Module module : registered.values()) {
            module.loadFiles();
            if (!module.enabledInConfig()) {
                module.markDisabled();
                plugin.getLogger().info("Module disabled: " + module.id());
                continue;
            }
            try {
                module.enable();
                enabled.put(module.id(), module);
                plugin.getLogger().info("Enabled module: " + module.id());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable " + module.id(), ex);
                module.markDisabled();
            }
        }
        plugin.refreshCommands();
    }

    public void disableAll() {
        List<Module> modules = new ArrayList<>(enabled.values());
        Collections.reverse(modules);
        for (Module module : modules) {
            try {
                module.disable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable " + module.id(), ex);
            }
            module.markDisabled();
        }
        enabled.clear();
    }

    public boolean setEnabled(String id, boolean on) {
        Module module = registered.get(id);
        if (module == null) return false;
        FileConfiguration config = plugin.getConfig();
        config.set("modules." + id, on);
        plugin.saveConfig();
        boolean ok = true;
        if (on) {
            if (enabled.containsKey(id)) {
                plugin.refreshCommands();
                return true;
            }
            try {
                module.loadFiles();
                module.enable();
                enabled.put(id, module);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable " + id, ex);
                module.markDisabled();
                ok = false;
            }
        } else {
            if (enabled.containsKey(id)) {
                try {
                    module.disable();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to disable " + id, ex);
                }
                enabled.remove(id);
            }
            module.markDisabled();
        }
        plugin.refreshCommands();
        return ok;
    }

    public void openGui(Player player, int page) {
        List<Module> list = new ArrayList<>(registered.values());
        int perPage = 14;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, "&8Modules", 3);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int start = current * perPage;
        for (int i = 0; i < slots.length; i++) {
            int index = start + i;
            if (index >= list.size()) break;
            Module module = list.get(index);
            boolean on = isEnabled(module.id());
            List<String> lore = List.of(
                    "&8Description",
                    "",
                    (on ? "&#97F900" : "&#FF0000") + "Information:",
                    (on ? "&#97F900" : "&#FF0000") + "| &fClick to " + (on ? "disable" : "enable"),
                    (on ? "&#97F900" : "&#FF0000") + "| &fthis module",
                    "",
                    (on ? "&#97F900" : "&#FF0000") + "ℹ &fCommands go " + (on ? "green" : "&#FF0000red"),
                    (on ? "&#A9FF00&lENABLED" : "&#FF0000&lDISABLED"),
                    "",
                    "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Toggle"
            );
            menu.set(slots[i], Items.named(
                    on ? Material.LIME_DYE : Material.RED_DYE,
                    (on ? "&#97F900&l" : "&#FF0000&l") + module.id().toUpperCase(),
                    lore
            ), event -> {
                event.setCancelled(true);
                setEnabled(module.id(), !on);
                openGui(player, current);
            });
        }
        menu.set(18, Items.named(Material.RED_STAINED_GLASS_PANE, "&#FF0000&lPREVIOUS PAGE", List.of("&7Page " + current)), event -> {
            event.setCancelled(true);
            if (current > 0) openGui(player, current - 1);
        });
        menu.set(26, Items.named(Material.LIME_STAINED_GLASS_PANE, "&#80ee0b&lNEXT PAGE", List.of("&7Page " + (current + 2))), event -> {
            event.setCancelled(true);
            if (current + 1 < pages) openGui(player, current + 1);
        });
        menu.fill(Items.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        plugin.menus().open(player, menu);
        player.sendMessage(ColorUtil.parse("&#A370EE&lMODULES &7▷ &fPage &#A370EE" + (current + 1) + "&7/&#A370EE" + pages));
    }

    public Collection<Module> registered() {
        return Collections.unmodifiableCollection(registered.values());
    }

    public boolean isEnabled(String id) {
        return enabled.containsKey(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module module : enabled.values()) {
            if (type.isInstance(module)) return (T) module;
        }
        return null;
    }

    public Module get(String id) {
        return enabled.get(id);
    }
}
