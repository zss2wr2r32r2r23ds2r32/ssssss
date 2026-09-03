package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ItemDefinition;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.SettingsModule;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Kit chooser reached from the armour icon in a mode's settings board.
 *
 * <p>Clicking an entry both records it as the spawn kit for the gamemode and
 * hands it out to everyone online. Clicking the entry that is already selected
 * releases it, which is what the {@code selected:} lore in {@code settings.yml}
 * describes with "To Unselect".</p>
 */
public final class KitSelectGui extends Gui {

    private static final int[] DEFAULT_SLOTS = {12, 14, 10, 16, 11, 15, 13};

    private final EventMode mode;

    public KitSelectGui(ShardedEventCore plugin, EventMode mode) {
        super(plugin, plugin.modules().byType(SettingsModule.class) == null
                ? 3 : plugin.modules().byType(SettingsModule.class).rows("kit-menu.rows", 3));
        this.mode = mode;
    }

    private SettingsModule settings() {
        return plugin.modules().byType(SettingsModule.class);
    }

    @Override
    protected Component title() {
        SettingsModule module = settings();
        return Text.parse(module == null
                ? "Kit Selector - "
                : module.title("kit-menu.title", "Kit Selector - "));
    }

    @Override
    protected void build() {
        SettingsModule module = settings();
        if (module == null) {
            return;
        }
        ConfigurationSection root = plugin.settingsConfig().raw().getConfigurationSection("kit-menu");
        ConfigurationSection items = root == null ? null : root.getConfigurationSection("items");
        if (items != null) {
            int index = 0;
            for (String key : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                renderEntry(module, key, section, DEFAULT_SLOTS[Math.min(index, DEFAULT_SLOTS.length - 1)]);
                index++;
            }
        }
        addBackButton(module, root);
        fill(module.filler(root));
    }

    private void renderEntry(SettingsModule module, String key, ConfigurationSection section, int fallbackSlot) {
        String kit = section.getString("kit", key);
        boolean selected = kit.equalsIgnoreCase(String.valueOf(plugin.state().selectedKit(mode)));

        ItemDefinition definition = ItemDefinition.of(section, Material.NETHERITE_LEGGINGS, fallbackSlot);
        if (selected) {
            definition = definition.withOverlay(section.getConfigurationSection("selected"));
        }

        Map<String, String> placeholders = new HashMap<>(module.selectorPlaceholders(mode));
        placeholders.put("%kit%", kit);
        placeholders.put("%status%", module.statusText(selected));
        placeholders.put("%selected%", module.statusText(selected));

        set(definition.slot(), definition.build(placeholders, selected),
                (player, event) -> onClick(module, player, kit, selected));
    }

    private void onClick(SettingsModule module, Player player, String kit, boolean selected) {
        if (selected) {
            plugin.state().selectedKit(mode, null);
            plugin.messages().send(player, "kits.unselected", "%kit%", kit);
            plugin.guis().refreshAll();
            return;
        }
        module.giveKitToEveryone(player, mode, kit);
    }

    private void addBackButton(SettingsModule module, ConfigurationSection root) {
        ConfigurationSection back = root == null ? null : root.getConfigurationSection("back");
        if (back == null || !back.getBoolean("enabled", true)) {
            return;
        }
        ItemDefinition definition = ItemDefinition.of(back, Material.ARROW, size() - 9);
        set(definition.slot(), definition.build(module.selectorPlaceholders(mode)),
                (player, event) -> plugin.guis().mode(mode).open(player));
    }
}
