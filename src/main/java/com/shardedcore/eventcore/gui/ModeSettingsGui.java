package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.modules.SettingsModule;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The per-gamemode settings board.
 *
 * <p>The layout below is only a fallback: slots, materials, names and lore all
 * come from {@code settings.yml}, so operators can rearrange the board or swap
 * icons without a code change.</p>
 */
public final class ModeSettingsGui extends Gui {

    /** Fallback slot and icon for one settings entry, per gamemode. */
    private record Entry(Setting setting, int slot, Material crystalIcon, Material diasmpIcon) {

        Material icon(EventMode mode) {
            return mode == EventMode.CRYSTAL ? crystalIcon : diasmpIcon;
        }
    }

    private static final Entry[] LAYOUT = {
            new Entry(Setting.PVP, 11, Material.NETHERITE_SWORD, Material.DIAMOND_SWORD),
            new Entry(Setting.LOCATOR_BAR, 13, Material.SPYGLASS, Material.SPYGLASS),
            new Entry(Setting.SPAWN_PROTECTION, 15, Material.BRUSH, Material.BRUSH),
            new Entry(Setting.KITS, 20, Material.NETHERITE_HELMET, Material.DIAMOND_HELMET),
            new Entry(Setting.WORLD_BORDER, 22, Material.BARRIER, Material.BARRIER),
            new Entry(Setting.REVIVE, 24, Material.TOTEM_OF_UNDYING, Material.TOTEM_OF_UNDYING),
            new Entry(Setting.BEDROCK_DROP, 29, Material.BEDROCK, Material.BEDROCK),
            new Entry(Setting.SUPPLY_DROPS, 29, Material.CHEST, Material.CHEST),
            new Entry(Setting.CLEAR_BLOCKS, 31, Material.OBSIDIAN, Material.OBSIDIAN),
            new Entry(Setting.COUNTDOWN, 33, Material.CLOCK, Material.CLOCK),
    };

    private final EventMode mode;

    public ModeSettingsGui(ShardedEventCore plugin, EventMode mode) {
        super(plugin, plugin.modules().byType(SettingsModule.class) == null
                ? 5 : plugin.modules().byType(SettingsModule.class).rows("modes." + mode.id() + ".rows", 5));
        this.mode = mode;
    }

    private SettingsModule settings() {
        return plugin.modules().byType(SettingsModule.class);
    }

    public EventMode mode() {
        return mode;
    }

    @Override
    protected Component title() {
        SettingsModule module = settings();
        String fallback = mode == EventMode.CRYSTAL ? "Crystal Settings - " : "DiamondSMP Settings - ";
        return Text.parse(module == null ? fallback : module.title("modes." + mode.id() + ".title", fallback));
    }

    @Override
    protected void build() {
        SettingsModule module = settings();
        if (module == null) {
            return;
        }
        for (Entry entry : LAYOUT) {
            Setting setting = entry.setting();
            if (!module.appliesTo(mode, setting)) {
                continue;
            }
            int slot = module.slot(mode, setting, entry.slot());
            set(slot, module.icon(mode, setting, entry.icon(mode), entry.slot()),
                    (player, event) -> module.handleClick(player, mode, setting, event.getClick()));
        }

        ConfigurationSection section = module.modeSection(mode);
        addBackButton(module, section);
        fill(module.filler(section));
    }

    private void addBackButton(SettingsModule module, ConfigurationSection section) {
        ConfigurationSection back = section == null ? null : section.getConfigurationSection("back");
        if (back == null || !back.getBoolean("enabled", true)) {
            return;
        }
        com.shardedcore.eventcore.config.ItemDefinition definition =
                com.shardedcore.eventcore.config.ItemDefinition.of(back, Material.ARROW, size() - 9);
        set(definition.slot(), definition.build(module.selectorPlaceholders(mode)),
                (player, event) -> plugin.guis().selector().open(player));
    }
}
