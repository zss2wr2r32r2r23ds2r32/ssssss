package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.DurationUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Ban, mute, IP ban, wipe, alts, and punish GUI. */
public final class PunishmentManager implements Listener {

    private enum GuiType { PUNISH_MAIN, PUNISH_REASONS, WIPE_CONFIRM }

    private record GuiSession(GuiType type, UUID target, String punishType, String reason, String duration) {
    }

    private final ShardedCore plugin;
    private final StaffModule module;
    private final StaffDatabase database;
    private final Map<UUID, GuiSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public PunishmentManager(ShardedCore plugin, StaffModule module, StaffDatabase database) {
        this.plugin = plugin;
        this.module = module;
        this.database = database;
    }

    public StaffDatabase database() {
        return database;
    }

    public void openPunishMenu(Player staff, Player target) {
        if (!staff.hasPermission("sharded.staff.punish")) {
            module.send(staff, "no-permission");
            return;
        }
        openPunishMain(staff, target.getUniqueId(), target.getName());
    }

    public void openPunishMenu(Player staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.staff.punish")) {
            module.send(staff, "no-permission");
            return;
        }
        openPunishMain(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()));
    }

    private void openPunishMain(Player staff, UUID targetId, String targetName) {
        int rows = 3;
        PunishHolder holder = new PunishHolder(GuiType.PUNISH_MAIN, targetId);
        String title = Text.apply(module.config().getString("punish.menu.title", "&8Punish | %player%"),
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
            case "mute" -> module.config().getConfigurationSection("mute-reasons");
            case "ipban" -> module.config().getConfigurationSection("reasons");
            default -> module.config().getConfigurationSection("reasons");
        };
        if (reasons == null) {
            module.send(staff, "no-reasons");
            return;
        }
        List<String> keys = new ArrayList<>(reasons.getKeys(false));
        int size = Math.min(54, Math.max(27, ((keys.size() + 8) / 9) * 9));
        PunishHolder holder = new PunishHolder(GuiType.PUNISH_REASONS, targetId);
        String title = Text.apply(module.config().getString("punish.reason-title", "&8%type% | %player%"),
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
        ConfigurationSection itemCfg = module.config().getConfigurationSection("punish.reason-item");
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
                    ? module.config().getConfigurationSection("mute-reasons")
                    : module.config().getConfigurationSection("reasons");
            if (reasons == null || !reasons.contains(reason)) return;
            String duration = firstDuration(reasons.get(reason));
            applyFromGui(staff, holder.targetId(), targetName, session.punishType(), reason, duration);
            staff.closeInventory();
        }

        if (holder.type() == GuiType.WIPE_CONFIRM) {
            if (event.getSlot() == module.config().getInt("wipe.items.confirm.slot", 15)) {
                wipePlayer(staff, holder.targetId(), targetName, session.reason());
                staff.closeInventory();
            } else if (event.getSlot() == module.config().getInt("wipe.items.cancel.slot", 11)) {
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
            default -> module.send(staff, "unknown-punish-type");
        }
    }

    public void ban(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.staff.ban")) {
            module.send(staff, "no-permission");
            return;
        }
        Long expiresAt = DurationUtil.expiresAt(durationRaw);
        if (expiresAt != null && expiresAt < 0) {
            module.send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        UUID staffUuid = staff instanceof Player p ? p.getUniqueId() : null;
        database.deactivatePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.BAN);
        String ip = database.latestIp(target.getUniqueId());
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), staffUuid, staffName,
                StaffDatabase.PunishmentType.BAN, reason, expiresAt, ip);
        Player online = target.getPlayer();
        if (online != null) {
            online.kick(buildKickComponent("ban-screen", staffName, reason, expiresAt));
        }
        module.send(staff, "banned", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%reason%", reason, "%duration%", formatDurationLabel(durationRaw, expiresAt));
        broadcastPunishment("Ban", target, staffName, reason);
    }

    public void mute(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.staff.mute")) {
            module.send(staff, "no-permission");
            return;
        }
        Long expiresAt = DurationUtil.expiresAt(durationRaw);
        if (expiresAt != null && expiresAt < 0) {
            module.send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        UUID staffUuid = staff instanceof Player p ? p.getUniqueId() : null;
        database.deactivatePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.MUTE);
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), staffUuid, staffName,
                StaffDatabase.PunishmentType.MUTE, reason, expiresAt, null);
        module.send(staff, "muted", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%reason%", reason, "%duration%", formatDurationLabel(durationRaw, expiresAt));
        Player online = target.getPlayer();
        if (online != null) module.send(online, "muted-target", "%reason%", reason);
        broadcastPunishment("Mute", target, staffName, reason);
    }

    public void banIp(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
        if (!staff.hasPermission("sharded.staff.banip")) {
            module.send(staff, "no-permission");
            return;
        }
        String ip = target.isOnline() && target.getPlayer() != null
                ? target.getPlayer().getAddress().getAddress().getHostAddress()
                : database.latestIp(target.getUniqueId());
        if (ip == null || ip.isBlank()) {
            module.send(staff, "no-ip");
            return;
        }
        Long expiresAt = DurationUtil.expiresAt(durationRaw);
        if (expiresAt != null && expiresAt < 0) {
            module.send(staff, "invalid-duration");
            return;
        }
        String staffName = staff.getName() == null ? "Console" : staff.getName();
        database.addIpBan(ip, reason, staffName, expiresAt);
        database.addPunishment(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()),
                staff instanceof Player p ? p.getUniqueId() : null, staffName,
                StaffDatabase.PunishmentType.IP_BAN, reason, expiresAt, ip);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String playerIp = online.getAddress() == null ? null : online.getAddress().getAddress().getHostAddress();
            if (ip.equals(playerIp)) {
                online.kick(buildKickComponent("ban-screen", staffName, reason, expiresAt));
            }
        }
        module.send(staff, "ip-banned", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%ip%", ip, "%reason%", reason);
    }

    public void unban(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.staff.unban")) {
            module.send(staff, "no-permission");
            return;
        }
        database.deactivatePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.BAN);
        String ip = database.latestIp(target.getUniqueId());
        if (ip != null) database.deactivateIpBan(ip);
        module.send(staff, "unbanned", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void unmute(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.staff.unmute")) {
            module.send(staff, "no-permission");
            return;
        }
        database.deactivatePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.MUTE);
        module.send(staff, "unmuted", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void pardon(CommandSender staff, OfflinePlayer target) {
        if (!staff.hasPermission("sharded.staff.pardon")) {
            module.send(staff, "no-permission");
            return;
        }
        unban(staff, target);
        unmute(staff, target);
        module.send(staff, "pardoned", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void showOffenses(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission("sharded.staff.offend")) {
            module.send(sender, "no-permission");
            return;
        }
        int bans = database.countActivePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.BAN);
        int mutes = database.countActivePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.MUTE);
        int warns = database.countActivePunishments(target.getUniqueId(), StaffDatabase.PunishmentType.WARN);
        module.send(sender, "offenses", "%player%", OfflinePlayers.name(target.getUniqueId()),
                "%bans%", String.valueOf(bans), "%mutes%", String.valueOf(mutes), "%warns%", String.valueOf(warns));
        int threshold = module.config().getInt("repeat-offender-threshold", 3);
        if (bans >= threshold) module.send(sender, "repeat-offender", "%player%", OfflinePlayers.name(target.getUniqueId()));
    }

    public void showAlts(CommandSender sender, OfflinePlayer target) {
        if (!sender.hasPermission("sharded.staff.alts")) {
            module.send(sender, "no-permission");
            return;
        }
        String ip = target.isOnline() && target.getPlayer() != null
                ? target.getPlayer().getAddress().getAddress().getHostAddress()
                : database.latestIp(target.getUniqueId());
        if (ip == null) {
            module.send(sender, "no-ip");
            return;
        }
        List<String> alts = database.findAlts(ip, target.getUniqueId());
        module.send(sender, "alts-header", "%player%", OfflinePlayers.name(target.getUniqueId()), "%ip%", ip);
        if (alts.isEmpty()) module.send(sender, "alts-none");
        else {
            int max = module.config().getInt("alts.max-display", 50);
            for (int i = 0; i < Math.min(max, alts.size()); i++) {
                module.send(sender, "alts-entry", "%name%", alts.get(i));
            }
        }
    }

    public void openWipeConfirm(Player staff, OfflinePlayer target, String reasonKey) {
        if (!staff.hasPermission("sharded.staff.wipe")) {
            module.send(staff, "no-permission");
            return;
        }
        int rows = module.config().getInt("wipe.rows", 3);
        PunishHolder holder = new PunishHolder(GuiType.WIPE_CONFIRM, target.getUniqueId());
        String title = Text.apply(module.config().getString("wipe.title", "&8Wipe | %player%"),
                "%player%", OfflinePlayers.name(target.getUniqueId()));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        fillWipe(inv, OfflinePlayers.name(target.getUniqueId()));
        staff.openInventory(inv);
        sessions.put(staff.getUniqueId(), new GuiSession(GuiType.WIPE_CONFIRM, target.getUniqueId(), null, reasonKey, null));
    }

    public void wipePlayer(CommandSender staff, UUID targetId, String targetName, String reasonKey) {
        if (!staff.hasPermission("sharded.staff.wipe")) {
            module.send(staff, "no-permission");
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
        module.send(staff, "wiped", "%player%", targetName, "%reason%", reasonKey == null ? "default" : reasonKey);
    }

    private void killstreakReset(UUID uuid) {
        var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
        if (ks != null && ks.database() != null) ks.database().reset(uuid);
    }

    private List<String> wipeKickScreen(String reasonKey) {
        ConfigurationSection reasons = module.config().getConfigurationSection("wipe.reasons");
        if (reasonKey != null && reasons != null && reasons.isList(reasonKey)) {
            return reasons.getStringList(reasonKey);
        }
        return module.config().getStringList("wipe.kick-screen");
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
                ? module.config().getStringList("ban-screen-permanent")
                : module.config().getStringList(configKey);
        if (lines.isEmpty()) lines = module.config().getStringList(configKey);
        List<Component> parts = new ArrayList<>();
        for (String line : lines) {
            parts.add(Text.c(Text.apply(line,
                    "%staff%", staff,
                    "%reason%", reason,
                    "%time_left%", expiresAt == null ? "Permanent" : DurationUtil.formatRemaining(expiresAt),
                    "%expires%", expiresAt == null ? "Never" : DurationUtil.formatExpires(expiresAt),
                    "%discord%", module.config().getString("discord", "discord.gg/shardedmc"))));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), parts);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        InetAddress address = event.getAddress();
        String ip = address == null ? null : address.getHostAddress();
        if (ip != null) {
            StaffDatabase.PunishmentRecord ipBan = database.getActiveIpBan(ip);
            if (ipBan != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        buildKickComponent("ban-screen", ipBan.staffName(), ipBan.reason(), ipBan.expiresAt()));
                return;
            }
        }
        StaffDatabase.PunishmentRecord ban = database.getActive(event.getUniqueId(), StaffDatabase.PunishmentType.BAN);
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    buildKickComponent("ban-screen", ban.staffName(), ban.reason(), ban.expiresAt()));
        }
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
    public void onChat(AsyncChatEvent event) {
        StaffDatabase.PunishmentRecord mute = database.getActive(event.getPlayer().getUniqueId(), StaffDatabase.PunishmentType.MUTE);
        if (mute == null) return;
        event.setCancelled(true);
        module.send(event.getPlayer(), "muted-chat", "%reason%", mute.reason());
    }

    private void broadcastPunishment(String action, OfflinePlayer target, String staff, String reason) {
        if (!module.config().getBoolean("public-broadcast.enabled", true)) return;
        List<String> actions = module.config().getStringList("public-broadcast.actions");
        if (!actions.contains(action)) return;
        String msg = module.raw("broadcast-punish",
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
        if (!module.config().getBoolean("punish.menu.filler.enabled", true)) return;
        Material material = Material.matchMaterial(module.config().getString("punish.menu.filler.material", "GRAY_STAINED_GLASS_PANE"));
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(material).name(" ").hideAll().build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void fillWipe(Inventory inv, String targetName) {
        ConfigurationSection wipe = module.config().getConfigurationSection("wipe");
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
        ConfigurationSection section = module.config().getConfigurationSection("wipe.items." + key);
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
        ConfigurationSection section = module.config().getConfigurationSection(path);
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
        ConfigurationSection section = module.config().getConfigurationSection("punish.menu.target");
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
        ConfigurationSection section = module.config().getConfigurationSection(path);
        Material material = Material.matchMaterial(section == null ? "BARRIER" : section.getString("material", "BARRIER"));
        if (material == null) material = Material.BARRIER;
        return new ItemBuilder(material)
                .name(section == null ? "Back" : section.getString("display_name", "Back"))
                .lore(section == null ? List.of() : section.getStringList("lore"))
                .hideAll()
                .build();
    }

    public List<String> banReasons() {
        ConfigurationSection section = module.config().getConfigurationSection("reasons");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public List<String> muteReasons() {
        ConfigurationSection section = module.config().getConfigurationSection("mute-reasons");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public List<String> wipeReasons() {
        ConfigurationSection section = module.config().getConfigurationSection("wipe.reasons");
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
