package com.sharded.core.modules.tpa;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.TeleportHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaModule extends Module implements CommandExecutor, TabCompleter {

    private static final String STATE_TPA_IN = "tpa-incoming-enabled";
    private static final String STATE_TPA_HERE = "tpa-here-enabled";
    private static final String STATE_TPA_AUTO = "tpa-auto";

    private TpaGuiHandler guiHandler;
    private TeleportHelper teleportHelper;
    private BukkitTask expiryTask;

    private final Map<UUID, List<TpaRequest>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, List<TpaRequest>> outgoing = new ConcurrentHashMap<>();

    public TpaModule(ShardedCore plugin) {
        super(plugin, "tpa");
    }

    org.bukkit.configuration.file.YamlConfiguration config() {
        return config;
    }

    @Override
    protected void onEnable() {
        guiHandler = new TpaGuiHandler(this);
        teleportHelper = new TeleportHelper(plugin);
        teleportHelper.register();

        registerCommand("tpa", this);
        registerCommand("tpahere", this);
        registerCommand("tpaccept", this);
        registerCommand("tpacancel", this);
        registerCommand("tpatoggle", this);
        registerCommand("tpauto", this);

        long interval = Math.max(20L, config.getLong("request-expire-check-seconds", 5L) * 20L);
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireRequests, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (expiryTask != null) expiryTask.cancel();
        if (teleportHelper != null) {
            teleportHelper.cancelAll();
            teleportHelper.unregister();
        }
        incoming.clear();
        outgoing.clear();
    }

    String guiRaw(String key, String... replacements) {
        return Text.apply(config.getString("gui." + key, ""), replacements);
    }

    List<String> guiRawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>(config.getStringList("gui." + key));
        if (lines.isEmpty()) {
            String single = config.getString("gui." + key);
            if (single != null && !single.isEmpty()) lines.add(single);
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.apply(line, replacements));
        }
        return out;
    }

    boolean acceptsTpa(UUID target) {
        return plugin.stateStore().getBool(target, STATE_TPA_IN, true);
    }

    boolean acceptsTpHere(UUID target) {
        return plugin.stateStore().getBool(target, STATE_TPA_HERE, true);
    }

    boolean autoAccept(UUID target) {
        return plugin.stateStore().getBool(target, STATE_TPA_AUTO, false);
    }

    void sendRequest(Player requester, UUID targetId, TpaType type) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            send(requester, "player-offline", "%player%", OfflinePlayers.name(targetId));
            return;
        }
        if (requester.getUniqueId().equals(targetId)) {
            send(requester, "self-request");
            return;
        }
        if (type == TpaType.TO_TARGET && !acceptsTpa(targetId)) {
            send(requester, "target-tpa-disabled", "%player%", target.getName());
            return;
        }
        if (type == TpaType.HERE && !acceptsTpHere(targetId)) {
            send(requester, "target-tpahere-disabled", "%player%", target.getName());
            return;
        }
        if (hasOutgoingRequest(requester.getUniqueId(), targetId, type)) {
            send(requester, "already-sent", "%player%", target.getName());
            return;
        }
        long lifetimeMs = config.getLong("request-lifetime-seconds", 60L) * 1000L;
        TpaRequest request = new TpaRequest(requester.getUniqueId(), targetId, type,
                System.currentTimeMillis(), System.currentTimeMillis() + lifetimeMs);
        addOutgoing(request);
        addIncoming(request);

        send(requester, type == TpaType.TO_TARGET ? "sent-tpa" : "sent-tpahere", "%player%", target.getName());
        if (autoAccept(targetId)) {
            acceptRequest(target, request);
            return;
        }
        send(target, type == TpaType.TO_TARGET ? "received-tpa" : "received-tpahere",
                "%player%", requester.getName());
    }

    private void acceptRequest(Player target, TpaRequest request) {
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline()) {
            send(target, "player-offline", "%player%", OfflinePlayers.name(request.requester()));
            removeRequest(request);
            return;
        }
        removeRequest(request);
        Location destination = request.type() == TpaType.TO_TARGET
                ? target.getLocation()
                : requester.getLocation();
        Player traveller = request.type() == TpaType.TO_TARGET ? requester : target;
        Player other = request.type() == TpaType.TO_TARGET ? target : requester;

        int delay = config.getInt("teleport.delay-seconds", 5);
        TeleportHelper.Settings settings = new TeleportHelper.Settings(
                delay,
                config.getString("teleport.countdown-actionbar",
                        "&#0098FF&lTPA &8▷ &fTeleporting in &#0098FF&n%seconds%&r&#0098FFs"),
                config.getString("teleport.cancelled-actionbar",
                        "&#0098FF&lTPA &8▷ &fYou moved &8— &7teleport cancelled."),
                config.getString("teleport.countdown-sound", "BLOCK_NOTE_BLOCK_PLING"),
                config.getString("teleport.cancel-sound", "BLOCK_NOTE_BLOCK_BASS"),
                config.getString("teleport.success-sound", "ENTITY_ENDERMAN_TELEPORT")
        );
        teleportHelper.begin(traveller, destination.clone(), settings, p -> {
            send(p, "teleported", "%player%", other.getName());
            send(other, "teleported-other", "%player%", p.getName());
        });
    }

    private void expireRequests() {
        long now = System.currentTimeMillis();
        for (List<TpaRequest> list : new ArrayList<>(incoming.values())) {
            for (TpaRequest request : new ArrayList<>(list)) {
                if (request.expiresAt() <= now) removeRequest(request);
            }
        }
    }

    private void addIncoming(TpaRequest request) {
        incoming.computeIfAbsent(request.target(), k -> new ArrayList<>()).add(request);
    }

    private void addOutgoing(TpaRequest request) {
        outgoing.computeIfAbsent(request.requester(), k -> new ArrayList<>()).add(request);
    }

    private boolean hasOutgoingRequest(UUID requester, UUID target, TpaType type) {
        List<TpaRequest> list = outgoing.get(requester);
        if (list == null) return false;
        return list.stream().anyMatch(r -> r.target().equals(target) && r.type() == type);
    }

    private void removeRequest(TpaRequest request) {
        List<TpaRequest> in = incoming.get(request.target());
        if (in != null) {
            in.removeIf(r -> sameRequest(r, request));
            if (in.isEmpty()) incoming.remove(request.target());
        }
        List<TpaRequest> out = outgoing.get(request.requester());
        if (out != null) {
            out.removeIf(r -> sameRequest(r, request));
            if (out.isEmpty()) outgoing.remove(request.requester());
        }
    }

    private static boolean sameRequest(TpaRequest a, TpaRequest b) {
        return a.requester().equals(b.requester()) && a.target().equals(b.target()) && a.type() == b.type();
    }

    private void cancelAllFor(UUID uuid) {
        List<TpaRequest> out = outgoing.remove(uuid);
        if (out != null) {
            for (TpaRequest request : out) {
                List<TpaRequest> in = incoming.get(request.target());
                if (in != null) {
                    in.removeIf(r -> sameRequest(r, request));
                    if (in.isEmpty()) incoming.remove(request.target());
                }
            }
        }
        List<TpaRequest> in = incoming.remove(uuid);
        if (in != null) {
            for (TpaRequest request : in) {
                List<TpaRequest> outList = outgoing.get(request.requester());
                if (outList != null) {
                    outList.removeIf(r -> sameRequest(r, request));
                    if (outList.isEmpty()) outgoing.remove(request.requester());
                }
            }
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TpaGuiHandler.TpaGuiHolder holder = com.sharded.core.util.TrackedInventories.lookup(
                event.getView().getTopInventory(), TpaGuiHandler.TpaGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelAllFor(uuid);
        if (teleportHelper != null) teleportHelper.cancel(uuid, false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tpa.use")) {
            send(player, "no-permission");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        return switch (cmd) {
            case "tpa" -> handleTpa(player, args, TpaType.TO_TARGET);
            case "tpahere" -> handleTpa(player, args, TpaType.HERE);
            case "tpaccept" -> {
                handleAccept(player);
                yield true;
            }
            case "tpacancel" -> {
                cancelAllFor(player.getUniqueId());
                send(player, "cancelled-all");
                yield true;
            }
            case "tpatoggle" -> {
                handleToggle(player, args);
                yield true;
            }
            case "tpauto" -> {
                handleAuto(player);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleTpa(Player player, String[] args, TpaType type) {
        if (args.length == 0) {
            send(player, type == TpaType.TO_TARGET ? "tpa-usage" : "tpahere-usage");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            send(player, "player-not-found", "%player%", args[0]);
            return true;
        }
        if (config.getBoolean("gui.enabled", true)) {
            guiHandler.openRequestGui(player, target, type);
        } else {
            sendRequest(player, target.getUniqueId(), type);
        }
        return true;
    }

    private void handleAccept(Player player) {
        List<TpaRequest> requests = incoming.get(player.getUniqueId());
        if (requests == null || requests.isEmpty()) {
            send(player, "no-requests");
            return;
        }
        TpaRequest latest = requests.get(requests.size() - 1);
        acceptRequest(player, latest);
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length == 0) {
            send(player, "tpatoggle-usage");
            return;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("tpa") || mode.equals("in")) {
            boolean enabled = !acceptsTpa(player.getUniqueId());
            plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_IN, enabled);
            send(player, enabled ? "tpa-enabled" : "tpa-disabled");
            return;
        }
        if (mode.equals("tpahere") || mode.equals("here")) {
            boolean enabled = !acceptsTpHere(player.getUniqueId());
            plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_HERE, enabled);
            send(player, enabled ? "tpahere-enabled" : "tpahere-disabled");
            return;
        }
        send(player, "tpatoggle-usage");
    }

    private void handleAuto(Player player) {
        boolean enabled = !autoAccept(player.getUniqueId());
        plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_AUTO, enabled);
        send(player, enabled ? "auto-enabled" : "auto-disabled");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("sharded.tpa.use")) return List.of();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ((cmd.equals("tpa") || cmd.equals("tpahere")) && args.length == 1) {
            return TabCompleteHelper.onlinePlayers(args[0]);
        }
        if (cmd.equals("tpatoggle") && args.length == 1) {
            return TabCompleteHelper.filter(args[0], "tpa", "tpahere", "in", "here");
        }
        return List.of();
    }
}
