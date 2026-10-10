package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ItemDefinition;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.CountdownModule;
import com.shardedcore.eventcore.modules.SettingsModule;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Countdown chooser reached from the clock icon.
 *
 * <p>Left click stores the length that {@code /start} will use for this
 * gamemode; right click stores it and starts the countdown immediately.</p>
 */
public final class CountdownSelectGui extends Gui {

    private static final int[] DEFAULT_SLOTS = {11, 13, 15, 10, 12, 14, 16};

    private final EventMode mode;

    public CountdownSelectGui(ShardedEventCore plugin, EventMode mode) {
        super(plugin, plugin.modules().byType(SettingsModule.class) == null
                ? 3 : plugin.modules().byType(SettingsModule.class).rows("countdown-menu.rows", 3));
        this.mode = mode;
    }

    private SettingsModule settings() {
        return plugin.modules().byType(SettingsModule.class);
    }

    @Override
    protected Component title() {
        SettingsModule module = settings();
        return Text.parse(module == null
                ? "Countdown Selector - "
                : module.title("countdown-menu.title", "Countdown Selector - "));
    }

    @Override
    protected void build() {
        SettingsModule module = settings();
        if (module == null) {
            return;
        }
        ConfigurationSection root = plugin.settingsConfig().raw().getConfigurationSection("countdown-menu");
        ConfigurationSection items = root == null ? null : root.getConfigurationSection("items");
        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
        int active = plugin.state().selectedCountdown(mode, countdown == null ? 10 : countdown.defaultSeconds());

        if (items != null) {
            int index = 0;
            for (String key : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                int seconds = section.getInt("seconds", parseKey(key, 10));
                boolean selected = seconds == active;

                ItemDefinition definition = ItemDefinition.of(section, Material.CLOCK,
                        DEFAULT_SLOTS[Math.min(index, DEFAULT_SLOTS.length - 1)]);
                if (selected) {
                    definition = definition.withOverlay(section.getConfigurationSection("selected"));
                }

                Map<String, String> placeholders = new HashMap<>(module.selectorPlaceholders(mode));
                placeholders.put("%seconds%", Integer.toString(seconds));
                placeholders.put("%status%", module.statusText(selected));
                placeholders.put("%selected%", module.statusText(selected));

                set(definition.slot(), definition.build(placeholders, selected),
                        (player, event) -> onClick(player, seconds, event.getClick().isRightClick()));
                index++;
            }
        }
        addBackButton(module, root);
        fill(module.filler(root));
    }

    private static int parseKey(String key, int fallback) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void onClick(Player player, int seconds, boolean startNow) {
        plugin.state().setSelectedCountdown(mode, seconds);
        plugin.messages().send(player, "countdown.selected", "%seconds%", Integer.toString(seconds));
        plugin.guis().refreshAll();

        if (!startNow) {
            return;
        }
        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
        if (countdown == null || !countdown.isEnabled()) {
            plugin.messages().send(player, "settings.module-disabled", "%module%", "countdown");
            return;
        }
        player.closeInventory();
        if (countdown.start(seconds)) {
            plugin.messages().send(player, "countdown.started", "%seconds%", Integer.toString(seconds));
        }
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
