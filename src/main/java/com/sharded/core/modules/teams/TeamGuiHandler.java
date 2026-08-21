package com.sharded.core.modules.teams;

import com.sharded.core.modules.leaderboards.LeaderboardsModule;
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

/** Dynamic team menus (create flow, members, browse, profile). */
final class TeamGuiHandler {

    enum MenuType { CREATE_START, CREATE_CONFIRM, DISBAND_CONFIRM, MAIN, MEMBERS, BROWSE, PROFILE }

    static final class TeamGuiHolder implements InventoryHolder {
        final MenuType type;
        final String pendingName;
        final int teamId;
        final int page;
        Inventory inventory;

        TeamGuiHolder(MenuType type, String pendingName, int teamId, int page) {
            this.type = type;
            this.pendingName = pendingName;
            this.teamId = teamId;
            this.page = page;
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
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.CREATE_START, null, 0, 0), 27,
                Text.c(module.guiRaw("create-title")));
        fill(inv);
        inv.setItem(13, button(Material.ANVIL, module.guiRaw("create-anvil-name"),
                module.guiRawList("create-anvil-lore")));
        player.openInventory(inv);
    }

    void openCreateConfirm(Player player, String name) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.CREATE_CONFIRM, name, 0, 0), 27,
                Text.c(module.guiRaw("confirm-title", "%team%", name)));
        fill(inv);
        inv.setItem(11, button(Material.RED_STAINED_GLASS_PANE, module.guiRaw("cancel-name"),
                module.guiRawList("cancel-lore")));
        inv.setItem(13, new ItemBuilder(Material.NAME_TAG)
                .name("&f" + name)
                .lore(module.guiRawList("confirm-lore"))
                .build());
        inv.setItem(15, button(Material.LIME_STAINED_GLASS_PANE, module.guiRaw("confirm-name"),
                module.guiRawList("confirm-lore")));
        player.openInventory(inv);
    }

    void openDisbandConfirm(Player player, String teamName) {
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.DISBAND_CONFIRM, teamName, 0, 0), 27,
                Text.c(module.guiRaw("disband-confirm-title", "%team%", teamName)));
        fill(inv);
        inv.setItem(11, button(Material.RED_STAINED_GLASS_PANE, module.guiRaw("cancel-name"),
                module.guiRawList("cancel-lore")));
        inv.setItem(13, button(Material.TNT, module.guiRaw("disband-name"),
                module.guiRawList("disband-lore")));
        inv.setItem(15, button(Material.LIME_STAINED_GLASS_PANE, module.guiRaw("disband-confirm-name"),
                module.guiRawList("disband-confirm-lore")));
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

        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.MAIN, null, 0, 0), 27,
                Text.c(module.guiRaw("main-title", "%team%", team == null ? "?" : team.name())));
        fill(inv);

        UUID ownerId = team == null ? player.getUniqueId() : team.leaderUuid();
        inv.setItem(11, ownerHead(ownerId, module.guiRaw("members-name"),
                module.guiRawList("members-lore", "%count%", String.valueOf(module.database().getMembers(teamId).size()))));
        inv.setItem(12, button(Material.BELL, module.guiRaw("emergency-name"), module.guiRawList("emergency-lore")));
        inv.setItem(13, button(Material.ALLAY_SPAWN_EGG, module.guiRaw("browse-name"), module.guiRawList("browse-lore")));
        inv.setItem(14, button(Material.WRITABLE_BOOK, module.guiRaw("chat-name"),
                module.guiRawList("chat-lore", "%status%", module.isTeamChat(player.getUniqueId()) ? "&aON" : "&cOFF")));
        inv.setItem(15, button(Material.WHITE_BANNER, module.guiRaw("allies-name"),
                module.guiRawList("allies-lore", "%count%", String.valueOf(module.database().allyCount(teamId)),
                        "%max%", String.valueOf(module.teamConfig().getInt("ally.max-allies", 1)))));
        inv.setItem(22, button(Material.GOLD_BLOCK, module.guiRaw("leaderboard-name"),
                module.guiRawList("leaderboard-lore")));

        if (leader) {
            inv.setItem(4, button(Material.DARK_OAK_DOOR, module.guiRaw("disband-name"), module.guiRawList("disband-lore")));
        } else if (self != null) {
            inv.setItem(4, button(Material.OAK_DOOR, module.guiRaw("leave-name"), module.guiRawList("leave-lore")));
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
        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.MEMBERS, null, 0, 0), size,
                Text.c(module.guiRaw("members-title", "%team%", team == null ? "?" : team.name())));
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

        inv.setItem(49, button(Material.ARROW, module.guiRaw("back-name"), List.of()));
        player.openInventory(inv);
    }

    void openBrowse(Player player, int page) {
        List<TeamDatabase.Team> teams = new ArrayList<>(module.database().listTeams());
        teams.sort(Comparator.comparing(TeamDatabase.Team::name, String.CASE_INSENSITIVE_ORDER));
        int perPage = module.teamConfig().getInt("gui.browse-per-page", 21);
        int maxPage = Math.max(0, (teams.size() + perPage - 1) / perPage - 1);
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.BROWSE, null, 0, page), 54,
                Text.c(module.guiRaw("browse-title")));
        fill(inv, 54);

        int start = page * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(start + perPage, teams.size()); i++) {
            while (slot < 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot > 43) break;
            TeamDatabase.Team team = teams.get(i);
            inv.setItem(slot++, browseItem(team));
        }

        if (page > 0) {
            inv.setItem(45, navItem(Material.ARROW, module.guiRaw("previous-name")));
        }
        if (page < maxPage) {
            inv.setItem(52, navItem(Material.ARROW, module.guiRaw("next-name")));
        }
        inv.setItem(53, button(Material.ARROW, module.guiRaw("back-name"), List.of()));
        player.openInventory(inv);
    }

    void openProfile(Player player, int teamId) {
        TeamDatabase.Team team = module.database().getTeamById(teamId);
        if (team == null) {
            openBrowse(player, 0);
            return;
        }
        TeamStats stats = computeStats(teamId);
        int rank = teamRank(teamId);
        String rankText = rank > 0 ? String.valueOf(rank) : module.guiRaw("unranked");
        long score = teamScore(teamId, stats);

        Inventory inv = Bukkit.createInventory(new TeamGuiHolder(MenuType.PROFILE, null, teamId, 0), 27,
                Text.c(module.guiRaw("profile-title", "%team%", team.name())));
        fill(inv);

        inv.setItem(13, ownerHead(team.leaderUuid(),
                module.guiRaw("profile-item-name", "%team%", team.name()),
                module.guiRawList("profile-item-lore",
                        "%rank%", rankText,
                        "%count%", String.valueOf(module.database().getMembers(teamId).size()),
                        "%kills%", String.valueOf(stats.kills),
                        "%tokens%", String.valueOf(stats.tokens),
                        "%playtime%", module.formatPlaytime(stats.playtime),
                        "%score%", String.valueOf(score))));
        inv.setItem(22, button(Material.ARROW, module.guiRaw("back-name"), List.of()));
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
            case BROWSE -> handleBrowseClick(player, holder, slot);
            case PROFILE -> {
                if (slot == 22) openBrowse(player, 0);
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
            case 11 -> openMembers(player);
            case 12 -> {
                player.closeInventory();
                module.handleEmergency(player);
            }
            case 13 -> openBrowse(player, 0);
            case 14 -> {
                module.toggleTeamChat(player);
                openMain(player);
            }
            case 15 -> module.send(player, "gui-ally-hint");
            case 22 -> {
                player.closeInventory();
                player.performCommand("leaderboard teams");
            }
        }
    }

    private void handleBrowseClick(Player player, TeamGuiHolder holder, int slot) {
        if (slot == 53) {
            openMain(player);
            return;
        }
        if (slot == 45 && holder.page > 0) {
            openBrowse(player, holder.page - 1);
            return;
        }
        if (slot == 52) {
            openBrowse(player, holder.page + 1);
            return;
        }
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;
        Integer teamId = module.teamIdFromItem(item);
        if (teamId != null) openProfile(player, teamId);
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

    private ItemStack browseItem(TeamDatabase.Team team) {
        int rank = teamRank(team.id());
        String rankText = rank > 0 ? String.valueOf(rank) : module.guiRaw("unranked");
        ItemStack head = ownerHead(team.leaderUuid(),
                module.guiRaw("browse-entry-name", "%team%", team.name()),
                module.guiRawList("browse-entry-lore",
                        "%count%", String.valueOf(module.database().getMembers(team.id()).size()),
                        "%rank%", rankText));
        return module.tagTeamId(head, team.id());
    }

    private ItemStack navItem(Material mat, String name) {
        return new ItemBuilder(mat).name(name)
                .lore(List.of("", module.guiRaw("click-footer") + "To View"))
                .build();
    }

    private int teamRank(int teamId) {
        LeaderboardsModule lb = module.plugin().modules().get(LeaderboardsModule.class);
        if (lb == null) return -1;
        return lb.teamRank(teamId);
    }

    private long teamScore(int teamId, TeamStats stats) {
        long tokenWeight = module.teamConfig().getLong("leaderboard.token-weight", 1L);
        long killWeight = module.teamConfig().getLong("leaderboard.kill-weight", 100L);
        long hourWeight = module.teamConfig().getLong("leaderboard.playtime-hour-weight", 50L);
        long hours = stats.playtime / 3_600_000L;
        return stats.tokens * tokenWeight + stats.kills * killWeight + hours * hourWeight;
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
                ? module.guiRawList("leader-head-lore", "%status%", status)
                : module.guiRawList("member-head-lore", "%role%", module.roleName(role), "%status%", status);
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
