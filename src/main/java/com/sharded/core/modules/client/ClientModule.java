package com.sharded.core.modules.client;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemsAdderEscMenuSync;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Discord rich presence for Lunar / Feather / Dawn; escape menu via ItemsAdder lang overwrite. */
public final class ClientModule extends Module implements PluginMessageListener {

    private final Set<UUID> lunarPlayers = ConcurrentHashMap.newKeySet();
    private boolean featherApiAvailable;

    public ClientModule(ShardedCore plugin) {
        super(plugin, "client");
    }

    @Override
    protected void onEnable() {
        ItemsAdderEscMenuSync.install(plugin, config);
        featherApiAvailable = Bukkit.getPluginManager().getPlugin("feather-server-api") != null;
        registerPluginChannels();
    }

    private void registerPluginChannels() {
        if (!config.getBoolean("discord-rpc.enabled", true)) return;
        var messenger = Bukkit.getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, "apollo:json");
        messenger.registerIncomingPluginChannel(plugin, "lunar:apollo", this);
    }

    @Override
    protected void onDisable() {
        var messenger = Bukkit.getMessenger();
        try {
            messenger.unregisterIncomingPluginChannel(plugin, "lunar:apollo", this);
            messenger.unregisterOutgoingPluginChannel(plugin, "apollo:json");
        } catch (Exception ignored) {
        }
        lunarPlayers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            applyDiscordPresence(player);
        }, config.getLong("apply-delay-ticks", 40L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lunarPlayers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"lunar:apollo".equals(channel)) return;
        lunarPlayers.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> applyDiscordPresence(player));
    }

    private void applyDiscordPresence(Player player) {
        if (!config.getBoolean("discord-rpc.enabled", true)) return;
        if (tryFeatherPresence(player)) return;
        if (tryApolloReflection(player)) return;
        sendLunarJsonPresence(player);
    }

    private boolean tryFeatherPresence(Player player) {
        if (!featherApiAvailable) return false;
        try {
            Class<?> featherApi = Class.forName("net.digitalingot.feather.serverapi.api.FeatherAPI");
            Object metaService = featherApi.getMethod("getMetaService").invoke(null);
            Class<?> activityClass = Class.forName("net.digitalingot.feather.serverapi.api.meta.DiscordActivity");
            Object builder = activityClass.getMethod("builder").invoke(null);

            String details = config.getString("discord-rpc.details", "Playing Sharded MC");
            String state = config.getString("discord-rpc.state", "Online on ShardedMC");
            String image = config.getString("discord-rpc.image-url", "");
            String imageText = config.getString("discord-rpc.image-text", "Sharded MC");

            invokeBuilder(builder, "withDetails", details);
            invokeBuilder(builder, "withState", state);
            if (image != null && !image.isBlank()) {
                invokeBuilder(builder, "withImage", image);
                invokeBuilder(builder, "withImageText", imageText);
            }
            if (config.getBoolean("discord-rpc.show-player-count", true)) {
                int online = Bukkit.getOnlinePlayers().size();
                int max = Bukkit.getMaxPlayers();
                invokeBuilder(builder, "withPartySize", online, max);
            }

            Object activity = builder.getClass().getMethod("build").invoke(builder);
            Class<?> featherPlayerClass = Class.forName("net.digitalingot.feather.serverapi.api.player.FeatherPlayer");
            Object featherPlayer = featherApi.getMethod("getPlayer", UUID.class).invoke(null, player.getUniqueId());
            if (featherPlayer == null) return false;
            metaService.getClass().getMethod("updateDiscordActivity", featherPlayerClass, activityClass)
                    .invoke(metaService, featherPlayer, activity);
            return true;
        } catch (ClassNotFoundException e) {
            featherApiAvailable = false;
            return false;
        } catch (Exception e) {
            plugin.getLogger().fine("[client] Feather RPC skipped for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void invokeBuilder(Object builder, String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] instanceof Integer ? int.class : args[i].getClass();
        }
        builder.getClass().getMethod(method, types).invoke(builder, args);
    }

    private boolean tryApolloReflection(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") == null) return false;
        try {
            Class<?> apollo = Class.forName("com.lunarclient.apollo.Apollo");
            Object playerManager = apollo.getMethod("getPlayerManager").invoke(null);
            Object optional = playerManager.getClass().getMethod("getPlayer", UUID.class).invoke(playerManager, player.getUniqueId());
            if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty()) return false;
            Object apolloPlayer = opt.get();

            Class<?> presenceClass = Class.forName("com.lunarclient.apollo.module.richpresence.ServerRichPresence");
            Object presenceBuilder = presenceClass.getMethod("builder").invoke(null);
            setPresenceField(presenceBuilder, "gameName", config.getString("discord-rpc.game-name", "Sharded MC"));
            setPresenceField(presenceBuilder, "gameState", config.getString("discord-rpc.state", "Online"));
            setPresenceField(presenceBuilder, "playerState", config.getString("discord-rpc.details", "Playing Sharded MC"));
            Object presence = presenceBuilder.getClass().getMethod("build").invoke(presenceBuilder);

            Object moduleManager = apollo.getMethod("getModuleManager").invoke(null);
            Object richModule = moduleManager.getClass().getMethod("getModule", Class.class)
                    .invoke(moduleManager, Class.forName("com.lunarclient.apollo.module.richpresence.RichPresenceModule"));
            richModule.getClass()
                    .getMethod("overrideServerRichPresence", Class.forName("com.lunarclient.apollo.player.ApolloPlayer"), presenceClass)
                    .invoke(richModule, apolloPlayer, presence);
            return true;
        } catch (Exception e) {
            plugin.getLogger().fine("[client] Apollo RPC skipped for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void setPresenceField(Object builder, String method, String value) throws Exception {
        if (value == null || value.isBlank()) return;
        builder.getClass().getMethod(method, String.class).invoke(builder, value);
    }

    private void sendLunarJsonPresence(Player player) {
        if (!lunarPlayers.contains(player.getUniqueId()) && !config.getBoolean("discord-rpc.send-without-detection", true)) {
            return;
        }
        try {
            String game = escapeJson(config.getString("discord-rpc.game-name", "Sharded MC"));
            String state = escapeJson(config.getString("discord-rpc.state", "Online on ShardedMC"));
            String details = escapeJson(config.getString("discord-rpc.details", "Playing Sharded MC"));
            String payload = """
                    {"@type":"type.googleapis.com/lunarclient.apollo.richpresence.v1.OverrideServerRichPresenceMessage","game_name":"%s","game_state":"%s","player_state":"%s"}
                    """.formatted(game, state, details).trim();
            player.sendPluginMessage(plugin, "apollo:json", payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().fine("[client] Lunar JSON RPC failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
