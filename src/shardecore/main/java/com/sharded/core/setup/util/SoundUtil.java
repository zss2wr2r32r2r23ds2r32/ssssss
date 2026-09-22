package com.sharded.core.setup.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.GuiSounds;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(Player player, String key) {
        if (player == null || key == null || key.isBlank()) {
            return;
        }
        ShardedCore plugin = ShardedCore.get();
        float volume = 1f;
        float pitch = 1f;
        if (plugin != null && plugin.guiNavigation() != null) {
            volume = plugin.guiNavigation().soundVolume();
            pitch = plugin.guiNavigation().soundPitch();
        }
        GuiSounds.playRaw(player, key, volume, pitch);
    }
}
