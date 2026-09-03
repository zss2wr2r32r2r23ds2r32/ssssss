package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;

import java.util.EnumMap;

/**
 * Lazily creates and caches the menus.
 *
 * <p>One instance per menu is shared by all viewers, so a toggle change means a
 * single in-place repaint rather than rebuilding an inventory per admin.</p>
 */
public final class GuiManager {

    private final ShardedEventCore plugin;

    private SettingsSelectorGui selector;
    private final EnumMap<EventMode, ModeSettingsGui> modeMenus = new EnumMap<>(EventMode.class);
    private final EnumMap<EventMode, KitSelectGui> kitMenus = new EnumMap<>(EventMode.class);
    private final EnumMap<EventMode, CountdownSelectGui> countdownMenus = new EnumMap<>(EventMode.class);

    public GuiManager(ShardedEventCore plugin) {
        this.plugin = plugin;
    }

    public SettingsSelectorGui selector() {
        if (selector == null) {
            selector = new SettingsSelectorGui(plugin);
        }
        return selector;
    }

    public ModeSettingsGui mode(EventMode mode) {
        return modeMenus.computeIfAbsent(mode, key -> new ModeSettingsGui(plugin, key));
    }

    public KitSelectGui kits(EventMode mode) {
        return kitMenus.computeIfAbsent(mode, key -> new KitSelectGui(plugin, key));
    }

    public CountdownSelectGui countdowns(EventMode mode) {
        return countdownMenus.computeIfAbsent(mode, key -> new CountdownSelectGui(plugin, key));
    }

    /** Repaints every open menu after a state change. */
    public void refreshAll() {
        if (selector != null) {
            selector.refresh();
        }
        modeMenus.values().forEach(Gui::refresh);
        kitMenus.values().forEach(Gui::refresh);
        countdownMenus.values().forEach(Gui::refresh);
    }

    /** Rebuilds every menu from scratch, picking up renamed titles and layouts. */
    public void invalidateAll() {
        if (selector != null) {
            selector.invalidate();
        }
        modeMenus.values().forEach(Gui::invalidate);
        kitMenus.values().forEach(Gui::invalidate);
        countdownMenus.values().forEach(Gui::invalidate);
    }
}
