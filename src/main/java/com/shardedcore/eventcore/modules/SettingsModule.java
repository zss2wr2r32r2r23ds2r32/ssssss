package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ItemDefinition;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.module.EventModule;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The brains behind {@code /settings}.
 *
 * <p>Reads {@code settings.yml} for the look of every menu entry, renders the
 * {@code %status%} placeholders, and turns a click into the right action. Menus
 * only ever ask this module what to draw and what a click means, which keeps
 * all operator-facing behaviour in one place.</p>
 */
public final class SettingsModule extends EventModule {

    /** How a settings entry responds to being clicked. */
    public enum Behaviour {
        /** Any click flips the stored boolean. */
        TOGGLE,
        /** Left click runs the action, right click flips the enabled boolean. */
        ACTION,
        /** Left click opens a sub-menu, right click flips the enabled boolean. */
        MENU
    }

    public SettingsModule(ShardedEventCore plugin) {
        super(plugin, "settings", "The /settings menus for both gamemodes.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onConfigReload() {
        plugin.guis().invalidateAll();
    }

    private FileConfiguration settings() {
        return plugin.settingsConfig().raw();
    }

    // ------------------------------------------------------------------ layout

    public ConfigurationSection selectorSection() {
        return settings().getConfigurationSection("selector");
    }

    public ConfigurationSection modeSection(EventMode mode) {
        return settings().getConfigurationSection("modes." + mode.id());
    }

    public ConfigurationSection itemSection(EventMode mode, Setting setting) {
        return settings().getConfigurationSection("modes." + mode.id() + ".items." + setting.id());
    }

    public String title(String path, String fallback) {
        return settings().getString(path, fallback);
    }

    public int rows(String path, int fallback) {
        return Math.max(1, Math.min(6, settings().getInt(path, fallback)));
    }

    /** Builds the background pane used by every menu. */
    public ItemStack filler(ConfigurationSection section) {
        ConfigurationSection fillerSection = section == null ? null : section.getConfigurationSection("filler");
        if (fillerSection == null) {
            fillerSection = settings().getConfigurationSection("filler");
        }
        if (fillerSection != null && !fillerSection.getBoolean("enabled", true)) {
            return null;
        }
        ItemDefinition definition = ItemDefinition.of(fillerSection, Material.GRAY_STAINED_GLASS_PANE, -1);
        return definition.build(Map.of());
    }

    // ------------------------------------------------------------------ status

    public String statusText(boolean enabled) {
        return settings().getString(enabled ? "status.enabled" : "status.disabled",
                enabled ? "&aENABLED" : "&cDISABLED");
    }

    public Component statusComponent(boolean enabled) {
        return com.shardedcore.eventcore.util.Text.parse(statusText(enabled));
    }

    /**
     * Placeholders available in a settings icon's name and lore.
     *
     * <p>Every entry gets {@code %status%} and the general event counters; entries
     * that own extra data (border size, dead players, selected kit) get their own
     * tokens as well.</p>
     */
    public Map<String, String> placeholders(EventMode mode, Setting setting) {
        boolean enabled = plugin.state().toggleValue(mode, setting);
        Map<String, String> placeholders = new HashMap<>(12);
        placeholders.put("%status%", statusText(enabled));
        placeholders.put("%state%", statusText(enabled));
        placeholders.put("%enabled%", Boolean.toString(enabled));
        placeholders.put("%mode%", mode.id());
        placeholders.put("%selected%", statusText(plugin.state().isSelected(mode)));
        placeholders.put("%phase%", plugin.state().phase().name().toLowerCase(java.util.Locale.ROOT));
        placeholders.put("%alive%", Integer.toString(plugin.state().aliveCount()));
        placeholders.put("%dead%", Integer.toString(plugin.state().dead().size()));

        switch (setting) {
            case WORLD_BORDER -> {
                WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
                placeholders.put("%size%", border == null ? "0" : border.formattedSize());
                placeholders.put("%border%", border == null ? "0" : border.formattedSize());
            }
            case COUNTDOWN -> {
                CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
                int fallback = countdown == null ? 10 : countdown.defaultSeconds();
                placeholders.put("%seconds%", Integer.toString(plugin.state().selectedCountdown(mode, fallback)));
            }
            case KITS -> {
                String kit = plugin.state().selectedKit(mode);
                placeholders.put("%kit%", kit == null ? settings().getString("status.none", "&7none") : kit);
            }
            case CLEAR_BLOCKS -> {
                ClearBlocksModule clear = plugin.modules().byType(ClearBlocksModule.class);
                placeholders.put("%tracked%", clear == null ? "0" : Integer.toString(clear.trackedCount()));
            }
            case BEDROCK_DROP -> {
                BedrockDropModule drop = plugin.modules().byType(BedrockDropModule.class);
                placeholders.put("%progress%", drop == null ? "100" : Integer.toString(drop.progressPercent()));
            }
            case SUPPLY_DROPS -> {
                SupplyDropModule drops = plugin.modules().byType(SupplyDropModule.class);
                placeholders.put("%count%", drops == null ? "0" : Integer.toString(drops.defaultCount()));
            }
            default -> {
                // No extra tokens for the plain toggles.
            }
        }
        return placeholders;
    }

    /** Renders a settings icon, applying the optional {@code selected:} overlay. */
    public ItemStack icon(EventMode mode, Setting setting, Material fallback, int fallbackSlot) {
        ConfigurationSection section = itemSection(mode, setting);
        boolean enabled = plugin.state().toggleValue(mode, setting);
        ItemDefinition definition = ItemDefinition.of(section, fallback, fallbackSlot);
        if (enabled && section != null) {
            definition = definition.withOverlay(section.getConfigurationSection("selected"));
        }
        return definition.build(placeholders(mode, setting), enabled);
    }

    public int slot(EventMode mode, Setting setting, int fallback) {
        ConfigurationSection section = itemSection(mode, setting);
        return section == null ? fallback : section.getInt("slot", fallback);
    }

    // --------------------------------------------------------------- behaviour

    /** Which settings entries exist for a mode, and how each behaves. */
    public Behaviour behaviour(Setting setting) {
        return switch (setting) {
            case PVP, LOCATOR_BAR, SPAWN_PROTECTION -> Behaviour.TOGGLE;
            case KITS, COUNTDOWN -> Behaviour.MENU;
            default -> Behaviour.ACTION;
        };
    }

    public boolean appliesTo(EventMode mode, Setting setting) {
        return switch (setting) {
            case BEDROCK_DROP -> mode == EventMode.CRYSTAL;
            case SUPPLY_DROPS -> mode == EventMode.DIASMP;
            default -> true;
        };
    }

    /** Flips a toggle, applies the side effects and refreshes every open menu. */
    public void toggle(Player actor, EventMode mode, Setting setting) {
        boolean value = plugin.state().flipToggle(mode, setting);

        if (setting == Setting.PVP || setting == Setting.LOCATOR_BAR) {
            ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
            if (protection != null && protection.isEnabled()) {
                protection.applyWorldRules();
            }
        }

        plugin.messages().send(actor, "settings.toggled",
                "%setting%", setting.id(),
                "%mode%", mode.id(),
                "%status%", statusText(value));
        plugin.guis().refreshAll();
    }

    /** Selects or unselects a gamemode. */
    public void select(Player actor, EventMode mode) {
        if (plugin.state().isSelected(mode)) {
            plugin.state().select(null);
            plugin.messages().send(actor, "settings.unselected", "%mode%", mode.id());
        } else {
            plugin.state().select(mode);
            plugin.messages().send(actor, "settings.selected", "%mode%", mode.id());
            ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
            if (protection != null && protection.isEnabled()) {
                protection.applyWorldRules();
            }
        }
        // The join handler caches the active spawn, so it has to be told.
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        if (spawnModule != null && spawnModule.isEnabled()) {
            spawnModule.refreshCache();
        }
        plugin.guis().refreshAll();
    }

    public boolean isToggleClick(ClickType click) {
        return click.isRightClick();
    }

    /** Placeholders available on the gamemode icons in the selector menu. */
    public Map<String, String> selectorPlaceholders(EventMode mode) {
        boolean selected = plugin.state().isSelected(mode);
        Map<String, String> placeholders = new HashMap<>(8);
        placeholders.put("%status%", statusText(selected));
        placeholders.put("%selected%", statusText(selected));
        placeholders.put("%mode%", mode.id());
        placeholders.put("%spawn%", statusText(plugin.state().hasSpawn(mode)));
        placeholders.put("%phase%", plugin.state().phase().name().toLowerCase(java.util.Locale.ROOT));
        placeholders.put("%alive%", Integer.toString(plugin.state().aliveCount()));
        String kit = plugin.state().selectedKit(mode);
        placeholders.put("%kit%", kit == null ? settings().getString("status.none", "&7none") : kit);
        return placeholders;
    }

    /**
     * Turns a click on a settings icon into the matching action.
     *
     * <p>Plain toggles react to any click. Everything else runs its action on
     * left click and flips its own enabled flag on right click, which keeps a
     * destructive action (clearing the arena, dropping to bedrock) one deliberate
     * click away while still exposing an on/off state through {@code %status%}.</p>
     */
    public void handleClick(Player actor, EventMode mode, Setting setting, ClickType click) {
        Behaviour behaviour = behaviour(setting);
        if (behaviour == Behaviour.TOGGLE) {
            toggle(actor, mode, setting);
            return;
        }
        if (isToggleClick(click)) {
            toggle(actor, mode, setting);
            return;
        }
        if (!plugin.state().toggleValue(mode, setting)) {
            plugin.messages().send(actor, "settings.action-disabled", "%setting%", setting.id());
            return;
        }
        runAction(actor, mode, setting, click);
    }

    private void runAction(Player actor, EventMode mode, Setting setting, ClickType click) {
        switch (setting) {
            case KITS -> openOrGiveKit(actor, mode);
            case COUNTDOWN -> plugin.guis().countdowns(mode).open(actor);
            case WORLD_BORDER -> openBorderPrompt(actor);
            case REVIVE -> reviveAll(actor);
            case BEDROCK_DROP -> runBedrockDrop(actor);
            case CLEAR_BLOCKS -> runClearBlocks(actor);
            case SUPPLY_DROPS -> runSupplyDrops(actor, click);
            default -> plugin.messages().send(actor, "settings.no-action", "%setting%", setting.id());
        }
    }

    /**
     * Crystal mode opens a kit chooser; DiamondSMP hands out its single kit
     * straight away, matching the two layouts in {@code settings.yml}.
     */
    private void openOrGiveKit(Player actor, EventMode mode) {
        ConfigurationSection section = itemSection(mode, Setting.KITS);
        boolean opensMenu = section == null ? mode == EventMode.CRYSTAL : section.getBoolean("menu", mode == EventMode.CRYSTAL);
        if (opensMenu) {
            plugin.guis().kits(mode).open(actor);
            return;
        }
        String kit = section == null ? null : section.getString("kit");
        if (kit == null || kit.isBlank()) {
            kit = plugin.state().selectedKit(mode);
        }
        giveKitToEveryone(actor, mode, kit);
    }

    /** Applies a kit to the whole server and remembers it as the spawn kit. */
    public void giveKitToEveryone(Player actor, EventMode mode, String kit) {
        if (kit == null || kit.isBlank()) {
            plugin.messages().send(actor, "kits.none-selected");
            return;
        }
        KitModule kits = plugin.modules().byType(KitModule.class);
        if (kits == null || !kits.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "kits");
            return;
        }
        int served = kits.giveEveryone(kit);
        if (served < 0) {
            plugin.messages().send(actor, "kits.missing", "%kit%", kit);
            return;
        }
        plugin.state().selectedKit(mode, kit);
        plugin.messages().send(actor, "kits.given-all", "%kit%", kit, "%players%", Integer.toString(served));
        plugin.guis().refreshAll();
    }

    private void openBorderPrompt(Player actor) {
        WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
        if (border == null || !border.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "worldborder");
            return;
        }
        actor.closeInventory();
        border.promptFor(actor);
    }

    private void reviveAll(Player actor) {
        GameModule game = plugin.modules().byType(GameModule.class);
        if (game == null || !game.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "game");
            return;
        }
        int revived = game.reviveAll();
        plugin.messages().send(actor, "game.revived-all", "%players%", Integer.toString(revived));
    }

    private void runBedrockDrop(Player actor) {
        BedrockDropModule drop = plugin.modules().byType(BedrockDropModule.class);
        if (drop == null || !drop.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "bedrockdrop");
            return;
        }
        if (!drop.start(cleared -> {
            plugin.messages().send(actor, "bedrockdrop.finished", "%blocks%", Long.toString(cleared));
            drop.dropPlayers();
            plugin.guis().refreshAll();
        })) {
            plugin.messages().send(actor, "bedrockdrop.busy");
            return;
        }
        plugin.messages().send(actor, "bedrockdrop.started");
    }

    private void runClearBlocks(Player actor) {
        ClearBlocksModule clear = plugin.modules().byType(ClearBlocksModule.class);
        if (clear == null || !clear.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "clearblocks");
            return;
        }
        if (!clear.clear(counts -> {
            plugin.messages().send(actor, "clearblocks.finished",
                    "%blocks%", Integer.toString(counts[0]),
                    "%entities%", Integer.toString(counts[1]));
            plugin.guis().refreshAll();
        })) {
            plugin.messages().send(actor, "clearblocks.busy");
            return;
        }
        plugin.messages().send(actor, "clearblocks.started");
    }

    private void runSupplyDrops(Player actor, ClickType click) {
        SupplyDropModule drops = plugin.modules().byType(SupplyDropModule.class);
        if (drops == null || !drops.isEnabled()) {
            plugin.messages().send(actor, "settings.module-disabled", "%module%", "supplydrops");
            return;
        }
        int count = click.isShiftClick() ? drops.defaultCount() * 2 : drops.defaultCount();
        if (!drops.spawn(count, placed -> plugin.messages().send(actor, "supplydrops.finished",
                "%count%", Integer.toString(placed)))) {
            plugin.messages().send(actor, "supplydrops.failed");
            return;
        }
        plugin.messages().send(actor, "supplydrops.started", "%count%", Integer.toString(count));
    }
}
