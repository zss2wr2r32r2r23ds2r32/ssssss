package com.sharded.core.modules.chatformat;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.modules.itemshop.ItemShopModule;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatFormatModule extends SetupFeatureModule implements Listener {

    public ChatFormatModule(ShardedCore plugin) {
        super(plugin, "chatformat");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        migrateConfigLists("config-version", "format");
    }

    @Override
    public void reload() {
        super.reload();
        migrateConfigLists("config-version", "format");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String format = config.getString("format",
                "%luckperms_prefix%%name_colour% %tag% &8▷ &r%chat_colour%%message%");
        List<String> extra = config.getStringList("extra-placeholders");
        StringBuilder built = new StringBuilder(format.replace("%message%", "<message>"));
        for (String placeholder : extra) {
            built.append(placeholder);
        }
        final String template = built.toString();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            Map<String, String> cosmetics = resolveCosmetics(source);
            String resolved = template
                    .replace("{tag}", cosmetics.getOrDefault("tag", ""))
                    .replace("{tag_before}", cosmetics.getOrDefault("tag_before", cosmetics.getOrDefault("tag", "")))
                    .replace("{tag_after}", cosmetics.getOrDefault("tag_after", ""))
                    .replace("%tag%", cosmetics.getOrDefault("tag", ""))
                    .replace("%name_colour%", cosmetics.getOrDefault("name", source.getName()))
                    .replace("%chat_colour%", cosmetics.getOrDefault("chat", ""))
                    .replace("<message>", plain);
            return TextUtil.component(TextUtil.applyPlaceholders(source, resolved, null));
        });
    }

    private Map<String, String> resolveCosmetics(Player player) {
        Map<String, String> map = new HashMap<>();
        CosmeticService cosmetics = plugin.cosmetics();
        if (cosmetics == null || cosmetics.database() == null) {
            map.put("name", player.getName());
            map.put("chat", "");
            map.put("tag", "");
            return map;
        }
        String chat = cosmetics.chatColorPrefix(player.getUniqueId());
        String tagMiddle = cosmetics.database().get(player.getUniqueId()).tagDisplay();
        String tag = "";
        if (tagMiddle != null && !tagMiddle.isBlank()) {
            String stripped = tagMiddle.trim();
            if (stripped.contains("[") && stripped.contains("]")) {
               tag = stripped;
            } else {
               tag = "&7[" + stripped + "&r&7]";
            }
        }
        map.put("chat", chat != null ? chat : "");
        ItemShopModule itemshop = plugin.modules().get(ItemShopModule.class);
        if (itemshop != null) {
            String before = itemshop.tagBefore(player);
            String after = itemshop.tagAfter(player);
            map.put("tag_before", before);
            map.put("tag_after", after);
            if (!before.isBlank() || !after.isBlank()) {
                map.put("tag", before.isBlank() ? after : before);
            } else {
                map.put("tag", tag);
            }
        } else {
            map.put("tag_before", tag);
            map.put("tag_after", "");
            map.put("tag", tag);
        }
        map.put("name", cosmetics.formattedName(player));
        return map;
    }
}
