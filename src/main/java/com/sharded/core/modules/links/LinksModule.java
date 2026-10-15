package com.sharded.core.modules.links;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clickable multi-line link messages for Discord, store, and applications. */
public final class LinksModule extends Module implements CommandExecutor {

    public LinksModule(ShardedCore plugin) {
        super(plugin, "links");
    }

    @Override
    protected void onEnable() {
        registerCommand("discord", this);
        registerCommand("store", this);
        registerCommand("apply", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String section = switch (cmd) {
            case "store", "webstore" -> "store";
            default -> cmd;
        };
        if (config.getConfigurationSection(section) == null) {
            send(sender, "missing-section", "%section%", section);
            return true;
        }
        deliverLink(sender, section);
        return true;
    }

    private void deliverLink(CommandSender sender, String sectionKey) {
        ConfigurationSection section = config.getConfigurationSection(sectionKey);
        if (section == null) return;

        List<String> lines = section.getStringList("message");
        if (lines.isEmpty()) {
            String single = section.getString("message");
            if (single != null && !single.isBlank()) lines = List.of(single);
        }
        String url = section.getString("url", "");
        Component message = buildLinkMessage(lines, url);
        MessageUtil.deliver(sender, message, resolveDelivery("link"));

        if (sender instanceof Player player) {
            playConfiguredSound(player, section.getString("sound", ""));
        }
    }

    private Component buildLinkMessage(List<String> lines, String url) {
        int lastNonEmpty = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line != null && !line.trim().isEmpty()) {
                lastNonEmpty = i;
                break;
            }
        }
        if (lastNonEmpty < 0) return Component.empty();

        List<Component> parts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.trim().isEmpty()) continue;
            Component part = Text.c(Text.apply(line.replace("%prefix%", messagePrefix())));
            if (i == lastNonEmpty && url != null && !url.isBlank()) {
                String normalized = normalizeUrl(url);
                part = part
                        .clickEvent(ClickEvent.openUrl(normalized))
                        .hoverEvent(HoverEvent.showText(Text.c(raw("click-hover", "%url%", normalized))));
            }
            parts.add(part);
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    private void playConfiguredSound(Player player, String soundName) {
        if (soundName == null || soundName.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 0.8f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return "https://" + trimmed;
    }
}
