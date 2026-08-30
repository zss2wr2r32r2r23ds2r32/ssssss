package com.shardedcore.modules.teams;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TeamsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<UUID, String> teamChat = new ConcurrentHashMap<>();
    private final Map<UUID, String> creating = new ConcurrentHashMap<>();
    private final Map<UUID, String> renaming = new ConcurrentHashMap<>();
    private final Map<UUID, Long> emergency = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingHome = new ConcurrentHashMap<>();

    public TeamsModule(ShardedCore plugin) {
        super(plugin, "teams");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS teams (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS team_members (
                        uuid TEXT PRIMARY KEY,
                        team_id TEXT NOT NULL,
                        role TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS team_invites (
                        team_id TEXT NOT NULL,
                        uuid TEXT NOT NULL,
                        expires INTEGER NOT NULL,
                        PRIMARY KEY (team_id, uuid)
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS team_allies (
                        a TEXT NOT NULL,
                        b TEXT NOT NULL,
                        status TEXT NOT NULL,
                        expires INTEGER NOT NULL,
                        PRIMARY KEY (a, b)
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS team_enderchest (
                        team_id TEXT NOT NULL,
                        slot INTEGER NOT NULL,
                        item TEXT NOT NULL,
                        PRIMARY KEY (team_id, slot)
                    )
                    """);
            try { sqlite.run("ALTER TABLE teams ADD COLUMN pvp INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { sqlite.run("ALTER TABLE teams ADD COLUMN home TEXT"); } catch (SQLException ignored) {}
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create team tables", ex);
        }
        registerCommand("team", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        teamChat.clear();
        creating.clear();
        renaming.clear();
        pendingHome.values().forEach(BukkitTask::cancel);
        pendingHome.clear();
        cleanup();
    }

    public String placeholder(Player player) {
        String name = teamName(player.getUniqueId());
        if (name == null || name.isBlank()) return cfg("placeholders.not-in-team", "N/A");
        return name;
    }

    public String leaderboardPlaceholder(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) return cfg("placeholders.not-in-team", "N/A");
        return rank(team);
    }

    public String teamKills(Player player) {
        String team = teamId(player.getUniqueId());
        return team == null ? "0" : String.valueOf(stats(team).kills);
    }

    public String teamPlaytime(Player player) {
        String team = teamId(player.getUniqueId());
        return team == null ? cfg("placeholders.no-playtime", "0h 0m") : stats(team).playtime;
    }

    public String teamMoney(Player player) {
        String team = teamId(player.getUniqueId());
        return team == null ? "0" : Amounts.format(stats(team).tokens);
    }

    public void wipe(UUID uuid) {
        String team = teamId(uuid);
        if (team != null) {
            String role = role(uuid);
            removeMember(uuid);
            if ("LEADER".equals(role)) disband(team);
        }
        teamChat.remove(uuid);
        creating.remove(uuid);
        renaming.remove(uuid);
        stopHome(uuid);
        try {
            sqlite.execute("DELETE FROM team_invites WHERE uuid = ?", uuid.toString());
        } catch (SQLException ignored) {
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            openMain(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> accept(player, args);
            case "ally" -> ally(player, args);
            case "chat" -> chat(player);
            case "demote" -> demote(player, args);
            case "disband" -> {
                openDisband(player);
                yield true;
            }
            case "invite" -> invite(player, args);
            case "leave" -> leave(player);
            case "promote" -> promote(player, args);
            case "kick" -> kick(player, args);
            case "create" -> {
                startCreate(player);
                yield true;
            }
            case "pvp", "friendlyfire", "ff" -> togglePvp(player);
            case "name", "rename" -> rename(player, args);
            case "home" -> home(player);
            case "sethome" -> setHome(player);
            case "enderchest", "ec" -> {
                openEnderchest(player);
                yield true;
            }
            case "settings" -> {
                openSettings(player);
                yield true;
            }
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    private void openMain(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            openCreate(player);
            return;
        }
        String name = teamName(player.getUniqueId());
        Stats stats = stats(team);
        Menus.Menu menu = plugin.menus().create(player,
                Text.apply(cfg("gui.main-title", "Team | %team%"), "team", name), config.getInt("gui.rows", 4));
        button(menu, config.getInt("gui.home-slot", 10), guiMaterial("home-material", Material.RED_BED),
                cfg("gui.home-name", "&#00A2FF&lTEAM HOME"),
                lore("gui.home-lore", "click", click("click-footer")),
                event -> home(player));
        button(menu, config.getInt("gui.browse-slot", 12), guiMaterial("browse-material", Material.SPYGLASS),
                cfg("gui.browse-name", "&#00D4FF&lBROWSE TEAMS"),
                lore("gui.browse-lore", "click", click("click-footer")),
                event -> openBrowse(player, 0));
        button(menu, config.getInt("gui.emergency-slot", 11), guiMaterial("emergency-material", Material.GOAT_HORN),
                cfg("gui.emergency-name", "&#FF2727&lEMERGENCY"),
                lore("gui.emergency-lore", "click", click("click-footer")),
                event -> emergency(player));
        OfflinePlayer owner = leaderOf(team);
        List<String> membersLore = lore("gui.members-lore", "click", click("click-footer"),
                "count", String.valueOf(members(team).size()));
        ItemStack membersIcon = owner == null
                ? Items.named(guiMaterial("members-material", Material.PLAYER_HEAD),
                cfg("gui.members-name", "&#9FFF00&lMEMBERS"), membersLore)
                : Items.head(owner, cfg("gui.members-name", "&#9FFF00&lMEMBERS"), membersLore);
        button(menu, config.getInt("gui.members-slot", 13), membersIcon, event -> openMembers(player, team));
        boolean chatting = team.equals(teamChat.get(player.getUniqueId()));
        button(menu, config.getInt("gui.chat-slot", 14), guiMaterial("chat-material", Material.GUSTER_BANNER_PATTERN),
                cfg("gui.chat-name", "&x&F&F&B&A&0&0&lTEAM CHAT"),
                lore("gui.chat-lore", "click", click("click-footer"),
                        "status", chatting ? "&#A9FF00&lENABLED" : "&#FF0000&lDISABLED"),
                event -> {
                    chat(player);
                    openMain(player);
                });
        button(menu, config.getInt("gui.allies-slot", 15), guiMaterial("allies-material", Material.SHIELD),
                cfg("gui.allies-name", "&#FFD700&lALLIES"),
                lore("gui.allies-lore", "click", click("click-footer"),
                        "count", String.valueOf(allies(team).size()),
                        "max", String.valueOf(config.getInt("ally.max-allies", 1))),
                event -> openAllies(player, team));
        button(menu, config.getInt("gui.leaderboard-slot", 16), guiMaterial("leaderboard-material", Material.PINK_BANNER),
                cfg("gui.leaderboard-name", "&#FFD700&lTEAM LEADERBOARD"),
                lore("gui.leaderboard-lore", "click", click("click-footer"),
                        "rank", rank(team),
                        "kills", String.valueOf(stats.kills),
                        "playtime", stats.playtime,
                        "money", Amounts.format(stats.tokens),
                        "tokens", Amounts.format(stats.tokens)),
                event -> openBrowse(player, 0));
        button(menu, config.getInt("gui.enderchest-slot", 21), guiMaterial("enderchest-material", Material.ENDER_CHEST),
                cfg("gui.enderchest-name", "&#A370EE&lTEAM ENDERCHEST"),
                lore("gui.enderchest-lore", "%click%", click("click-footer")),
                event -> openEnderchest(player));
        button(menu, config.getInt("gui.settings-slot", 23), guiMaterial("settings-material", Material.REPEATER),
                cfg("gui.settings-name", "&#FFBA00&lTEAM SETTINGS"),
                lore("gui.settings-lore", "%click%", click("click-footer")),
                event -> openSettings(player));
        int leaveSlot = config.getInt("gui.disband-slot", 22);
        if ("LEADER".equals(role(player.getUniqueId()))) {
            button(menu, leaveSlot, guiMaterial("disband-material", Material.BARRIER),
                    cfg("gui.disband-name", "&#FF2727&lDISBAND TEAM"),
                    lore("gui.disband-lore", "click", click("click-footer")),
                    event -> openDisband(player));
        } else {
            List<String> leaveLore = lore("gui.leave-lore", "click", click("click-footer"));
            menu.set(leaveSlot, Items.head(player, cfg("gui.leave-name", "&#FF2727&lLEAVE TEAM"), leaveLore), event -> {
                event.setCancelled(true);
                player.closeInventory();
                leave(player);
            });
        }
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openCreate(Player player) {
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.create-title", "&8Create Team"), 3);
        button(menu, 13, Material.NAME_TAG, cfg("gui.create-anvil-name", "&x&F&F&B&A&0&0&lCreate Team"),
                lore("gui.create-anvil-lore", "%click_create%", click("click-footer-create")),
                event -> {
                    player.closeInventory();
                    startCreate(player);
                });
        GuiButtons.border(menu);
        plugin.menus().open(player, menu);
    }

    private void startCreate(Player player) {
        if (teamId(player.getUniqueId()) != null) {
            send(player, "already-in-team");
            return;
        }
        creating.put(player.getUniqueId(), "");
        send(player, "type-name");
    }

    private void confirmCreate(Player player, String raw) {
        Menus.Menu menu = plugin.menus().create(player,
                Text.apply(cfg("gui.confirm-title", "&8Confirm &f%team%"), "team", raw), 3);
        button(menu, 11, GuiButtons.cancel(player), event -> {
                    creating.remove(player.getUniqueId());
                    openCreate(player);
                });
        button(menu, 15, GuiButtons.confirm(player), event -> {
                    creating.remove(player.getUniqueId());
                    createTeam(player, raw);
                    openMain(player);
                });
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openDisband(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null || !"LEADER".equals(role(player.getUniqueId()))) {
            send(player, "not-leader");
            return;
        }
        String name = teamName(player.getUniqueId());
        Menus.Menu menu = plugin.menus().create(player,
                Text.apply(cfg("gui.disband-confirm-title", "&8Disband &f%team%?"), "team", name), 3);
        button(menu, 11, GuiButtons.cancel(player), event -> openMain(player));
        button(menu, 15, GuiButtons.confirm(player), event -> {
                    player.closeInventory();
                    disband(team);
                    send(player, "disbanded", "team", name);
                });
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openMembers(Player player, String team) {
        Menus.Menu menu = plugin.menus().create(player,
                Text.apply(cfg("gui.members-title", "&8Members &f%team%"), "team", displayName(team)), 6);
        int slot = 10;
        for (Member member : members(team)) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.uuid);
            String role = member.role;
            List<String> lore = lore("LEADER".equals(role) ? "gui.leader-head-lore" : "gui.member-head-lore",
                    "role", role, "status", offline.isOnline() ? "&aOnline" : "&7Offline");
            String headName = "&#9FFF00&l" + (offline.getName() == null ? member.uuid.toString() : offline.getName());
            ItemStack head = offline.getPlayer() != null
                    ? Items.head(offline.getPlayer(), headName, lore)
                    : Items.named(Material.PLAYER_HEAD, headName, lore);
            menu.set(slot, head, event -> {
                event.setCancelled(true);
                if (!player.getUniqueId().equals(member.uuid) && "LEADER".equals(role(player.getUniqueId()))) {
                    if ("MEMBER".equals(member.role)) promoteMember(member.uuid);
                    else if ("OFFICER".equals(member.role)) demoteMember(member.uuid);
                    openMembers(player, team);
                } else if (!player.getUniqueId().equals(member.uuid)
                        && "OFFICER".equals(role(player.getUniqueId()))
                        && "MEMBER".equals(member.role)) {
                    removeMember(member.uuid);
                    openMembers(player, team);
                }
            });
            slot++;
        }
        button(menu, 49, GuiButtons.back(player), event -> openMain(player));
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openBrowse(Player player, int page) {
        List<String> teams = teamIds();
        int per = config.getInt("gui.browse-per-page", 21);
        int pages = Math.max(1, (teams.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.browse-title", "&8Browse Teams"), 6);
        int start = current * per;
        int[] area = CosmeticsSlots.AREA;
        for (int i = 0; i < per && start + i < teams.size() && i < area.length; i++) {
            String id = teams.get(start + i);
            String name = displayName(id);
            menu.set(area[i], Items.named(Material.NAME_TAG,
                    Text.apply(cfg("gui.browse-entry-name", "&x&F&F&B&A&0&0&l%team%"), "team", name),
                    lore("gui.browse-entry-lore", "%click%", click("click-footer"),
                            "team", name,
                            "count", String.valueOf(members(id).size()),
                            "rank", rank(id))),
                    event -> {
                        event.setCancelled(true);
                        openProfile(player, id);
                    });
        }
        if (current > 0) {
            button(menu, 48, GuiButtons.previous(player), event -> openBrowse(player, current - 1));
        }
        if (current + 1 < pages) {
            button(menu, 50, GuiButtons.next(player), event -> openBrowse(player, current + 1));
        }
        button(menu, 49, GuiButtons.back(player), event -> openMain(player));
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openProfile(Player player, String team) {
        Stats stats = stats(team);
        Menus.Menu menu = plugin.menus().create(player,
                Text.apply(cfg("gui.profile-title", "&8Team &f%team%"), "team", displayName(team)), 3);
        menu.set(13, Items.named(Material.NAME_TAG,
                Text.apply(cfg("gui.profile-item-name", "&x&F&F&B&A&0&0&l%team%"), "team", displayName(team)),
                lore("gui.profile-item-lore", "%click%", click("click-footer"),
                        "team", displayName(team),
                        "rank", rank(team),
                        "count", String.valueOf(members(team).size()),
                        "kills", String.valueOf(stats.kills),
                        "tokens", Amounts.format(stats.tokens),
                        "money", Amounts.format(stats.tokens),
                        "playtime", stats.playtime,
                        "score", String.valueOf(stats.score))));
        button(menu, 22, GuiButtons.back(player), event -> openBrowse(player, 0));
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void openAllies(Player player, String team) {
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.allies-name", "&#FFD700&lAllies"), 3);
        int slot = 11;
        for (String ally : allies(team)) {
            menu.set(slot++, Items.named(Material.GOLDEN_HELMET, "&#FFD700&l" + displayName(ally), List.of("&7Ally")));
        }
        button(menu, 22, GuiButtons.back(player), event -> openMain(player));
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private boolean accept(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "usage-accept");
            return true;
        }
        if (teamId(player.getUniqueId()) != null) {
            send(player, "already-in-team");
            return true;
        }
        String team = resolveTeam(args[1]);
        if (team == null || !hasInvite(team, player.getUniqueId())) {
            send(player, "no-invite");
            return true;
        }
        if (members(team).size() >= config.getInt("creation.max-members", 18)) {
            send(player, "team-full");
            return true;
        }
        addMember(player.getUniqueId(), team, "MEMBER");
        deleteInvite(team, player.getUniqueId());
        send(player, "accepted", "team", displayName(team));
        return true;
    }

    private boolean ally(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "usage-ally");
            return true;
        }
        String team = teamId(player.getUniqueId());
        if (team == null || !"LEADER".equals(role(player.getUniqueId()))) {
            send(player, "not-leader");
            return true;
        }
        String other = resolveTeam(args[1]);
        if (other == null || other.equals(team)) {
            send(player, "unknown-team");
            return true;
        }
        if (allies(team).contains(other)) {
            send(player, "already-allied");
            return true;
        }
        if (allies(team).size() >= config.getInt("ally.max-allies", 1)) {
            send(player, "ally-limit");
            return true;
        }
        if (pendingAlly(other, team)) {
            setAlly(team, other, "accepted");
            send(player, "ally-accepted", "team", displayName(other));
            return true;
        }
        setAlly(team, other, "pending");
        send(player, "ally-sent", "team", displayName(other));
        return true;
    }

    private boolean chat(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return true;
        }
        if (team.equals(teamChat.get(player.getUniqueId()))) {
            teamChat.remove(player.getUniqueId());
            send(player, "chat-off");
        } else {
            teamChat.put(player.getUniqueId(), team);
            send(player, "chat-on");
        }
        return true;
    }

    private boolean demote(Player player, String[] args) {
        return rankChange(player, args, false);
    }

    private boolean promote(Player player, String[] args) {
        return rankChange(player, args, true);
    }

    private boolean rankChange(Player player, String[] args, boolean up) {
        if (args.length < 2) {
            send(player, up ? "usage-promote" : "usage-demote");
            return true;
        }
        if (!"LEADER".equals(role(player.getUniqueId()))) {
            send(player, "not-leader");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(player, "player-missing");
            return true;
        }
        String team = teamId(player.getUniqueId());
        if (team == null || !team.equals(teamId(target.getUniqueId()))) {
            send(player, "not-member");
            return true;
        }
        if (up) promoteMember(target.getUniqueId());
        else demoteMember(target.getUniqueId());
        send(player, up ? "promoted" : "demoted", "player", target.getName());
        return true;
    }

    private boolean invite(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "usage-invite");
            return true;
        }
        String team = teamId(player.getUniqueId());
        String role = role(player.getUniqueId());
        if (team == null || (!"LEADER".equals(role) && !"OFFICER".equals(role))) {
            send(player, "not-officer");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(player, "player-missing");
            return true;
        }
        if (teamId(target.getUniqueId()) != null) {
            send(player, "target-in-team");
            return true;
        }
        addInvite(team, target.getUniqueId());
        send(player, "invited", "player", target.getName());
        send(target, "invite-received", "team", displayName(team));
        return true;
    }

    private boolean leave(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return true;
        }
        if ("LEADER".equals(role(player.getUniqueId()))) {
            send(player, "leader-leave");
            return true;
        }
        removeMember(player.getUniqueId());
        teamChat.remove(player.getUniqueId());
        send(player, "left");
        return true;
    }

    private boolean kick(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "usage-kick");
            return true;
        }
        String role = role(player.getUniqueId());
        if (!"LEADER".equals(role) && !"OFFICER".equals(role)) {
            send(player, "not-officer");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(player, "player-missing");
            return true;
        }
        String team = teamId(player.getUniqueId());
        if (team == null || !team.equals(teamId(target.getUniqueId())) || "LEADER".equals(role(target.getUniqueId()))) {
            send(player, "not-member");
            return true;
        }
        removeMember(target.getUniqueId());
        send(player, "kicked", "player", target.getName());
        send(target, "kicked-target");
        return true;
    }

    private void emergency(Player player) {
        long wait = config.getLong("emergency.cooldown-seconds", 60) * 1000L;
        Long last = emergency.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) {
            send(player, "emergency-cooldown");
            return;
        }
        String team = teamId(player.getUniqueId());
        if (team == null) return;
        emergency.put(player.getUniqueId(), System.currentTimeMillis());
        String loc = player.getWorld().getName() + " " + player.getLocation().getBlockX()
                + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ();
        for (Member member : members(team)) {
            Player online = Bukkit.getPlayer(member.uuid);
            if (online != null) send(online, "emergency", "player", player.getName(), "location", loc);
        }
    }

    private void createTeam(Player player, String raw) {
        String name = raw.trim();
        int min = config.getInt("creation.min-name-length", 3);
        int max = config.getInt("creation.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            send(player, "invalid-name");
            return;
        }
        for (String banned : config.getStringList("creation.banned-keywords")) {
            if (!banned.isBlank() && name.toLowerCase(Locale.ROOT).contains(banned.toLowerCase(Locale.ROOT))) {
                send(player, "banned-name");
                return;
            }
        }
        for (String banned : config.getStringList("creation.blacklisted-names")) {
            if (name.equalsIgnoreCase(banned.trim())) {
                send(player, "banned-name");
                return;
            }
        }
        String id = sanitize(name);
        if (id.isEmpty() || exists(id) || nameTaken(name)) {
            send(player, "name-taken");
            return;
        }
        try {
            sqlite.execute("INSERT INTO teams (id, name, created_at) VALUES (?, ?, ?)", id, name, System.currentTimeMillis());
            sqlite.execute("INSERT INTO team_members (uuid, team_id, role) VALUES (?, ?, 'LEADER')",
                    player.getUniqueId().toString(), id);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to create team", ex);
            send(player, "failed");
            return;
        }
        send(player, "created", "team", name);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTyped(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (renaming.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String name = PlainTextComponentSerializer.plainText().serialize(event.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                renaming.remove(player.getUniqueId());
                rename(player, new String[]{"name", name});
            });
            return;
        }
        if (!creating.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        String name = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> confirmCreate(player, name));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String team = teamChat.get(player.getUniqueId());
        if (team == null || !team.equals(teamId(player.getUniqueId()))) return;
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        String format = cfg("chat.team-format", "&x&F&F&B&A&0&0[TEAM] &f%player% &8» &7%message%")
                .replace("%player%", player.getName())
                .replace("%message%", raw);
        for (Member member : members(team)) {
            Player online = Bukkit.getPlayer(member.uuid);
            if (online != null) online.sendMessage(ColorUtil.parse(format));
        }
        for (String ally : allies(team)) {
            String allyFormat = cfg("chat.ally-format", "&#ffb300[ALLY] &f%player% &8» &7%message%")
                    .replace("%player%", player.getName())
                    .replace("%message%", raw);
            for (Member member : members(ally)) {
                Player online = Bukkit.getPlayer(member.uuid);
                if (online != null) online.sendMessage(ColorUtil.parse(allyFormat));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        creating.remove(event.getPlayer().getUniqueId());
        renaming.remove(event.getPlayer().getUniqueId());
        stopHome(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onHomeMove(PlayerMoveEvent event) {
        if (!config.getBoolean("home.cancel-on-move", true)) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!pendingHome.containsKey(event.getPlayer().getUniqueId())) return;
        stopHome(event.getPlayer().getUniqueId());
        send(event.getPlayer(), "home-cancel");
    }

    private boolean togglePvp(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return true;
        }
        if (!"LEADER".equals(role(player.getUniqueId())) && !"OFFICER".equals(role(player.getUniqueId()))) {
            send(player, "not-officer");
            return true;
        }
        boolean next = !pvp(team);
        try {
            sqlite.execute("UPDATE teams SET pvp = ? WHERE id = ?", next ? 1 : 0, team);
        } catch (SQLException ignored) {
        }
        send(player, next ? "pvp-on" : "pvp-off");
        return true;
    }

    private boolean rename(Player player, String[] args) {
        if (!"LEADER".equals(role(player.getUniqueId()))) {
            send(player, "not-leader");
            return true;
        }
        if (args.length < 2) {
            send(player, "usage-name");
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        int min = config.getInt("creation.min-name-length", 3);
        int max = config.getInt("creation.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            send(player, "invalid-name");
            return true;
        }
        if (nameTaken(name) && !name.equalsIgnoreCase(teamName(player.getUniqueId()))) {
            send(player, "name-taken");
            return true;
        }
        String team = teamId(player.getUniqueId());
        try {
            sqlite.execute("UPDATE teams SET name = ? WHERE id = ?", name, team);
        } catch (SQLException ex) {
            send(player, "failed");
            return true;
        }
        send(player, "renamed", "team", name);
        return true;
    }

    private boolean home(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return true;
        }
        Location location = homeOf(team);
        if (location == null) {
            send(player, "no-home");
            return true;
        }
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && combat.tagged(player)) {
            send(player, "in-combat");
            return true;
        }
        player.closeInventory();
        if (pendingHome.containsKey(player.getUniqueId())) {
            send(player, "already-teleporting");
            return true;
        }
        int seconds = Math.max(0, config.getInt("home.delay-seconds", 5));
        if (seconds == 0) {
            finishHome(player, location);
            return true;
        }
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopHome(player.getUniqueId());
                return;
            }
            CombatModule tagged = plugin.modules().get(CombatModule.class);
            if (tagged != null && tagged.tagged(player)) {
                stopHome(player.getUniqueId());
                send(player, "in-combat");
                return;
            }
            if (left[0] <= 0) {
                stopHome(player.getUniqueId());
                finishHome(player, location);
                return;
            }
            send(player, "home-countdown", "seconds", String.valueOf(left[0]));
            left[0]--;
        }, 0L, 20L);
        pendingHome.put(player.getUniqueId(), task);
        return true;
    }

    private void finishHome(Player player, Location location) {
        player.teleportAsync(location).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Boolean.TRUE.equals(ok) || !player.isOnline()) return;
            send(player, "home-teleport");
        }));
    }

    private void stopHome(UUID uuid) {
        BukkitTask task = pendingHome.remove(uuid);
        if (task != null) task.cancel();
    }

    private boolean setHome(Player player) {
        if (!"LEADER".equals(role(player.getUniqueId())) && !"OFFICER".equals(role(player.getUniqueId()))) {
            send(player, "not-officer");
            return true;
        }
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return true;
        }
        Location loc = player.getLocation();
        String raw = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ()
                + ";" + loc.getYaw() + ";" + loc.getPitch();
        try {
            sqlite.execute("UPDATE teams SET home = ? WHERE id = ?", raw, team);
        } catch (SQLException ignored) {
        }
        send(player, "home-set");
        return true;
    }

    private void openEnderchest(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return;
        }
        Menus.Menu menu = plugin.menus().create(player,
                cfg("gui.enderchest-title", "Enderchest"), 3).unlocked();
        try {
            sqlite.query("SELECT slot, item FROM team_enderchest WHERE team_id = ?", rs -> {
                try {
                    while (rs.next()) {
                        ItemStack item = Items.deserialize(rs.getString("item"));
                        if (item != null) menu.inventory().setItem(rs.getInt("slot"), item);
                    }
                } catch (SQLException ignored) {
                }
                return null;
            }, team);
        } catch (SQLException ignored) {
        }
        menu.onClose(closed -> saveEnderchest(team, closed.getOpenInventory().getTopInventory()));
        plugin.menus().open(player, menu);
    }

    private void saveEnderchest(String team, Inventory inventory) {
        try {
            sqlite.execute("DELETE FROM team_enderchest WHERE team_id = ?", team);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                sqlite.execute("INSERT INTO team_enderchest (team_id, slot, item) VALUES (?, ?, ?)",
                        team, slot, Items.serialize(item));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save team enderchest", ex);
        }
    }

    private void openSettings(Player player) {
        String team = teamId(player.getUniqueId());
        if (team == null) {
            send(player, "not-in-team");
            return;
        }
        boolean friendly = pvp(team);
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.settings-title", "☀ Team ☀ Previewing | Settings"),
                config.getInt("gui.settings-rows", 3));
        button(menu, config.getInt("gui.tag-slot", 11), guiMaterial("tag-material", Material.NAME_TAG),
                cfg("gui.tag-name", "&#FFBA00&lTEAM NAME"),
                lore("gui.tag-lore", "%click%", click("click-footer"), "tag", displayName(team)),
                event -> {
                    if (!"LEADER".equals(role(player.getUniqueId()))) {
                        send(player, "not-leader");
                        return;
                    }
                    player.closeInventory();
                    renaming.put(player.getUniqueId(), team);
                    send(player, "type-rename");
                });
        button(menu, config.getInt("gui.home-set-slot", 13), guiMaterial("home-set-material", Material.RED_BANNER),
                cfg("gui.home-set-name", "&#00A2FF&lSET HOME"),
                lore("gui.home-set-lore", "%click%", click("click-footer")),
                event -> {
                    player.closeInventory();
                    setHome(player);
                });
        Material pvpItem = friendly
                ? guiMaterial("pvp-on-material", Material.LIME_WOOL)
                : guiMaterial("pvp-off-material", Material.RED_WOOL);
        String pvpName = friendly
                ? cfg("gui.pvp-name-on", "&#94FF00&lFRIENDLY FIRE")
                : cfg("gui.pvp-name-off", "&#FF0000&lFRIENDLY FIRE");
        button(menu, config.getInt("gui.pvp-slot", 15), pvpItem, pvpName,
                lore("gui.pvp-lore", "%click%", click("click-footer"),
                        "status", friendly ? "&#94FF00&lON" : "&#FF0000&lOFF"),
                event -> {
                    togglePvp(player);
                    openSettings(player);
                });
        menu.set(config.getInt("gui.settings-back-slot", 22), GuiButtons.back(player), event -> {
            event.setCancelled(true);
            openMain(player);
        });
        menu.fill(Items.named(
                Sounds.material(cfg("gui.settings-filler", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                " ", List.of()));
        plugin.menus().open(player, menu);
    }

    private boolean pvp(String team) {
        try {
            return Boolean.TRUE.equals(sqlite.query("SELECT pvp FROM teams WHERE id = ?", rs -> {
                try {
                    return rs.next() && rs.getInt("pvp") == 1;
                } catch (SQLException ex) {
                    return false;
                }
            }, team));
        } catch (SQLException ex) {
            return false;
        }
    }

    private Location homeOf(String team) {
        try {
            return sqlite.query("SELECT home FROM teams WHERE id = ?", rs -> {
                try {
                    if (!rs.next()) return null;
                    String raw = rs.getString("home");
                    if (raw == null || raw.isBlank()) return null;
                    String[] parts = raw.split(";");
                    if (parts.length < 4) return null;
                    World world = Bukkit.getWorld(parts[0]);
                    if (world == null) return null;
                    Location loc = new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    if (parts.length >= 6) {
                        loc.setYaw(Float.parseFloat(parts[4]));
                        loc.setPitch(Float.parseFloat(parts[5]));
                    }
                    return loc;
                } catch (Exception ex) {
                    return null;
                }
            }, team);
        } catch (SQLException ex) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player damager = damager(event.getDamager());
        if (damager == null) return;
        String team = teamId(victim.getUniqueId());
        if (team == null || !team.equals(teamId(damager.getUniqueId()))) return;
        if (!pvp(team)) event.setCancelled(true);
    }

    private static Player damager(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void button(Menus.Menu menu, int slot, ItemStack item,
                        java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        menu.set(slot, item, event -> {
            event.setCancelled(true);
            click.accept(event);
        });
    }

    private void button(Menus.Menu menu, int slot, Material material, String name, List<String> lore,
                        java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        button(menu, slot, Items.named(material, name, lore), click);
    }

    private List<String> lore(String path, String... pairs) {
        List<String> lines = config.getStringList(path);
        if (lines.isEmpty()) return List.of();
        String[] extras = {
                "click", click("click-footer"),
                "click_confirm", click("click-footer-confirm"),
                "click_create", click("click-footer-create"),
                "click_cancel", click("click-footer-cancel")
        };
        String[] merged = new String[extras.length + pairs.length];
        System.arraycopy(extras, 0, merged, 0, extras.length);
        System.arraycopy(pairs, 0, merged, extras.length, pairs.length);
        return Text.applyList(new ArrayList<>(lines), merged);
    }

    private String click(String key) {
        return cfg("gui." + key, "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To View");
    }

    private ItemStack glass() {
        return Items.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
    }

    private String teamId(UUID uuid) {
        try {
            return sqlite.query("SELECT team_id FROM team_members WHERE uuid = ?", rs -> {
                try {
                    return rs.next() ? rs.getString("team_id") : null;
                } catch (SQLException ex) {
                    return null;
                }
            }, uuid.toString());
        } catch (SQLException ex) {
            return null;
        }
    }

    private String teamName(UUID uuid) {
        String id = teamId(uuid);
        return id == null ? null : displayName(id);
    }

    private String displayName(String id) {
        try {
            return sqlite.query("SELECT name FROM teams WHERE id = ?", rs -> {
                try {
                    return rs.next() ? rs.getString("name") : id;
                } catch (SQLException ex) {
                    return id;
                }
            }, id);
        } catch (SQLException ex) {
            return id;
        }
    }

    private String role(UUID uuid) {
        try {
            return sqlite.query("SELECT role FROM team_members WHERE uuid = ?", rs -> {
                try {
                    return rs.next() ? rs.getString("role") : "";
                } catch (SQLException ex) {
                    return "";
                }
            }, uuid.toString());
        } catch (SQLException ex) {
            return "";
        }
    }

    private List<Member> members(String team) {
        List<Member> list = new ArrayList<>();
        try {
            sqlite.query("SELECT uuid, role FROM team_members WHERE team_id = ?", rs -> {
                try {
                    while (rs.next()) {
                        list.add(new Member(UUID.fromString(rs.getString("uuid")), rs.getString("role")));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, team);
        } catch (SQLException ignored) {
        }
        return list;
    }

    private List<String> teamIds() {
        List<String> list = new ArrayList<>();
        try {
            sqlite.query("SELECT id FROM teams", rs -> {
                try {
                    while (rs.next()) list.add(rs.getString("id"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            });
        } catch (SQLException ignored) {
        }
        return list;
    }

    private boolean exists(String id) {
        try {
            return Boolean.TRUE.equals(sqlite.query("SELECT 1 FROM teams WHERE id = ?", rs -> {
                try {
                    return rs.next();
                } catch (SQLException ex) {
                    return false;
                }
            }, id));
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean nameTaken(String name) {
        try {
            return Boolean.TRUE.equals(sqlite.query("SELECT 1 FROM teams WHERE lower(name) = ?", rs -> {
                try {
                    return rs.next();
                } catch (SQLException ex) {
                    return false;
                }
            }, name.toLowerCase(Locale.ROOT)));
        } catch (SQLException ex) {
            return false;
        }
    }

    private String resolveTeam(String raw) {
        String id = sanitize(raw);
        if (exists(id)) return id;
        for (String team : teamIds()) {
            if (team.equalsIgnoreCase(raw) || displayName(team).equalsIgnoreCase(raw)) return team;
        }
        return null;
    }

    private void addMember(UUID uuid, String team, String role) {
        try {
            sqlite.execute("INSERT OR REPLACE INTO team_members (uuid, team_id, role) VALUES (?, ?, ?)",
                    uuid.toString(), team, role);
        } catch (SQLException ignored) {
        }
    }

    private void removeMember(UUID uuid) {
        try {
            sqlite.execute("DELETE FROM team_members WHERE uuid = ?", uuid.toString());
        } catch (SQLException ignored) {
        }
    }

    private void promoteMember(UUID uuid) {
        addMember(uuid, teamId(uuid), "OFFICER");
    }

    private void demoteMember(UUID uuid) {
        addMember(uuid, teamId(uuid), "MEMBER");
    }

    private void disband(String team) {
        try {
            sqlite.execute("DELETE FROM team_members WHERE team_id = ?", team);
            sqlite.execute("DELETE FROM team_invites WHERE team_id = ?", team);
            sqlite.execute("DELETE FROM team_allies WHERE a = ? OR b = ?", team, team);
            sqlite.execute("DELETE FROM teams WHERE id = ?", team);
        } catch (SQLException ignored) {
        }
        teamChat.values().removeIf(team::equals);
    }

    private void addInvite(String team, UUID uuid) {
        long expires = System.currentTimeMillis() + config.getLong("invites.lifetime-seconds", 120) * 1000L;
        try {
            sqlite.execute("INSERT OR REPLACE INTO team_invites (team_id, uuid, expires) VALUES (?, ?, ?)",
                    team, uuid.toString(), expires);
        } catch (SQLException ignored) {
        }
    }

    private boolean hasInvite(String team, UUID uuid) {
        try {
            return Boolean.TRUE.equals(sqlite.query(
                    "SELECT 1 FROM team_invites WHERE team_id = ? AND uuid = ? AND expires > ?", rs -> {
                        try {
                            return rs.next();
                        } catch (SQLException ex) {
                            return false;
                        }
                    }, team, uuid.toString(), System.currentTimeMillis()));
        } catch (SQLException ex) {
            return false;
        }
    }

    private void deleteInvite(String team, UUID uuid) {
        try {
            sqlite.execute("DELETE FROM team_invites WHERE team_id = ? AND uuid = ?", team, uuid.toString());
        } catch (SQLException ignored) {
        }
    }

    private List<String> invites(UUID uuid) {
        List<String> list = new ArrayList<>();
        try {
            sqlite.query("SELECT team_id FROM team_invites WHERE uuid = ? AND expires > ?", rs -> {
                try {
                    while (rs.next()) list.add(rs.getString("team_id"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, uuid.toString(), System.currentTimeMillis());
        } catch (SQLException ignored) {
        }
        return list;
    }

    private List<String> allies(String team) {
        List<String> list = new ArrayList<>();
        try {
            sqlite.query("SELECT a, b FROM team_allies WHERE (a = ? OR b = ?) AND status = 'accepted'", rs -> {
                try {
                    while (rs.next()) {
                        String a = rs.getString("a");
                        String b = rs.getString("b");
                        list.add(a.equals(team) ? b : a);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            }, team, team);
        } catch (SQLException ignored) {
        }
        return list;
    }

    private boolean pendingAlly(String from, String to) {
        String a = pair(from, to)[0];
        String b = pair(from, to)[1];
        try {
            return Boolean.TRUE.equals(sqlite.query(
                    "SELECT 1 FROM team_allies WHERE a = ? AND b = ? AND status = 'pending'", rs -> {
                        try {
                            return rs.next();
                        } catch (SQLException ex) {
                            return false;
                        }
                    }, a, b));
        } catch (SQLException ex) {
            return false;
        }
    }

    private void setAlly(String one, String two, String status) {
        String a = pair(one, two)[0];
        String b = pair(one, two)[1];
        long expires = System.currentTimeMillis() + config.getLong("ally.request-lifetime-seconds", 120) * 1000L;
        try {
            sqlite.execute("INSERT OR REPLACE INTO team_allies (a, b, status, expires) VALUES (?, ?, ?, ?)",
                    a, b, status, expires);
        } catch (SQLException ignored) {
        }
    }

    private static String[] pair(String one, String two) {
        return one.compareTo(two) <= 0 ? new String[]{one, two} : new String[]{two, one};
    }

    private String rank(String team) {
        List<String> ranked = new ArrayList<>(teamIds());
        ranked.sort((a, b) -> Integer.compare(stats(b).score, stats(a).score));
        int index = ranked.indexOf(team);
        return index < 0 ? cfg("gui.unranked", "Unranked") : String.valueOf(index + 1);
    }

    private Stats stats(String team) {
        int kills = 0;
        long play = 0;
        double tokens = 0;
        var economy = plugin.modules().get(com.shardedcore.modules.economy.EconomyModule.class);
        for (Member member : members(team)) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(member.uuid);
            try {
                kills += player.getStatistic(Statistic.PLAYER_KILLS);
                play += player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            } catch (IllegalArgumentException ignored) {
            }
            if (economy != null) tokens += economy.service().get(member.uuid);
        }
        int score = kills * config.getInt("leaderboard.kill-weight", 100)
                + (int) (tokens * config.getInt("leaderboard.token-weight", 1))
                + (int) ((play / 20L / 3600L) * config.getInt("leaderboard.playtime-hour-weight", 50));
        return new Stats(kills, tokens, playtime(play / 20L), score);
    }

    private OfflinePlayer leaderOf(String team) {
        for (Member member : members(team)) {
            if ("LEADER".equals(member.role)) return Bukkit.getOfflinePlayer(member.uuid);
        }
        return null;
    }

    private Material guiMaterial(String key, Material fallback) {
        return Sounds.material(cfg("gui." + key, fallback.name()), fallback);
    }

    private static String playtime(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours + "h " + minutes + "m";
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tabs.filter(List.of("accept", "ally", "chat", "demote", "disband", "invite", "leave",
                    "promote", "kick", "create", "pvp", "name", "home", "sethome", "enderchest", "settings"), args[0]);
        }
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            List<String> names = new ArrayList<>();
            for (String id : invites(player.getUniqueId())) names.add(displayName(id));
            return Tabs.filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ally")) {
            List<String> names = new ArrayList<>();
            String self = teamId(player.getUniqueId());
            for (String id : teamIds()) {
                if (!id.equals(self)) names.add(displayName(id));
            }
            return Tabs.filter(names, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("demote") || args[0].equalsIgnoreCase("promote")
                || args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("invite"))) {
            if (args[0].equalsIgnoreCase("invite")) return Tabs.players(args[1]);
            String team = teamId(player.getUniqueId());
            if (team == null) return List.of();
            List<String> names = new ArrayList<>();
            for (Member member : members(team)) {
                Player online = Bukkit.getPlayer(member.uuid);
                if (online != null) names.add(online.getName());
            }
            return Tabs.filter(names, args[1]);
        }
        return List.of();
    }

    private record Member(UUID uuid, String role) {
    }

    private record Stats(int kills, double tokens, String playtime, int score) {
    }

    private static final class CosmeticsSlots {
        private static final int[] AREA = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
    }
}
