package com.sharded.core.modules.teams;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Dynamic team menus (create flow, members, leaderboard, stats). */
final class TeamGuiHandler {

    enum MenuType { CREATE_START, CREATE_CONFIRM, DISBAND_CONFIRM, MAIN, MEMBERS, STATS, LEADERBOARD }

    static final class TeamGuiHolder implements InventoryHolder {
        final MenuType type;
        final String pendingName;
        Inventory inventory;

        TeamGuiHolder(MenuType type, String pendingName) {
            this.type = type;
            this.pendingName = pendingName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TeamsModule module;

    TeamGuiHandler(TeamsModule module) {
        this.module = module;
    }

    void openFor(Player player) {
        Integer teamId = module.database().getTeamId(player.getUniqueId());
        if (teamId == null) openCreateStart(player);
        else openMain(player);
    }

    void openCreateStart(Player player) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.CREATE_START, null), 27,
                Text.c(module.raw("gui-create-title")));
        fill(inv);
        inv.setItem(13, new ItemBuilder(Material.ANVIL)
                .name(module.raw("gui-create-anvil-name"))
                .lore(module.rawList("gui-create-anvil-lore"))
                .build());
        player.openInventory(inv);
    }

    void openCreateConfirm(Player player, String name) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.CREATE_CONFIRM, name), 27,
                Text.c(module.raw("gui-confirm-title", "%team%", name)));
        fill(inv);
        inv.setItem(11, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(module.raw("gui-cancel-name"))
                .lore(module.rawList("gui-cancel-lore"))
                .build());
        inv.setItem(13, new ItemBuilder(Material.NAME_TAG)
                .name("&f" + name)
                .lore(module.rawList("gui-confirm-lore"))
                .build());
        inv.setItem(15, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(module.raw("gui-confirm-name"))
                .lore(module.rawList("gui-confirm-lore"))
                .build());
        player.openInventory(inv);
    }

    void openDisbandConfirm(Player player, String teamName) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.DISBAND_CONFIRM, teamName), 27,
                Text.c(module.raw("gui-disband-confirm-title", "%team%", teamName)));
        fill(inv);
        inv.setItem(11, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(module.raw("gui-cancel-name"))
                .lore(module.rawList("gui-cancel-lore"))
                .build());
        inv.setItem(13, new ItemBuilder(Material.TNT)
                .name(module.raw("gui-disband-name"))
                .lore(module.rawList("gui-disband-lore"))
                .build());
        inv.setItem(15, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(module.raw("gui-disband-confirm-name"))
                .lore(module.rawList("gui-disband-confirm-lore"))
                .build());
        player.openInventory(inv);
    }

    void openMain(Player player) {
        Integer teamId = module.database().getTeamId(player.getUniqueId());
        if (teamId == null) {
            openCreateStart(player);
            return;
        }
        TeamDatabase.Team team = module.database().getTeamById(teamId);
        TeamDatabase.Member self = module.database().getMember(teamId, player.getUniqueId());
        boolean leader = team != null && team.leaderUuid().equals(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.MAIN, null), 27,
                Text.c(module.raw("gui-main-title", "%team%", team == null ? "?" : team.name())));
        fill(inv);

        UUID ownerId = team == null ? player.getUniqueId() : team.leaderUuid();
        inv.setItem(10, ownerHead(ownerId, module.raw("gui-members-name"),
                module.rawList("gui-members-lore", "%count%", String.valueOf(module.database().getMembers(teamId).size()))));
        inv.setItem(12, button(Material.BOOK, module.raw("gui-stats-name"), module.rawList("gui-stats-lore")));
        inv.setItem(13, button(Material.MOJANG_BANNER_PATTERN, module.raw("gui-leaderboard-name"), module.rawList("gui-leaderboard-lore")));
        inv.setItem(14, button(Material.BELL, module.raw("gui-emergency-name"), module.rawList("gui-emergency-lore")));
        inv.setItem(16, button(Material.WRITABLE_BOOK, module.raw("gui-chat-name"),
                module.rawList("gui-chat-lore", "%status%", module.isTeamChat(player.getUniqueId()) ? "&aON" : "&cOFF")));
        inv.setItem(22, button(Material.WHITE_BANNER, module.raw("gui-allies-name"),
                module.rawList("gui-allies-lore", "%count%", String.valueOf(module.database().allyCount(teamId)),
                        "%max%", String.valueOf(module.teamConfig().getInt("ally.max-allies", 1)))));

        if (leader) {
            inv.setItem(4, button(Material.DARK_OAK_DOOR, module.raw("gui-disband-name"), module.rawList("gui-disband-lore")));
        } else if (self != null) {
            inv.setItem(4, button(Material.OAK_DOOR, module.raw("gui-leave-name"), module.rawList("gui-leave-lore")));
        }
        player.openInventory(inv);
    }

    void openMembers(Player player) {
        Integer teamId = module.database().getTeamId(player.getUniqueId());
        if (teamId == null) return;
        TeamDatabase.Team team = module.database().getTeamById(teamId);
        List<TeamDatabase.Member> members = new ArrayList<>(module.database().getMembers(teamId));
        members.sort(Comparator.comparingInt(TeamDatabase.Member::role));

        int size = 54;
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.MEMBERS, null), size,
                Text.c(module.raw("gui-members-title", "%team%", team == null ? "?" : team.name())));
        fill(inv, size);

        if (team != null) {
            inv.setItem(4, memberHead(team.leaderUuid(), TeamDatabase.ROLE_LEADER, true));
        }

        int slot = 10;
        for (TeamDatabase.Member member : members) {
            if (team != null && member.uuid().equals(team.leaderUuid())) continue;
            while (slot < size && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot >= size - 9) break;
            inv.setItem(slot++, memberHead(member.uuid(), member.role(), false));
        }

        inv.setItem(49, button(Material.ARROW, module.raw("gui-back-name"), List.of()));
        player.openInventory(inv);
    }

    void openStats(Player player) {
        Integer teamId = module.database().getTeamId(player.getUniqueId());
        if (teamId == null) return;
        TeamStats stats = computeStats(teamId);

        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.STATS, null), 27,
                Text.c(module.raw("gui-stats-title")));
        fill(inv);

        inv.setItem(10, button(Material.DIAMOND_SWORD, module.raw("gui-stat-kills-name"),
                module.rawList("gui-stat-kills-lore", "%kills%", String.valueOf(stats.kills))));
        inv.setItem(12, button(Material.GOLD_INGOT, module.raw("gui-stat-tokens-name"),
                module.rawList("gui-stat-tokens-lore", "%tokens%", String.valueOf(stats.tokens))));
        inv.setItem(14, button(Material.CLOCK, module.raw("gui-stat-playtime-name"),
                module.rawList("gui-stat-playtime-lore", "%playtime%", module.formatPlaytime(stats.playtime))));
        inv.setItem(16, button(Material.EXPERIENCE_BOTTLE, module.raw("gui-stats-name"),
                module.rawList("gui-stats-lore")));
        inv.setItem(22, button(Material.ARROW, module.raw("gui-back-name"), List.of()));
        player.openInventory(inv);
    }

    void openLeaderboard(Player player) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.LEADERBOARD, null), 54,
                Text.c(module.raw("gui-leaderboard-title")));
        fill(inv, 54);

        List<TeamDatabase.Team> teams = module.database().listTeams();
        var tokenService = module.plugin().modules().tokens();
        long tokenWeight = module.teamConfig().getLong("leaderboard.token-weight", 1L);
        long killWeight = module.teamConfig().getLong("leaderboard.kill-weight", 100L);
        long hourWeight = module.teamConfig().getLong("leaderboard.playtime-hour-weight", 50L);

        record Scored(TeamDatabase.Team team, long score, long tokens, int kills, long playtime) {}
        List<Scored> scored = new ArrayList<>();
        for (TeamDatabase.Team team : teams) {
            long tokens = 0;
            int kills = 0;
            long playtime = 0;
            for (TeamDatabase.Member member : module.database().getMembers(team.id())) {
                kills += member.kills();
                playtime += member.playtimeMs();
                if (tokenService != null) tokens += tokenService.getBalance(member.uuid());
            }
            long hours = playtime / 3_600_000L;
            long score = tokens * tokenWeight + kills * killWeight + hours * hourWeight;
            scored.add(new Scored(team, score, tokens, kills, playtime));
        }
        scored.sort(Comparator.comparingLong(Scored::score).reversed());

        int slot = 10;
        int limit = Math.min(10, scored.size());
        for (int i = 0; i < limit; i++) {
            while (slot < 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            Scored entry = scored.get(i);
            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name(module.raw("gui-leaderboard-line-name", "%rank%", String.valueOf(i + 1), "%team%", entry.team.name()))
                    .lore(module.rawList("gui-leaderboard-line-lore",
                            "%score%", String.valueOf(entry.score),
                            "%tokens%", String.valueOf(entry.tokens),
                            "%kills%", String.valueOf(entry.kills),
                            "%playtime%", module.formatPlaytime(entry.playtime)))
                    .build());
        }
        inv.setItem(49, button(Material.ARROW, module.raw("gui-back-name"), List.of()));
        player.openInventory(inv);
    }

    void handleClick(Player player, TeamGuiHolder holder, int slot, ItemStack current) {
        switch (holder.type) {
            case CREATE_START -> {
                if (slot == 13) module.beginCreateNameInput(player);
            }
            case CREATE_CONFIRM -> {
                if (slot == 11) openCreateStart(player);
                else if (slot == 15 && holder.pendingName != null) {
                    module.confirmCreate(player, holder.pendingName);
                    openMain(player);
                }
            }
            case DISBAND_CONFIRM -> {
                if (slot == 11) openMain(player);
                else if (slot == 15 && holder.pendingName != null) {
                    player.closeInventory();
                    module.handleDisband(player);
                }
            }
            case MAIN -> handleMainClick(player, slot);
            case MEMBERS -> handleMembersClick(player, slot, current);
            case STATS -> handleStatsClick(player, slot);
            case LEADERBOARD -> {
                if (slot == 49) openMain(player);
            }
        }
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 4 -> {
                Integer teamId = module.database().getTeamId(player.getUniqueId());
                if (teamId == null) return;
                TeamDatabase.Team team = module.database().getTeamById(teamId);
                if (team != null && team.leaderUuid().equals(player.getUniqueId())) {
                    openDisbandConfirm(player, team.name());
                } else {
                    player.closeInventory();
                    module.handleLeave(player);
                }
            }
            case 10 -> openMembers(player);
            case 12 -> openStats(player);
            case 13 -> openLeaderboard(player);
            case 14 -> {
                player.closeInventory();
                module.handleEmergency(player);
            }
            case 16 -> {
                module.toggleTeamChat(player);
                openMain(player);
            }
            case 22 -> module.send(player, "gui-ally-hint");
        }
    }

    private void handleStatsClick(Player player, int slot) {
        Integer teamId = module.database().getTeamId(player.getUniqueId());
        if (teamId == null) return;
        TeamStats stats = computeStats(teamId);
        switch (slot) {
            case 10 -> module.send(player, "stats-kills", "%kills%", String.valueOf(stats.kills));
            case 12 -> module.send(player, "stats-tokens", "%tokens%", String.valueOf(stats.tokens));
            case 14 -> module.send(player, "stats-playtime", "%playtime%", module.formatPlaytime(stats.playtime));
            case 22 -> openMain(player);
        }
    }

    private void handleMembersClick(Player player, int slot, ItemStack item) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        if (!(item.getItemMeta() instanceof SkullMeta meta) || meta.getOwningPlayer() == null) return;
        UUID targetId = meta.getOwningPlayer().getUniqueId();
        if (targetId.equals(player.getUniqueId())) return;
        player.closeInventory();
        module.handleMemberHeadClick(player, targetId);
        Bukkit.getScheduler().runTaskLater(module.plugin(), () -> openMembers(player), 2L);
    }

    private TeamStats computeStats(int teamId) {
        long tokens = 0;
        int kills = 0;
        long playtime = 0;
        var tokenService = module.plugin().modules().tokens();
        for (TeamDatabase.Member member : module.database().getMembers(teamId)) {
            kills += member.kills();
            playtime += member.playtimeMs();
            if (tokenService != null) tokens += tokenService.getBalance(member.uuid());
        }
        return new TeamStats(tokens, kills, playtime);
    }

    private ItemStack memberHead(UUID uuid, int role, boolean leader) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Text.c("&f" + OfflinePlayers.name(uuid)));
        String status = Bukkit.getPlayer(uuid) != null ? module.raw("online") : module.raw("offline");
        List<String> lore = leader
                ? module.rawList("gui-leader-head-lore", "%status%", status)
                : module.rawList("gui-member-head-lore", "%role%", module.roleName(role), "%status%", status);
        meta.lore(lore.stream().map(Text::c).toList());
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack ownerHead(UUID ownerId, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId));
        meta.displayName(Text.c(name));
        meta.lore(lore.stream().map(Text::c).toList());
        head.setItemMeta(meta);
        return head;
    }

    private static void fill(Inventory inv) {
        fill(inv, inv.getSize());
    }

    private static void fill(Inventory inv, int size) {
        ItemStack pane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private record TeamStats(long tokens, int kills, long playtime) {}
}
