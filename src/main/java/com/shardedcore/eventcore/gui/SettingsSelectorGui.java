package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ItemDefinition;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.SettingsModule;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * The {@code /settings} entry point: one icon per gamemode.
 *
 * <p>Left clicking an icon selects that gamemode and opens its settings; right
 * clicking releases the selection. The selected icon glows and swaps to the
 * {@code selected:} name and lore from {@code settings.yml}.</p>
 */
public final class SettingsSelectorGui extends Gui {

    private static final int[] DEFAULT_SLOTS = {12, 14};

    public SettingsSelectorGui(ShardedEventCore plugin) {
        super(plugin, plugin.modules().byType(SettingsModule.class) == null
                ? 3 : plugin.modules().byType(SettingsModule.class).rows("selector.rows", 3));
    }

    private SettingsModule settings() {
        return plugin.modules().byType(SettingsModule.class);
    }

    @Override
    protected Component title() {
        SettingsModule module = settings();
        return Text.parse(module == null
                ? "Settings Selector - "
                : module.title("selector.title", "Settings Selector - "));
    }

    @Override
    protected void build() {
        SettingsModule module = settings();
        if (module == null) {
            return;
        }
        ConfigurationSection selector = module.selectorSection();

        EventMode[] modes = EventMode.values();
        for (int index = 0; index < modes.length; index++) {
            EventMode mode = modes[index];
            ConfigurationSection section = selector == null
                    ? null : selector.getConfigurationSection("items." + mode.id());
            boolean selected = plugin.state().isSelected(mode);

            ItemDefinition definition = ItemDefinition.of(section, mode.defaultIcon(),
                    DEFAULT_SLOTS[Math.min(index, DEFAULT_SLOTS.length - 1)]);
            if (selected && section != null) {
                definition = definition.withOverlay(section.getConfigurationSection("selected"));
            }
            ItemStack stack = definition.build(module.selectorPlaceholders(mode), selected);

            set(definition.slot(), stack, (player, event) -> onModeClick(player, mode, event.getClick(), selector));
        }

        fill(module.filler(selector));
    }

    private void onModeClick(org.bukkit.entity.Player player, EventMode mode,
                             org.bukkit.event.inventory.ClickType click, ConfigurationSection selector) {
        SettingsModule module = settings();
        if (module == null) {
            return;
        }
        boolean alreadySelected = plugin.state().isSelected(mode);

        if (click.isRightClick()) {
            if (alreadySelected) {
                module.select(player, mode);
            } else {
                plugin.guis().mode(mode).open(player);
            }
            return;
        }

        if (!alreadySelected) {
            module.select(player, mode);
        }
        boolean openOnSelect = selector == null || selector.getBoolean("open-on-select", true);
        if (openOnSelect || alreadySelected) {
            plugin.guis().mode(mode).open(player);
        }
    }
}
