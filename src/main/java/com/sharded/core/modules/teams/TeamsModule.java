package com.sharded.core.modules.teams;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class TeamsModule extends Module implements CommandExecutor, TabCompleter {

    private TeamDatabase database;
    private TeamGuiHandler guiHandler;
    private final Set<UUID> teamChatMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingTeamName = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> emergencyCooldown = new ConcurrentHashMap<>();

    public TeamsModule(ShardedCore plugin) {
        super(plugin, "teams");
    }

    ShardedCore plugin() {
        return plugin;
    }

    org.bukkit.configuration.file.YamlConfiguration teamConfig() {
        return config;
    }

    boolean isTeamChat(UUID uuid) {
        return teamChatMode.contains(uuid);
    }

    String formatPlaytime(long ms) {
        long hours = ms / 3_600_000L;
        long minutes = (ms % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }

    String roleName(int role) {
        return switch (role) {
            case TeamDatabase.ROLE_LEADER -> raw("role-leader");
            case TeamDatabase.ROLE_OFFICER -> raw("role-officer");
            default -> raw("role-member");
        };
    }

    public TeamDatabase database() {
        return database;
    }

    public String notInTeamPlaceholder() {
        return config.getString("placeholders.not-in-team", "N/A");
    }

    @Override
    protected void onEnable() {
        try {
            database = new TeamDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open teams database", e);
        }

        guiHandler = new TeamGuiHandler(this);
        registerCommand("team", this);
        registerCommand("teams", this);
    }

    @Override
    protected void onDisable() {
        awaitingTeamName.clear();
        flushPlaytimeAll();
        teamChatMode.clear();
        onlineSince.clear();
        if (database != null) database.close();
        database = null;
        guiHandler = null;
    }

    void beginCreateNameInput(Player player) {
        if (database.getTeamId(player.getUniqueId()) != null) {
            send(player, "already-in-team");
            return;
        }
        awaitingTeamName.add(player.getUniqueId());
        player.closeInventory();
        send(player, "create-name-prompt");
    }

    void confirmCreate(Player player, String name) {
        if (!validateTeamName(player, name)) return;
        TeamDatabase.Team team = database.createTeam(name, player.getUniqueId());
        if (team == null) {
            send(player, "create-failed");
            return;
        }
        send(player, "created", "%team%", team.name());
    }

    boolean validateTeamName(Player player, String name) {
        if (database.getTeamId(player.getUniqueId()) != null) {
            send(player, "already-in-team");
            return false;
        }
        name = name.trim();
        int min = config.getInt("creation.min-name-length", 3);
        int max = config.getInt("creation.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            send(player, "create-length", "%min%", String.valueOf(min), "%max%", String.valueOf(max));
            return false;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            send(player, "create-invalid");
            return false;
        }
        for (String banned : config.getStringList("creation.banned-keywords")) {
            if (name.toLowerCase(Locale.ROOT).contains(banned.toLowerCase(Locale.ROOT))) {
                send(player, "create-banned");
                return false;
            }
        }
        if (database.getTeamByName(name) != null) {
            send(player, "create-exists");
            return false;
        }
        return true;
    }

    void handleMemberHeadClick(Player player, UUID targetId) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        TeamDatabase.Member target = database.getMember(teamId, targetId);
        if (self == null || target == null) return;

        boolean leader = isLeader(player, teamId);
        if (leader) {
            if (target.role() == TeamDatabase.ROLE_MEMBER) {
                database.setRole(teamId, targetId, TeamDatabase.ROLE_OFFICER);
                send(player, "promoted", "%player%", OfflinePlayers.name(targetId));
            } else if (target.role() == TeamDatabase.ROLE_OFFICER) {
                database.setRole(teamId, targetId, TeamDatabase.ROLE_MEMBER);
                send(player, "demoted", "%player%", OfflinePlayers.name(targetId));
            }
            return;
        }
        if (self.role() <= TeamDatabase.ROLE_OFFICER && target.role() > TeamDatabase.ROLE_OFFICER) {
            database.removeMember(teamId, targetId);
            send(player, "kicked", "%player%", OfflinePlayers.name(targetId));
            Player online = Bukkit.getPlayer(targetId);
            if (online != null) send(online, "kicked-you", "%team%", database.getTeamById(teamId).name());
        } else {
            send(player, "no-permission-rank");
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamGuiHandler.TeamGuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot(), event.getCurrentItem());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.teams.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            openGui(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "gui", "menu" -> {
                openGui(player);
                yield true;
            }
            case "create" -> {
                if (args.length < 2) {
                    send(player, "create-usage");
                    yield true;
                }
                handleCreate(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                yield true;
            }
            case "invite" -> {
                if (args.length < 2) {
                    send(player, "invite-usage");
                    yield true;
                }
                handleInvite(player, args[1]);
                yield true;
            }
            case "accept" -> {
                handleAccept(player);
                yield true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    send(player, "kick-usage");
                    yield true;
                }
                handleKick(player, args[1]);
                yield true;
            }
            case "leave" -> {
                handleLeave(player);
                yield true;
            }
            case "disband" -> {
                handleDisband(player);
                yield true;
            }
            case "promote" -> {
                if (args.length < 2) {
                    send(player, "promote-usage");
                    yield true;
                }
                handlePromote(player, args[1]);
                yield true;
            }
            case "demote" -> {
                if (args.length < 2) {
                    send(player, "demote-usage");
                    yield true;
                }
                handleDemote(player, args[1]);
                yield true;
            }
            case "members" -> {
                handleMembers(player);
                yield true;
            }
            case "stats" -> {
                handleStats(player);
                yield true;
            }
            case "leaderboard", "top" -> {
                handleLeaderboard(player);
                yield true;
            }
            case "emergency", "help", "sos" -> {
                handleEmergency(player);
                yield true;
            }
            case "ally" -> {
                if (args.length < 2) {
                    send(player, "ally-usage");
                    yield true;
                }
                if (args[1].equalsIgnoreCase("accept")) {
                    handleAllyAccept(player);
                } else {
                    handleAllyRequest(player, args[1]);
                }
                yield true;
            }
            case "chat" -> {
                toggleTeamChat(player);
                yield true;
            }
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    private void openGui(Player player) {
        guiHandler.openFor(player);
    }

    private void handleCreate(Player player, String name) {
        if (!validateTeamName(player, name)) return;
        confirmCreate(player, name.trim());
    }

    private void handleInvite(Player player, String targetName) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        if (self == null || self.role() > TeamDatabase.ROLE_OFFICER) {
            send(player, "no-permission-rank");
            return;
        }
        if (database.getMembers(teamId).size() >= config.getInt("creation.max-members", 18)) {
            send(player, "team-full");
            return;
        }
        OfflinePlayer target = OfflinePlayers.resolve(targetName);
        if (database.getTeamId(target.getUniqueId()) != null) {
            send(player, "target-in-team", "%player%", name(target));
            return;
        }
        long expires = System.currentTimeMillis() + config.getLong("invites.lifetime-seconds", 120L) * 1000L;
        database.addInvite(teamId, target.getUniqueId(), player.getUniqueId(), expires);
        TeamDatabase.Team team = database.getTeamById(teamId);
        send(player, "invited", "%player%", name(target));
        if (target.isOnline() && target.getPlayer() != null) {
            send(target.getPlayer(), "invite-received", "%team%", team.name(), "%player%", player.getName());
        }
    }

    private void handleAccept(Player player) {
        TeamDatabase.Invite invite = database.getLatestInvite(player.getUniqueId());
        if (invite != null && invite.expiresAt() >= System.currentTimeMillis()) {
            if (database.getTeamId(player.getUniqueId()) != null) {
                send(player, "already-in-team");
                return;
            }
            TeamDatabase.Team team = database.getTeamById(invite.teamId());
            if (team == null) {
                send(player, "invite-expired");
                return;
            }
            if (database.getMembers(invite.teamId()).size() >= config.getInt("creation.max-members", 18)) {
                send(player, "team-full");
                return;
            }
            database.addMember(invite.teamId(), player.getUniqueId(), TeamDatabase.ROLE_MEMBER);
            database.removeInvite(invite.teamId(), player.getUniqueId());
            broadcastTeam(invite.teamId(), raw("joined-broadcast", "%player%", player.getName()));
            send(player, "joined", "%team%", team.name());
            return;
        }
        Integer teamId = database.getTeamId(player.getUniqueId());
        if (teamId == null) {
            send(player, "no-invite");
            return;
        }
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        if (self == null || self.role() > TeamDatabase.ROLE_OFFICER) {
            send(player, "no-permission-rank");
            return;
        }
        handleAllyAccept(player);
    }

    private void handleKick(Player player, String targetName) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        if (self == null || self.role() > TeamDatabase.ROLE_OFFICER) {
            send(player, "no-permission-rank");
            return;
        }
        OfflinePlayer target = OfflinePlayers.resolve(targetName);
        TeamDatabase.Member targetMember = database.getMember(teamId, target.getUniqueId());
        if (targetMember == null) {
            send(player, "not-in-your-team", "%player%", name(target));
            return;
        }
        if (targetMember.role() <= self.role() && !player.getUniqueId().equals(database.getTeamById(teamId).leaderUuid())) {
            send(player, "cannot-kick-rank");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(player, "cannot-kick-self");
            return;
        }
        database.removeMember(teamId, target.getUniqueId());
        send(player, "kicked", "%player%", name(target));
        if (target.isOnline() && target.getPlayer() != null) {
            send(target.getPlayer(), "kicked-you", "%team%", database.getTeamById(teamId).name());
        }
    }

    void handleLeave(Player player) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Team team = database.getTeamById(teamId);
        if (team != null && team.leaderUuid().equals(player.getUniqueId())) {
            send(player, "leader-leave-disband");
            return;
        }
        database.removeMember(teamId, player.getUniqueId());
        teamChatMode.remove(player.getUniqueId());
        send(player, "left", "%team%", team.name());
        broadcastTeam(teamId, raw("left-broadcast", "%player%", player.getName()));
    }

    void handleDisband(Player player) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Team team = database.getTeamById(teamId);
        if (team == null || !team.leaderUuid().equals(player.getUniqueId())) {
            send(player, "not-leader");
            return;
        }
        broadcastTeam(teamId, raw("disband-broadcast", "%team%", team.name()));
        for (TeamDatabase.Member member : database.getMembers(teamId)) {
            teamChatMode.remove(member.uuid());
        }
        database.deleteTeam(teamId);
        send(player, "disbanded", "%team%", team.name());
    }

    private void handlePromote(Player player, String targetName) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        if (!isLeader(player, teamId)) {
            send(player, "not-leader");
            return;
        }
        OfflinePlayer target = OfflinePlayers.resolve(targetName);
        TeamDatabase.Member member = database.getMember(teamId, target.getUniqueId());
        if (member == null) {
            send(player, "not-in-your-team", "%player%", name(target));
            return;
        }
        if (member.role() == TeamDatabase.ROLE_LEADER) {
            send(player, "already-leader");
            return;
        }
        if (member.role() == TeamDatabase.ROLE_OFFICER) {
            send(player, "already-officer");
            return;
        }
        database.setRole(teamId, target.getUniqueId(), TeamDatabase.ROLE_OFFICER);
        send(player, "promoted", "%player%", name(target));
    }

    private void handleDemote(Player player, String targetName) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        if (!isLeader(player, teamId)) {
            send(player, "not-leader");
            return;
        }
        OfflinePlayer target = OfflinePlayers.resolve(targetName);
        TeamDatabase.Member member = database.getMember(teamId, target.getUniqueId());
        if (member == null) {
            send(player, "not-in-your-team", "%player%", name(target));
            return;
        }
        if (member.role() == TeamDatabase.ROLE_LEADER) {
            send(player, "cannot-demote-leader");
            return;
        }
        if (member.role() == TeamDatabase.ROLE_MEMBER) {
            send(player, "already-member");
            return;
        }
        database.setRole(teamId, target.getUniqueId(), TeamDatabase.ROLE_MEMBER);
        send(player, "demoted", "%player%", name(target));
    }

    private void handleMembers(Player player) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Team team = database.getTeamById(teamId);
        List<TeamDatabase.Member> members = database.getMembers(teamId);
        send(player, "members-header", "%team%", team.name());
        for (TeamDatabase.Member member : members) {
            String status = Bukkit.getPlayer(member.uuid()) != null ? raw("online") : raw("offline");
            send(player, "members-line", "%player%", OfflinePlayers.name(member.uuid()),
                    "%role%", roleName(member.role()), "%status%", status);
        }
    }

    void handleStats(Player player) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Team team = database.getTeamById(teamId);
        long tokens = 0;
        int kills = 0;
        long playtime = 0;
        var tokenService = plugin.modules().tokens();
        for (TeamDatabase.Member member : database.getMembers(teamId)) {
            kills += member.kills();
            playtime += member.playtimeMs();
            if (tokenService != null) tokens += tokenService.getBalance(member.uuid());
        }
        send(player, "stats", "%team%", team.name(),
                "%tokens%", String.valueOf(tokens),
                "%kills%", String.valueOf(kills),
                "%playtime%", formatPlaytime(playtime));
    }

    private void handleLeaderboard(Player player) {
        List<TeamDatabase.Team> teams = database.listTeams();
        long tokenWeight = config.getLong("leaderboard.token-weight", 1L);
        long killWeight = config.getLong("leaderboard.kill-weight", 100L);
        long hourWeight = config.getLong("leaderboard.playtime-hour-weight", 50L);
        var tokenService = plugin.modules().tokens();

        record Scored(TeamDatabase.Team team, long score, long tokens, int kills, long playtime) {
        }
        List<Scored> scored = new ArrayList<>();
        for (TeamDatabase.Team team : teams) {
            long tokens = 0;
            int kills = 0;
            long playtime = 0;
            for (TeamDatabase.Member member : database.getMembers(team.id())) {
                kills += member.kills();
                playtime += member.playtimeMs();
                if (tokenService != null) tokens += tokenService.getBalance(member.uuid());
            }
            long hours = playtime / 3_600_000L;
            long score = tokens * tokenWeight + kills * killWeight + hours * hourWeight;
            scored.add(new Scored(team, score, tokens, kills, playtime));
        }
        scored.sort(Comparator.comparingLong(Scored::score).reversed());
        send(player, "leaderboard-header");
        int limit = config.getInt("leaderboard.limit", 10);
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            Scored entry = scored.get(i);
            send(player, "leaderboard-line",
                    "%rank%", String.valueOf(i + 1),
                    "%team%", entry.team.name(),
                    "%score%", String.valueOf(entry.score),
                    "%tokens%", String.valueOf(entry.tokens),
                    "%kills%", String.valueOf(entry.kills),
                    "%playtime%", formatPlaytime(entry.playtime));
        }
    }

    void handleEmergency(Player player) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        long cooldownMs = config.getLong("emergency.cooldown-seconds", 60L) * 1000L;
        long now = System.currentTimeMillis();
        Long last = emergencyCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000L;
            send(player, "emergency-cooldown", "%seconds%", String.valueOf(left));
            return;
        }
        emergencyCooldown.put(player.getUniqueId(), now);
        Location loc = player.getLocation();
        String msg = raw("emergency-alert", "%player%", player.getName(),
                "%world%", loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                "%x%", String.valueOf(loc.getBlockX()),
                "%y%", String.valueOf(loc.getBlockY()),
                "%z%", String.valueOf(loc.getBlockZ()));
        for (TeamDatabase.Member member : database.getMembers(teamId)) {
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null && !online.equals(player)) online.sendMessage(Text.c(msg));
        }
        send(player, "emergency-sent");
    }

    private void handleAllyRequest(Player player, String teamName) {
        Integer teamId = requireTeam(player);
        if (teamId == null) return;
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        if (self == null || self.role() > TeamDatabase.ROLE_OFFICER) {
            send(player, "no-permission-rank");
            return;
        }
        if (database.allyCount(teamId) >= config.getInt("ally.max-allies", 1)) {
            send(player, "ally-max");
            return;
        }
        TeamDatabase.Team targetTeam = database.getTeamByName(teamName);
        if (targetTeam == null) {
            send(player, "team-not-found", "%team%", teamName);
            return;
        }
        if (targetTeam.id() == teamId) {
            send(player, "ally-self");
            return;
        }
        if (database.allyCount(targetTeam.id()) >= config.getInt("ally.max-allies", 1)) {
            send(player, "ally-target-full", "%team%", targetTeam.name());
            return;
        }
        long expires = System.currentTimeMillis() + config.getLong("ally.request-lifetime-seconds", 120L) * 1000L;
        database.addAllyRequest(teamId, targetTeam.id(), expires);
        send(player, "ally-request-sent", "%team%", targetTeam.name());
        TeamDatabase.Team myTeam = database.getTeamById(teamId);
        for (TeamDatabase.Member member : database.getMembers(targetTeam.id())) {
            if (member.role() > TeamDatabase.ROLE_OFFICER) continue;
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null) {
                send(online, "ally-request-received", "%team%", myTeam.name());
            }
        }
    }

    private void handleAllyAccept(Player player) {
        Integer teamId = database.getTeamId(player.getUniqueId());
        if (teamId == null) {
            send(player, "not-in-team");
            return;
        }
        TeamDatabase.Member self = database.getMember(teamId, player.getUniqueId());
        if (self == null || self.role() > TeamDatabase.ROLE_OFFICER) {
            send(player, "no-permission-rank");
            return;
        }
        TeamDatabase.AllyRequest request = database.getIncomingAllyRequest(teamId);
        if (request == null) {
            send(player, "no-ally-request");
            return;
        }
        if (database.allyCount(teamId) >= config.getInt("ally.max-allies", 1)
                || database.allyCount(request.fromTeamId()) >= config.getInt("ally.max-allies", 1)) {
            send(player, "ally-max");
            database.removeAllyRequest(request.fromTeamId(), request.toTeamId());
            return;
        }
        database.addAllyPair(teamId, request.fromTeamId());
        database.removeAllyRequest(request.fromTeamId(), request.toTeamId());
        TeamDatabase.Team a = database.getTeamById(teamId);
        TeamDatabase.Team b = database.getTeamById(request.fromTeamId());
        send(player, "ally-formed", "%team%", b.name());
        broadcastTeam(teamId, raw("ally-formed-broadcast", "%team%", b.name()));
        broadcastTeam(request.fromTeamId(), raw("ally-formed-broadcast", "%team%", a.name()));
    }

    void toggleTeamChat(Player player) {
        if (database.getTeamId(player.getUniqueId()) == null) {
            send(player, "not-in-team");
            return;
        }
        if (teamChatMode.contains(player.getUniqueId())) {
            teamChatMode.remove(player.getUniqueId());
            send(player, "chat-disabled");
        } else {
            teamChatMode.add(player.getUniqueId());
            send(player, "chat-enabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingTeamName.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            awaitingTeamName.remove(player.getUniqueId());
            if (message.equalsIgnoreCase("cancel")) {
                send(player, "create-cancelled");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (validateTeamName(player, message)) {
                    guiHandler.openCreateConfirm(player, message);
                }
            });
            return;
        }
        if (!teamChatMode.contains(player.getUniqueId())) return;
        Integer teamId = database.getTeamId(player.getUniqueId());
        if (teamId == null) {
            teamChatMode.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message.isBlank()) return;
        String format = config.getString("chat.team-format", "&#FF3399[TEAM] &f%player% &8» &7%message%");
        String out = Text.apply(format, "%player%", player.getName(), "%message%", message);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (TeamDatabase.Member member : database.getMembers(teamId)) {
                Player online = Bukkit.getPlayer(member.uuid());
                if (online != null) online.sendMessage(Text.c(out));
            }
            for (int allyId : database.getAllies(teamId)) {
                for (TeamDatabase.Member member : database.getMembers(allyId)) {
                    Player online = Bukkit.getPlayer(member.uuid());
                    if (online != null) {
                        String allyFormat = config.getString("chat.ally-format", "&#ffb300[ALLY] &f%player% &8» &7%message%");
                        online.sendMessage(Text.c(Text.apply(allyFormat, "%player%", player.getName(), "%message%", message)));
                    }
                }
            }
        });
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || database == null) return;
        if (database.getTeamId(killer.getUniqueId()) != null) {
            database.incrementKills(killer.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlineSince.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushPlaytime(uuid);
        teamChatMode.remove(uuid);
        awaitingTeamName.remove(uuid);
        onlineSince.remove(uuid);
        emergencyCooldown.remove(uuid);
    }

    private void flushPlaytime(UUID uuid) {
        Long since = onlineSince.get(uuid);
        if (since == null || database == null) return;
        if (database.getTeamId(uuid) == null) return;
        database.addPlaytime(uuid, System.currentTimeMillis() - since);
    }

    private void flushPlaytimeAll() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(onlineSince).entrySet()) {
            if (database.getTeamId(entry.getKey()) != null) {
                database.addPlaytime(entry.getKey(), now - entry.getValue());
            }
        }
    }

    private Integer requireTeam(Player player) {
        Integer teamId = database.getTeamId(player.getUniqueId());
        if (teamId == null) send(player, "not-in-team");
        return teamId;
    }

    private boolean isLeader(Player player, int teamId) {
        TeamDatabase.Team team = database.getTeamById(teamId);
        return team != null && team.leaderUuid().equals(player.getUniqueId());
    }

    private void broadcastTeam(int teamId, String message) {
        for (TeamDatabase.Member member : database.getMembers(teamId)) {
            Player online = Bukkit.getPlayer(member.uuid());
            if (online != null) online.sendMessage(Text.c(message));
        }
    }

    private String name(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("sharded.teams.use")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0],
                    "create", "invite", "accept", "kick", "leave", "disband", "promote", "demote",
                    "members", "stats", "leaderboard", "emergency", "ally", "chat", "gui");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("promote") || sub.equals("demote")) {
                return TabCompleteHelper.onlinePlayers(args[1]);
            }
            if (sub.equals("ally")) {
                List<String> names = database.listTeams().stream().map(TeamDatabase.Team::name).collect(Collectors.toList());
                names.add("accept");
                return TabCompleteHelper.filter(args[1], names);
            }
            if (sub.equals("create")) return List.of("<name>");
        }
        return List.of();
    }
}
