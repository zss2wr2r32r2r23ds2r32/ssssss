package com.sharded.core.modules.punishments;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.util.DurationUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Ban, mute, kick, IP ban, wipe, alts, and punish GUI — overrides other plugins. */
public final class PunishmentsModule extends Module implements CommandExecutor, TabCompleter {

    private enum GuiType { PUNISH_MAIN, PUNISH_REASONS, WIPE_CONFIRM }

    private record GuiSession(GuiType type, UUID target, String punishType, String reason, String duration) {
    }

    private PunishmentDatabase database;
    private final Map<UUID, GuiSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public PunishmentsModule(ShardedCore plugin) {
        super(plugin, "punishments");
    }

    public PunishmentDatabase database() {
        return database;
    }

    @Override
    protected void onEnable() {
        try {
            database = new PunishmentDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open punishments database", e);
        }
        registerListener(this);
        registerCommand("punish", this);
        registerCommand("ban", this);
        registerCommand("banip", this);
        registerCommand("kick", this);
        registerCommand("offend", this);
        registerCommand("unban", this);
        registerCommand("unmute", this);
        registerCommand("pardon", this);
        registerCommand("wipe", this);
        registerCommand("alts", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
        sessions.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "punish" -> handlePunishCmd(sender, args);
            case "ban" -> handleBanCmd(sender, args);
            case "banip" -> handleBanIpCmd(sender, args);
            case "kick" -> handleKickCmd(sender, args);
            case "offend" -> handleOffendCmd(sender, args);
            case "unban" -> handleUnbanCmd(sender, args);
            case "unmute" -> handleUnmuteCmd(sender, args);
            case "pardon" -> handlePardonCmd(sender, args);
            case "wipe" -> handleWipeCmd(sender, args);
            case "alts" -> handleAltsCmd(sender, args);
            default -> false;
        };
    }

    private boolean handlePunishCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(staff, "punish-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(staff, "player-not-found");
            return true;
        }
        openPunishMenu(staff, target);
        return true;
    }

    private boolean handleBanCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "ban-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? joinArgs(args, 1, args.length - (args.length >= 3 ? 1 : 0))
                : config.getString("default-reason", "Unfair Modifications");
        String duration = args.length >= 3 ? args[args.length - 1] : defaultDuration("reasons", reason);
        if (args.length == 2 && config.getConfigurationSection("reasons") != null
                && config.getConfigurationSection("reasons").contains(reason)) {
            duration = defaultDuration("reasons", reason);
        }
        ban(sender, target, reason, duration);
        return true;
    }

    private boolean handleBanIpCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "banip-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : config.getString("default-reason", "Unfair Modifications");
        String duration = args.length >= 3 ? args[2] : defaultDuration("reasons", reason);
        banIp(sender, target, reason, duration);
        return true;
    }

    private boolean handleKickCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "kick-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? joinArgs(args, 1)
                : config.getString("kick.default-reason", "No reason specified");
        kick(sender, target, reason);
        return true;
    }

    private boolean handleOffendCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "offend-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }
        showOffenses(sender, target);
        return true;
    }

    private boolean handleUnbanCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "unban-usage");
            return true;
        }
        unban(sender, OfflinePlayers.resolve(args[0]));
        return true;
    }

    private boolean handleUnmuteCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "unmute-usage");
            return true;
        }
        unmute(sender, OfflinePlayers.resolve(args[0]));
        return true;
    }

    private boolean handlePardonCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "pardon-usage");
            return true;
        }
        pardon(sender, OfflinePlayers.resolve(args[0]));
        return true;
    }

    private boolean handleWipeCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(staff, "wipe-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(staff, "player-not-found");
            return true;
        }
        String reason = args.length >= 2 ? args[1] : "default";
        if (config.getBoolean("wipe.confirm-gui", true)) openWipeConfirm(staff, target, reason);
        else wipePlayer(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), reason);
        return true;
    }

    private boolean handleAltsCmd(CommandSender sender, String[] args) {
        OfflinePlayer target = args.length == 0 && sender instanceof Player p ? p : OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, args.length == 0 ? "alts-usage" : "player-not-found");
            return true;
        }
        showAlts(sender, target);
        return true;
    }

    private String joinArgs(String[] args, int from) {
        return joinArgs(args, from, args.length);
    }

    private String joinArgs(String[] args, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return TabCompleteHelper.knownPlayers(args[0]);
        }
        if (args.length == 2) {
            return switch (name) {
                case "ban", "banip" -> TabCompleteHelper.configKeys(args[1], banReasons());
                case "kick" -> TabCompleteHelper.configKeys(args[1], kickReasons());
                case "wipe" -> TabCompleteHelper.configKeys(args[1], wipeReasons());
                default -> List.of();
            };
        }
        return List.of();
    }

    private String defaultDuration(String sectionPath, String reason) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null || !section.contains(reason)) return "permanent";
        Object raw = section.get(reason);
        if (raw instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return raw == null ? "permanent" : String.valueOf(raw);
    }

    public List<String> kickReasons() {
        return config.getStringList("kick-reasons");
    }

    public void openPunishMenu(Player staff, Player target) {
        if (!staff.hasPermission("sharded.punishments.punish")) {
            send(staff, "no-permission");
            return;
        }
        openPunishMain(staff, target.getUniqueId(), target.getName());
    }

    public void openPunishMenu(Player staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.punishments.punish")) {
            send(staff, "no-permission");
            return;
        }
        openPunishMain(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()));
    }

    private void openPunishMain(Player staff, UUID targetId, String targetName) {
        int rows = 3;
        PunishHolder holder = new PunishHolder(GuiType.PUNISH_MAIN, targetId);
        String title = Text.apply(config.getString("punish.menu.title", "&8Punish | %player%"),
                "%player%", targetName);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        fill(inv);
        inv.setItem(11, menuItem("punish.menu.ban", targetName, "ban"));
        inv.setItem(13, head(targetName));
        inv.setItem(15, menuItem("punish.menu.mute", targetName, "mute"));
        inv.setItem(16, menuItem("punish.menu.ipban", targetName, "ipban"));
        staff.openInventory(inv);
        sessions.put(staff.getUniqueId(), new GuiSession(GuiType.PUNISH_MAIN, targetId, null, null, null));
    }

    private void openReasonMenu(Player staff, UUID targetId, String targetName, String type) {
        ConfigurationSection reasons = switch (type.toLowerCase(Locale.ROOT)) {
            case "mute" -> config.getConfigurationSection("mute-reasons");
            case "ipban" -> config.getConfigurationSection("reasons");
            default -> config.getConfigurationSection("reasons");
        };
        if (reasons == null) {
            send(staff, "no-reasons");
            return;
        }
        List<String> keys = new ArrayList<>(reasons.getKeys(false));
        int size = Math.min(54, Math.max(27, ((keys.size() + 8) / 9) * 9));
        PunishHolder holder = new PunishHolder(GuiType.PUNISH_REASONS, targetId);
        String title = Text.apply(config.getString("punish.reason-title", "&8%type% | %player%"),
                "%type%", type.toUpperCase(Locale.ROOT), "%player%", targetName);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(title));
        fill(inv);
        int slot = 0;
        for (String reason : keys) {
            if (slot >= size - 9) break;
            inv.setItem(slot++, reasonItem(reason, reasons.get(reason), targetName, type));
        }
        inv.setItem(size - 5, navItem("punish.back"));
        staff.openInventory(inv);
        sessions.put(staff.getUniqueId(), new GuiSession(GuiType.PUNISH_REASONS, targetId, type, null, null));
    }

    private ItemStack reasonItem(String reason, Object durations, String targetName, String type) {
        ConfigurationSection itemCfg = config.getConfigurationSection("punish.reason-item");
        Material material = Material.matchMaterial(itemCfg == null ? "PAPER" : itemCfg.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;
        List<String> durationLines = formatDurationChoices(durations);
        String durationLine = durationLines.isEmpty() ? "Permanent" : String.join(", ", durationLines);
        List<String> lore = new ArrayList<>();
        for (String line : itemCfg == null ? List.of("&7Click to apply") : itemCfg.getStringList("lore")) {
            lore.add(line.replace("%durations%", String.join("\n", durationLines))
                    .replace("%duration%", durationLine)
                    .replace("%reason%", reason)
                    .replace("%player%", targetName)
                    .replace("%type%", type));
        }
        return new ItemBuilder(material)
                .name((itemCfg == null ? "&#00FFAA%reason%" : itemCfg.getString("display_name", "&#00FFAA%reason%"))
                        .replace("%reason%", reason))
                .lore(lore)
                .hideAll()
                .build();
    }

    private List<String> formatDurationChoices(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) out.add(String.valueOf(entry));
        } else if (raw != null) {
            out.add(String.valueOf(raw));
        }
        return out;
    }

    private String firstDuration(Object raw) {
        List<String> choices = formatDurationChoices(raw);
        return choices.isEmpty() ? "permanent" : choices.get(0);
    }

    @EventHandler
    public void onGuiClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PunishHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        GuiSession session = sessions.get(staff.getUniqueId());
        if (session == null) return;
        String targetName = OfflinePlayers.name(holder.targetId());
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (holder.type() == GuiType.PUNISH_MAIN) {
            int slot = event.getSlot();
            if (slot == 11) openReasonMenu(staff, holder.targetId(), targetName, "ban");
            else if (slot == 15) openReasonMenu(staff, holder.targetId(), targetName, "mute");
            else if (slot == 16) openReasonMenu(staff, holder.targetId(), targetName, "ipban");
            return;
        }

        if (holder.type() == GuiType.PUNISH_REASONS) {
            if (event.getSlot() == event.getView().getTopInventory().getSize() - 5) {
                openPunishMain(staff, holder.targetId(), targetName);
                return;
            }
            String reason = clicked.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(clicked.getItemMeta().displayName())
                    : null;
            if (reason == null || reason.isBlank()) return;
            reason = ColorUtil.normalize(reason).replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]", "").trim();
            ConfigurationSection reasons = "mute".equalsIgnoreCase(session.punishType())
                    ? config.getConfigurationSection("mute-reasons")
                    : config.getConfigurationSection("reasons");
            if (reasons == null || !reasons.contains(reason)) return;
            String duration = firstDuration(reasons.get(reason));
            applyFromGui(staff, holder.targetId(), targetName, session.punishType(), reason, duration);
            staff.closeInventory();
        }

        if (holder.type() == GuiType.WIPE_CONFIRM) {
            if (event.getSlot() == config.getInt("wipe.items.confirm.slot", 15)) {
                wipePlayer(staff, holder.targetId(), targetName, session.reason());
                staff.closeInventory();
            } else if (event.getSlot() == config.getInt("wipe.items.cancel.slot", 11)) {
                staff.closeInventory();
            }
        }
    }

    private void applyFromGui(Player staff, UUID targetId, String targetName, String type, String reason, String duration) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "ban" -> ban(staff, target, reason, duration);
            case "mute" -> mute(staff, target, reason, duration);
            case "ipban" -> banIp(staff, target, reason, duration);
            default -> send(staff, "unknown-punish-type");
        }
    }

    public void kick(CommandSender staff, Player target, String reason) {
        if (!staff.hasPermission("sharded.punishments.kick")) {
            send(staff, "no-permission");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        UUID staffUuid = staff instanceof Player p ? p.getUniqueId() : null;
        database.addPunishment(target.getUniqueId(), target.getName(), staffUuid, staffName,
                PunishmentDatabase.PunishmentType.KICK, reason, null, null, false);
        target.kick(buildKickScreen("kick-screen", staffName, reason));
        send(staff, "kicked", "%player%", target.getName(), "%reason%", reason);
    }

    public void ban(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.punishments.ban")) {
            send(staff, "no-permission");
            return;
        }
        boolean doxxed = isDoxxingReason(reason);
        if (doxxed) durationRaw = "permanent";
        Long expiresAt = doxxed ? null : DurationUtil.expiresAt(durationRaw);
        if (!doxxed && expiresAt != null && expiresAt < 0) {
            send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        UUID staffUuid = staff instanceof Player p ? p.getUniqueId() : null;
        database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
        String ip = database.latestIp(target.getUniqueId());
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), staffUuid, staffName,
                PunishmentDatabase.PunishmentType.BAN, reason, expiresAt, ip, doxxed);
        if (doxxed) database.markDoxxed(target.getUniqueId());
        Player online = target.getPlayer();
        if (online != null) {
            online.kick(buildKickComponent("ban-screen", staffName, reason, expiresAt));
        }
        send(staff, "banned", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%reason%", reason, "%duration%", formatDurationLabel(durationRaw, expiresAt));
        broadcastPunishment("Ban", target, staffName, reason);
        if (doxxed) sendDoxxingWebhook(target, staffName, reason);
    }

    private boolean isDoxxingReason(String reason) {
        if (reason == null) return false;
        for (String entry : config.getStringList("doxxing-reasons")) {
            if (reason.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }

    private void sendDoxxingWebhook(OfflinePlayer target, String staffName, String reason) {
        if (!config.getBoolean("doxxing-webhook.enabled", false)) return;
        String url = config.getString("doxxing-webhook.url", "");
        if (url.isBlank()) return;
        String name = OfflinePlayers.name(target.getUniqueId());
        String thumb = config.getString("doxxing-webhook.thumbnail-url", "https://mc-heads.net/avatar/%uuid%")
                .replace("%uuid%", target.getUniqueId().toString())
                .replace("%player%", name);
        String rebancmd = config.getString("doxxing-webhook.reban-command", "/ban %player% Doxxing permanent")
                .replace("%player%", name);
        List<DiscordWebhook.Field> fields = List.of(
                new DiscordWebhook.Field("Player", name, true),
                new DiscordWebhook.Field("UUID", target.getUniqueId().toString(), true),
                new DiscordWebhook.Field("Staff", staffName, true),
                new DiscordWebhook.Field("Reason", reason, false),
                new DiscordWebhook.Field("Re-ban", rebancmd, false)
        );
        DiscordWebhook.sendEmbedAsync(plugin.getLogger(), url,
                config.getString("doxxing-webhook.title", "Doxxing Ban"),
                config.getString("doxxing-webhook.description", "%player% was permanently banned for doxxing.")
                        .replace("%player%", name).replace("%staff%", staffName).replace("%reason%", reason),
                (int) config.getLong("doxxing-webhook.color", 0xFF0000),
                thumb,
                config.getString("doxxing-webhook.footer", "ShardedCore Punishments"),
                fields);
    }

    public void mute(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.punishments.mute")) {
            send(staff, "no-permission");
            return;
        }
        Long expiresAt = DurationUtil.expiresAt(durationRaw);
        if (expiresAt != null && expiresAt < 0) {
            send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        UUID staffUuid = staff instanceof Player p ? p.getUniqueId() : null;
        database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), staffUuid, staffName,
                PunishmentDatabase.PunishmentType.MUTE, reason, expiresAt, null, false);
        send(staff, "muted", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%reason%", reason, "%duration%", formatDurationLabel(durationRaw, expiresAt));
        Player online = target.getPlayer();
        if (online != null) send(online, "muted-target", "%reason%", reason);
        broadcastPunishment("Mute", target, staffName, reason);
    }

    public void banIp(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.punishments.banip")) {
            send(staff, "no-permission");
            return;
        }
        String ip = target.isOnline() && target.getPlayer() != null
                ? target.getPlayer().getAddress().getAddress().getHostAddress()
                : database.latestIp(target.getUniqueId());
        if (ip == null || ip.isBlank()) {
            send(staff, "no-ip");
            return;
        }
        Long expiresAt = DurationUtil.expiresAt(durationRaw);
        if (expiresAt != null && expiresAt < 0) {
            send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        database.addIpBan(ip, reason, staffName, expiresAt);
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()),
                staff instanceof Player p ? p.getUniqueId() : null, staffName,
                PunishmentDatabase.PunishmentType.IP_BAN, reason, expiresAt, ip, false);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String playerIp = online.getAddress() == null ? null : online.getAddress().getAddress().getHostAddress();
            if (ip.equals(playerIp)) {
                online.kick(buildKickComponent("ban-screen", staffName, reason, expiresAt));
            }
        }
        send(staff, "ip-banned", "%player%", OfflinePlayers.name(target.getUniqueId()), "%reason%", reason);
    }

    public void unban(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.punishments.unban")) {
            send(staff, "no-permission");
            return;
        }
        database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
        String ip = database.latestIp(target.getUniqueId());
        if (ip != null) database.deactivateIpBan(ip);
        send(staff, "unbanned", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void unmute(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.punishments.unmute")) {
            send(staff, "no-permission");
            return;
        }
        database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
        send(staff, "unmuted", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void pardon(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.punishments.pardon")) {
            send(staff, "no-permission");
            return;
        }
        unban(staff, target);
        unmute(staff, target);
        send(staff, "pardoned", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void showOffenses(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission("sharded.punishments.offend")) {
            send(sender, "no-permission");
            return;
        }
        int bans = database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
        int mutes = database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
        int warns = database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.WARN);
        send(sender, "offenses", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%bans%", String.valueOf(bans), "%mutes%", String.valueOf(mutes), "%warns%", String.valueOf(warns));
        int threshold = config.getInt("repeat-offender-threshold", 3);
        if (bans >= threshold) send(sender, "repeat-offender", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void showAlts(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission("sharded.punishments.alts")) {
            send(sender, "no-permission");
            return;
        }
        String ip = target.isOnline() && target.getPlayer() != null
                ? target.getPlayer().getAddress().getAddress().getHostAddress()
                : database.latestIp(target.getUniqueId());
        if (ip == null) {
            send(sender, "no-ip");
            return;
        }
        List<PunishmentDatabase.AltAccount> alts = database.findAlts(ip, target.getUniqueId());
        send(sender, "alts-header", "%player%", OfflinePlayers.name(target.getUniqueId()));
        if (alts.isEmpty()) send(sender, "alts-none");
        else {
            int max = config.getInt("alts.max-display", 50);
            for (int i = 0; i < Math.min(max, alts.size()); i++) {
                PunishmentDatabase.AltAccount alt = alts.get(i);
                boolean online = Bukkit.getPlayer(alt.uuid()) != null;
                send(sender, "alts-entry", "%name%", alt.name(),
                        "%status%", online ? raw("alts-online") : raw("alts-offline"));
            }
        }
    }

    public void openWipeConfirm(Player staff, OfflinePlayer target, String reasonKey) {
        if (!staff.hasPermission("sharded.punishments.wipe")) {
            send(staff, "no-permission");
            return;
        }
        int rows = config.getInt("wipe.rows", 3);
        PunishHolder holder = new PunishHolder(GuiType.WIPE_CONFIRM, target.getUniqueId());
        String title = Text.apply(config.getString("wipe.title", "&8Wipe | %player%"),
                "%player%", OfflinePlayers.name(target.getUniqueId()));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        fillWipe(inv, OfflinePlayers.name(target.getUniqueId()));
        staff.openInventory(inv);
        sessions.put(staff.getUniqueId(), new GuiSession(GuiType.WIPE_CONFIRM, target.getUniqueId(), null, reasonKey, null));
    }

    public void wipePlayer(CommandSender staff, UUID targetId, String targetName, String reasonKey) {
        if (!staff.hasPermission("sharded.punishments.wipe")) {
            send(staff, "no-permission");
            return;
        }
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            online.getInventory().clear();
            online.getEnderChest().clear();
            online.setLevel(0);
            online.setExp(0);
            online.getActivePotionEffects().forEach(effect -> online.removePotionEffect(effect.getType()));
            for (org.bukkit.Statistic stat : org.bukkit.Statistic.values()) {
                try {
                    online.setStatistic(stat, 0);
                } catch (Exception ignored) {
                }
            }
            SpawnSelectModule spawn = plugin.modules().get(SpawnSelectModule.class);
            if (spawn != null) {
                org.bukkit.Location main = spawn.mainSpawn();
                if (main != null) online.teleport(main);
            }
            List<String> screen = wipeKickScreen(reasonKey);
            online.kick(buildLines(screen, targetName, staff.getName(), reasonKey));
        }
        plugin.stateStore().clear(targetId);
        if (plugin.modules().tokens() != null) plugin.modules().tokens().reset(targetId);
        killstreakReset(targetId);
        send(staff, "wiped", "%player%", targetName, "%reason%", reasonKey == null ? "default" : reasonKey);
    }

    private void killstreakReset(UUID uuid) {
        var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
        if (ks != null && ks.database() != null) ks.database().reset(uuid);
    }

    private List<String> wipeKickScreen(String reasonKey) {
        ConfigurationSection reasons = config.getConfigurationSection("wipe.reasons");
        if (reasonKey != null && reasons != null && reasons.isList(reasonKey)) {
            return reasons.getStringList(reasonKey);
        }
        return config.getStringList("wipe.kick-screen");
    }

    private Component buildLines(List<String> lines, String player, String staff, String reason) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(Text.c(Text.apply(line,
                    "%player%", player,
                    "%staff%", staff == null ? "Staff" : staff,
                    "%reason%", reason == null ? "" : reason)));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), components);
    }

    private Component buildKickComponent(String configKey, String staff, String reason, Long expiresAt) {
        List<String> lines = DurationUtil.isPermanent(String.valueOf(expiresAt))
                || expiresAt == null
                ? config.getStringList("ban-screen-permanent")
                : config.getStringList(configKey);
        if (lines.isEmpty()) lines = config.getStringList(configKey);
        List<Component> parts = new ArrayList<>();
        for (String line : lines) {
            parts.add(Text.c(Text.apply(line,
                    "%staff%", staff,
                    "%reason%", reason,
                    "%time_left%", expiresAt == null ? "Permanent" : DurationUtil.formatRemaining(expiresAt),
                    "%expires%", expiresAt == null ? "Never" : DurationUtil.formatExpires(expiresAt),
                    "%discord%", config.getString("discord", "discord.gg/shardedmc"))));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    private Component buildKickScreen(String configKey, String staff, String reason) {
        List<String> lines = config.getStringList(configKey);
        List<Component> parts = new ArrayList<>();
        for (String line : lines) {
            parts.add(Text.c(Text.apply(line,
                    "%staff%", staff,
                    "%reason%", reason,
                    "%discord%", config.getString("discord", ".gg/shardedmc"))));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (database.isDoxxed(event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    buildBanEvasionScreen("doxxing-deny-screen"));
            return;
        }
        InetAddress address = event.getAddress();
        String ip = address == null ? null : address.getHostAddress();
        if (ip != null) {
            PunishmentDatabase.PunishmentRecord ipBan = database.getActiveIpBan(ip);
            if (ipBan != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        buildKickComponent("ban-screen", ipBan.staffName(), ipBan.reason(), ipBan.expiresAt()));
                return;
            }
            if (config.getBoolean("ban-evasion.enabled", true)
                    && database.hasBannedAltOnIp(ip, event.getUniqueId())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        buildBanEvasionScreen("ban-evasion-screen"));
                return;
            }
        }
        PunishmentDatabase.PunishmentRecord ban = database.getActive(event.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    buildKickComponent("ban-screen", ban.staffName(), ban.reason(), ban.expiresAt()));
        }
    }

    private Component buildBanEvasionScreen(String key) {
        List<String> lines = config.getStringList(key);
        List<Component> parts = new ArrayList<>();
        for (String line : lines) {
            parts.add(Text.c(Text.apply(line, "%discord%", config.getString("discord", ".gg/shardedmc"))));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getAddress() != null) {
            database.recordIp(player.getUniqueId(), player.getName(),
                    player.getAddress().getAddress().getHostAddress());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMutedCommand(PlayerCommandPreprocessEvent event) {
        PunishmentDatabase.PunishmentRecord mute = database.getActive(
                event.getPlayer().getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
        if (mute == null) return;
        String label = event.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        for (String blocked : config.getStringList("mute.blocked-commands")) {
            if (label.equalsIgnoreCase(blocked)) {
                event.setCancelled(true);
                send(event.getPlayer(), "muted-command", "%command%", label);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PunishmentDatabase.PunishmentRecord mute = database.getActive(event.getPlayer().getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
        if (mute == null) return;
        event.setCancelled(true);
        send(event.getPlayer(), "muted-chat", "%reason%", mute.reason());
    }

    private void broadcastPunishment(String action, OfflinePlayer target, String staff, String reason) {
        if (!config.getBoolean("public-broadcast.enabled", true)) return;
        List<String> actions = config.getStringList("public-broadcast.actions");
        if (!actions.contains(action)) return;
        String msg = raw("broadcast-punish",
                "%action%", action,
                "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%staff%", staff,
                "%reason%", reason);
        Bukkit.broadcast(Text.c(msg));
    }

    private String formatDurationLabel(String raw, Long expiresAt) {
        if (expiresAt == null) return "Permanent";
        if (DurationUtil.isPermanent(raw)) return "Permanent";
        return DurationUtil.formatRemaining(expiresAt);
    }

    private void fill(Inventory inv) {
        if (!config.getBoolean("punish.menu.filler.enabled", true)) return;
        Material material = Material.matchMaterial(config.getString("punish.menu.filler.material", "GRAY_STAINED_GLASS_PANE"));
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(material).name(" ").hideAll().build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void fillWipe(Inventory inv, String targetName) {
        ConfigurationSection wipe = config.getConfigurationSection("wipe");
        if (wipe == null) return;
        if (wipe.getBoolean("filler.enabled", true)) {
            Material material = Material.matchMaterial(wipe.getString("filler.material", "GRAY_STAINED_GLASS_PANE"));
            if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack filler = new ItemBuilder(material).name(" ").hideAll().build();
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }
        putWipeItem(inv, "cancel", targetName);
        putWipeItem(inv, "info", targetName);
        putWipeItem(inv, "confirm", targetName);
    }

    private void putWipeItem(Inventory inv, String key, String targetName) {
        ConfigurationSection section = config.getConfigurationSection("wipe.items." + key);
        if (section == null) return;
        Material material = Material.matchMaterial(section.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) lore.add(line.replace("%player%", targetName));
        ItemStack item = new ItemBuilder(material)
                .name(section.getString("display_name", key).replace("%player%", targetName))
                .lore(lore)
                .hideAll()
                .build();
        if ("info".equals(key) && item.getItemMeta() instanceof SkullMeta meta) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            meta.setOwningPlayer(offline);
            item.setItemMeta(meta);
        }
        inv.setItem(section.getInt("slot", 0), item);
    }

    private ItemStack menuItem(String path, String targetName, String type) {
        ConfigurationSection section = config.getConfigurationSection(path);
        Material material = Material.matchMaterial(section == null ? "PAPER" : section.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;
        List<String> lore = new ArrayList<>();
        if (section != null) {
            for (String line : section.getStringList("lore")) lore.add(line.replace("%player%", targetName));
        }
        return new ItemBuilder(material)
                .name(section == null ? type : section.getString("display_name", type).replace("%player%", targetName))
                .lore(lore)
                .hideAll()
                .build();
    }

    private ItemStack head(String playerName) {
        ConfigurationSection section = config.getConfigurationSection("punish.menu.target");
        Material material = Material.PLAYER_HEAD;
        ItemStack item = new ItemBuilder(material)
                .name(section == null ? playerName : section.getString("display_name", playerName).replace("%player%", playerName))
                .lore(section == null ? List.of() : section.getStringList("lore"))
                .hideAll()
                .build();
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        Material material = Material.matchMaterial(section == null ? "BARRIER" : section.getString("material", "BARRIER"));
        if (material == null) material = Material.BARRIER;
        return new ItemBuilder(material)
                .name(section == null ? "Back" : section.getString("display_name", "Back"))
                .lore(section == null ? List.of() : section.getStringList("lore"))
                .hideAll()
                .build();
    }

    public List<String> banReasons() {
        ConfigurationSection section = config.getConfigurationSection("reasons");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public List<String> muteReasons() {
        ConfigurationSection section = config.getConfigurationSection("mute-reasons");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public List<String> wipeReasons() {
        ConfigurationSection section = config.getConfigurationSection("wipe.reasons");
        return section == null ? List.of("default") : new ArrayList<>(section.getKeys(false));
    }

    private static final class PunishHolder implements InventoryHolder {
        private final GuiType type;
        private final UUID targetId;

        PunishHolder(GuiType type, UUID targetId) {
            this.type = type;
            this.targetId = targetId;
        }

        GuiType type() {
            return type;
        }

        UUID targetId() {
            return targetId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
