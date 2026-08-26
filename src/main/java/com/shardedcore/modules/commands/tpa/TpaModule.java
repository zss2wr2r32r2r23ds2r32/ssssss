package com.shardedcore.modules.commands.tpa;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.OfflinePlayers;
import com.shardedcore.util.TabCompleteHelper;
import com.shardedcore.util.TeleportHelper;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaModule extends Module implements CommandExecutor, TabCompleter, Listener {

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

    @Override
    public void enable() {
        guiHandler = new TpaGuiHandler(this);
        teleportHelper = new TeleportHelper(plugin);
        teleportHelper.start();
        registerListener(this);
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
    public void disable() {
        if (expiryTask != null) expiryTask.cancel();
        if (teleportHelper != null) teleportHelper.shutdown();
        incoming.clear();
        outgoing.clear();
        cleanup();
    }

    String guiRaw(String key, String... replacements) {
        return Text.apply(config.getString("gui." + key, ""), replacements);
    }

    List<String> guiRawList(String key, String... replacements) {
        List<String> out = new ArrayList<>();
        for (String line : config.getStringList("gui." + key)) out.add(Text.apply(line, replacements));
        return out;
    }

    void sendRequest(Player requester, UUID targetId, TpaType type) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            send(requester, "player-offline", "player", OfflinePlayers.name(targetId));
            return;
        }
        if (requester.getUniqueId().equals(targetId)) {
            send(requester, "self-request");
            return;
        }
        if (type == TpaType.TO_TARGET && !plugin.stateStore().getBool(targetId, STATE_TPA_IN, true)) {
            send(requester, "target-tpa-disabled", "player", target.getName());
            return;
        }
        if (type == TpaType.HERE && !plugin.stateStore().getBool(targetId, STATE_TPA_HERE, true)) {
            send(requester, "target-tpahere-disabled", "player", target.getName());
            return;
        }
        if (hasOutgoing(requester.getUniqueId(), targetId, type)) {
            send(requester, "already-sent", "player", target.getName());
            return;
        }
        long expiresAt = System.currentTimeMillis() + config.getLong("request-lifetime-seconds", 60L) * 1000L;
        TpaRequest request = new TpaRequest(requester.getUniqueId(), targetId, type, expiresAt);
        incoming.computeIfAbsent(targetId, k -> new ArrayList<>()).add(request);
        outgoing.computeIfAbsent(requester.getUniqueId(), k -> new ArrayList<>()).add(request);
        send(requester, type == TpaType.TO_TARGET ? "sent-tpa" : "sent-tpahere", "player", target.getName());
        if (plugin.stateStore().getBool(targetId, STATE_TPA_AUTO, false)) {
            acceptRequest(target, request);
            return;
        }
        send(target, type == TpaType.TO_TARGET ? "received-tpa" : "received-tpahere", "player", requester.getName());
    }

    private void acceptRequest(Player target, TpaRequest request) {
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline()) {
            send(target, "player-offline", "player", OfflinePlayers.name(request.requester()));
            removeRequest(request);
            return;
        }
        removeRequest(request);
        Location destination = request.type() == TpaType.TO_TARGET ? target.getLocation() : requester.getLocation();
        Player traveller = request.type() == TpaType.TO_TARGET ? requester : target;
        Player other = request.type() == TpaType.TO_TARGET ? target : requester;
        int delay = config.getInt("teleport.delay-seconds", 5);
        String countdown = config.getString("teleport.countdown-actionbar",
                "&#0098FF&lTPA &8▷ &fTeleporting in &#0098FF&n{seconds}s");
        String cancelled = config.getString("teleport.cancelled-actionbar",
                "&#0098FF&lTPA &8▷ &fYou moved &8— &7teleport cancelled.");
        teleportHelper.teleportDelayed(traveller, destination.clone(), delay, countdown, p -> {
            send(p, "teleported", "player", other.getName());
            send(other, "teleported-other", "player", p.getName());
        }, () -> MessageUtil.sendActionBar(traveller, plugin, cancelled));
    }

    private void expireRequests() {
        long now = System.currentTimeMillis();
        for (List<TpaRequest> list : new ArrayList<>(incoming.values())) {
            for (TpaRequest request : new ArrayList<>(list)) {
                if (request.expiresAt() <= now) removeRequest(request);
            }
        }
    }

    private boolean hasOutgoing(UUID requester, UUID target, TpaType type) {
        List<TpaRequest> list = outgoing.get(requester);
        return list != null && list.stream().anyMatch(r -> r.target().equals(target) && r.type() == type);
    }

    private void removeRequest(TpaRequest request) {
        List<TpaRequest> in = incoming.get(request.target());
        if (in != null) { in.remove(request); if (in.isEmpty()) incoming.remove(request.target()); }
        List<TpaRequest> out = outgoing.get(request.requester());
        if (out != null) { out.remove(request); if (out.isEmpty()) outgoing.remove(request.requester()); }
    }

    private void cancelAllFor(UUID uuid) {
        List<TpaRequest> out = outgoing.remove(uuid);
        if (out != null) for (TpaRequest r : out) removeRequest(r);
        List<TpaRequest> in = incoming.remove(uuid);
        if (in != null) for (TpaRequest r : in) removeRequest(r);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TpaGuiHandler.TpaGuiHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), TpaGuiHandler.TpaGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelAllFor(event.getPlayer().getUniqueId());
        if (teleportHelper != null) teleportHelper.cancel(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.tpa")) {
            send(player, "no-permission");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> handleTpa(player, args, TpaType.TO_TARGET);
            case "tpahere" -> handleTpa(player, args, TpaType.HERE);
            case "tpaccept" -> { handleAccept(player); yield true; }
            case "tpacancel" -> { cancelAllFor(player.getUniqueId()); send(player, "cancelled-all"); yield true; }
            case "tpatoggle" -> { handleToggle(player, args); yield true; }
            case "tpauto" -> { handleAuto(player); yield true; }
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
            send(player, "player-not-found", "player", args[0]);
            return true;
        }
        if (config.getBoolean("gui.enabled", true)) guiHandler.openRequestGui(player, target, type);
        else sendRequest(player, target.getUniqueId(), type);
        return true;
    }

    private void handleAccept(Player player) {
        List<TpaRequest> requests = incoming.get(player.getUniqueId());
        if (requests == null || requests.isEmpty()) {
            send(player, "no-requests");
            return;
        }
        acceptRequest(player, requests.get(requests.size() - 1));
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length == 0) { send(player, "tpatoggle-usage"); return; }
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("tpa") || mode.equals("in")) {
            boolean enabled = !plugin.stateStore().getBool(player.getUniqueId(), STATE_TPA_IN, true);
            plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_IN, enabled);
            send(player, enabled ? "tpa-enabled" : "tpa-disabled");
        } else if (mode.equals("tpahere") || mode.equals("here")) {
            boolean enabled = !plugin.stateStore().getBool(player.getUniqueId(), STATE_TPA_HERE, true);
            plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_HERE, enabled);
            send(player, enabled ? "tpahere-enabled" : "tpahere-disabled");
        } else send(player, "tpatoggle-usage");
    }

    private void handleAuto(Player player) {
        boolean enabled = !plugin.stateStore().getBool(player.getUniqueId(), STATE_TPA_AUTO, false);
        plugin.stateStore().setBool(player.getUniqueId(), STATE_TPA_AUTO, enabled);
        send(player, enabled ? "auto-enabled" : "auto-disabled");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ((cmd.equals("tpa") || cmd.equals("tpahere")) && args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
            return TabCompleteHelper.filter(names, args[0]);
        }
        if (cmd.equals("tpatoggle") && args.length == 1) {
            return TabCompleteHelper.filter(List.of("tpa", "tpahere", "in", "here"), args[0]);
        }
        return List.of();
    }
}
