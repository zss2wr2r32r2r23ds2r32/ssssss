package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Feedback;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

/** Backs {@code /announce <text>}: a configurable title plus optional chat block. */
public final class AnnounceModule extends EventModule {

    public AnnounceModule(ShardedEventCore plugin) {
        super(plugin, "announce", "Server-wide announcement titles via /announce.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    public void announce(String text) {
        FileConfiguration config = config().raw();
        Map<String, String> placeholders = Map.of("%text%", text);

        Title.Times times = Feedback.times(config.getConfigurationSection("times"), 10, 60, 10);
        Feedback.broadcastTitle(
                config.getString("title", "&#AD4EFF&lANNOUNCEMENT"),
                config.getString("subtitle", "&f%text%"),
                times,
                placeholders);

        Sound sound = Feedback.sound(config.getConfigurationSection("sound"));
        Feedback.play(Bukkit.getServer(), sound);

        if (config.getBoolean("also-chat", false)) {
            List<String> lines = config.getStringList("chat-format");
            for (String line : lines) {
                Bukkit.getServer().sendMessage(Text.parse(line, placeholders));
            }
        }
    }
}
