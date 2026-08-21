package com.sharded.core.modules.leaderboards;

import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** Paginated stats and leaderboard GUIs. */
final class LeaderboardGuiHandler {

    static final String CLICK = "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0";

    enum View { HUB, BOARD, STATS }

    static final class Holder implements InventoryHolder {
        final View view;
        final String boardType;
        final int page;
        final UUID statsTarget;
        Inventory inventory;

        Holder(View view, String boardType, int page, UUID statsTarget) {
            this.view = view;
            this.boardType = boardType;
            this.page = page;
            this.statsTarget = statsTarget;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LeaderboardsModule module;
    private final LeaderboardService service;
    private final YamlConfiguration cfg;

    LeaderboardGuiHandler(LeaderboardsModule module, LeaderboardService service, YamlConfiguration cfg) {
        this.module = module;
        this.service = service;
        this.cfg = cfg;
    }

    void openHub(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(View.HUB, "", 0, null), 27,
                Text.c(cfg.getString("gui.hub-title", "&8Leaderboards")));
        Holder holder = (Holder) inv.getHolder();
        holder.inventory = inv;
        fill(inv, 27);
        putHubItem(inv, "tokens", Material.SUNFLOWER, 11);
        putHubItem(inv, "kills", Material.DIAMOND_SWORD, 12);
        putHubItem(inv, "deaths", Material.SKELETON_SKULL, 13);
        putHubItem(inv, "killstreaks", Material.NETHERITE_SWORD, 14);
        putHubItem(inv, "playtime", Material.CLOCK, 15);
        putHubItem(inv, "teams", Material.PINK_BANNER, 16);
        player.openInventory(inv);
    }

    void openBoard(Player player, String type, int page) {
        List<LeaderboardService.Entry> all = service.entries(type);
        int perPage = cfg.getInt("gui.entries-per-page", 10);
        int maxPage = Math.max(0, (all.size() + perPage - 1) / perPage - 1);
        page = Math.max(0, Math.min(page, maxPage));

        String titleKey = "gui.board-title." + type.toLowerCase();
        Inventory inv = Bukkit.createInventory(new Holder(View.BOARD, type, page, null), 36,
                Text.c(cfg.getString(titleKey, "&8Leaderboard")));
        Holder holder = (Holder) inv.getHolder();
        holder.inventory = inv;
        fill(inv, 36);

        int start = page * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(start + perPage, all.size()); i++) {
            if (slot == 17) slot = 19;
            if (slot > 21) break;
            LeaderboardService.Entry entry = all.get(i);
            int rank = i + 1;
            inv.setItem(slot++, entryHead(entry, type, rank));
        }

        int selfRank = resolveSelfRank(player, type);
        inv.setItem(31, selfHead(player, type, selfRank, service.entries(type)));

        if (page > 0) {
            inv.setItem(27, navItem(Material.ARROW, cfg.getString("gui.previous-name", "&ePrevious Page"), page - 1));
        }
        if (page < maxPage) {
            inv.setItem(35, navItem(Material.ARROW, cfg.getString("gui.next-name", "&eNext Page"), page + 1));
        }
        inv.setItem(26, backItem());
        player.openInventory(inv);
    }

    void openStats(Player viewer, UUID targetId) {
        LeaderboardService.StatsSnapshot stats = service.statsFor(targetId);
        Inventory inv = Bukkit.createInventory(new Holder(View.STATS, "", 0, targetId), 27,
                Text.c(cfg.getString("gui.stats-title", "&8Stats")));
        Holder holder = (Holder) inv.getHolder();
        holder.inventory = inv;
        fill(inv, 27);

        inv.setItem(10, headItem(targetId, stats.name(),
                lore(cfg, "gui.stats-head-lore", stats)));
        inv.setItem(11, statItem("kills", Material.DIAMOND_SWORD, stats.kills()));
        inv.setItem(12, statItem("deaths", Material.SKELETON_SKULL, stats.deaths()));
        inv.setItem(14, statItem("killstreaks", Material.NETHERITE_SWORD, stats.bestStreak()));
        inv.setItem(15, statItem("tokens", Material.SUNFLOWER, stats.tokens()));
        inv.setItem(16, statItem("playtime", Material.CLOCK, stats.playMinutes()));
        inv.setItem(13, teamItem(stats.team()));
        inv.setItem(26, closeItem());
        viewer.openInventory(inv);
    }

    void handleClick(Player player, Holder holder, int slot) {
        switch (holder.view) {
            case HUB -> {
                String type = hubTypeForSlot(slot);
                if (type != null) openBoard(player, type, 0);
            }
            case BOARD -> {
                if (slot == 26) openHub(player);
                else if (slot == 27 && holder.page > 0) openBoard(player, holder.boardType, holder.page - 1);
                else if (slot == 35) openBoard(player, holder.boardType, holder.page + 1);
            }
            case STATS -> {
                if (slot == 26) player.closeInventory();
            }
        }
    }

    private String hubTypeForSlot(int slot) {
        return switch (slot) {
            case 11 -> "tokens";
            case 12 -> "kills";
            case 13 -> "deaths";
            case 14 -> "killstreaks";
            case 15 -> "playtime";
            case 16 -> "teams";
            default -> null;
        };
    }

    private ItemStack entryHead(LeaderboardService.Entry entry, String type, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (entry.uuid() != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
        String color = cfg.getString("gui.colors." + type.toLowerCase(), "&f");
        meta.displayName(Text.c(color + "#" + rank + " &f" + entry.displayName()));
        meta.lore(loreLine(type, rank, service.formatValue(type, entry.value())).stream().map(Text::c).toList());
        head.setItemMeta(meta);
        return head;
    }

    private int resolveSelfRank(Player player, String type) {
        if (type.equalsIgnoreCase("teams")) {
            TeamsModule teams = module.plugin().modules().get(TeamsModule.class);
            if (teams != null && teams.database() != null) {
                Integer teamId = teams.database().getTeamId(player.getUniqueId());
                if (teamId != null) return service.teamRank(teamId);
            }
            return -1;
        }
        return service.rankOf(type, player.getUniqueId());
    }

    private ItemStack selfHead(Player player, String type, int rank, List<LeaderboardService.Entry> all) {
        if (type.equalsIgnoreCase("teams")) {
            TeamsModule teams = module.plugin().modules().get(TeamsModule.class);
            if (teams != null && teams.database() != null) {
                Integer teamId = teams.database().getTeamId(player.getUniqueId());
                if (teamId != null) {
                    for (LeaderboardService.Entry e : all) {
                        if (String.valueOf(teamId).equals(e.key())) {
                            return teamSelfHead(player, rank, e);
                        }
                    }
                }
            }
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        String color = cfg.getString("gui.colors." + type.toLowerCase(), "&f");
        long value = 0;
        for (LeaderboardService.Entry e : all) {
            if (player.getUniqueId().equals(e.uuid())) {
                value = e.value();
                break;
            }
        }
        String rankText = rank > 0 ? "#" + rank : cfg.getString("gui.unranked", "Unranked");
        meta.displayName(Text.c(color + "&lYou &7(" + rankText + "&7)"));
        meta.lore(loreLine(type, rank, service.formatValue(type, value)).stream().map(Text::c).toList());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack teamSelfHead(Player player, int rank, LeaderboardService.Entry entry) {
        ItemStack head = new ItemStack(Material.PINK_BANNER);
        String color = cfg.getString("gui.colors.teams", "&d");
        String rankText = rank > 0 ? "#" + rank : cfg.getString("gui.unranked", "Unranked");
        head = new ItemBuilder(head)
                .name(color + "&lYour Team &7(" + rankText + "&7)")
                .lore(loreLine("teams", rank, service.formatValue("teams", entry.value())))
                .build();
        return head;
    }

    private List<String> loreLine(String type, int rank, String value) {
        String color = cfg.getString("gui.colors." + type.toLowerCase(), "&f");
        String label = cfg.getString("gui.labels." + type.toLowerCase(), type);
        return List.of(
                "&8Leaderboard",
                "",
                color + "Information:",
                color + "| &fRank: " + color + (rank > 0 ? "#" + rank : cfg.getString("gui.unranked", "Unranked")),
                color + "| &f" + label + ": " + color + value
        );
    }

    private ItemStack headItem(UUID uuid, String name, List<String> loreLines) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Text.c(cfg.getString("gui.stats-head-name", "&x&F&F&0&0&6&7%name%")
                .replace("%name%", name)));
        meta.lore(loreLines.stream().map(Text::c).toList());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack statItem(String key, Material mat, long value) {
        String color = cfg.getString("gui.colors." + key, "&f");
        String name = cfg.getString("gui.stats-items." + key + ".name", "&f" + key);
        List<String> lore = cfg.getStringList("gui.stats-items." + key + ".lore");
        if (lore.isEmpty()) {
            lore = List.of("&8Statistics", "", color + "Information:",
                    color + "| &f" + cfg.getString("gui.labels." + key, key) + ": " + color + value);
        } else {
            lore = lore.stream().map(l -> l.replace("%value%", String.valueOf(value))).toList();
        }
        return new ItemBuilder(mat).name(name).lore(lore).build();
    }

    private ItemStack teamItem(String team) {
        String color = cfg.getString("gui.colors.team", "&d");
        return new ItemBuilder(Material.PINK_BANNER)
                .name(color + "&lTeam")
                .lore(List.of("&8Statistics", "", color + "Information:", color + "| &fTeam: " + color + team))
                .build();
    }

    private void putHubItem(Inventory inv, String type, Material mat, int slot) {
        String color = cfg.getString("gui.colors." + type, "&f");
        inv.setItem(slot, new ItemBuilder(mat)
                .name(cfg.getString("gui.hub-items." + type + ".name", color + "&l" + type.toUpperCase()))
                .lore(cfg.getStringList("gui.hub-items." + type + ".lore").isEmpty()
                        ? defaultHubLore(type, color)
                        : cfg.getStringList("gui.hub-items." + type + ".lore"))
                .build());
    }

    private List<String> defaultHubLore(String type, String color) {
        return List.of("&8Leaderboard", "", color + "Information:",
                color + "| &fView the " + type + " leaderboard.",
                "", CLICK + "To View");
    }

    private ItemStack navItem(Material mat, String name, int targetPage) {
        return new ItemBuilder(mat).name(name).lore(List.of("", CLICK + "To View")).build();
    }

    private ItemStack backItem() {
        return new ItemBuilder(Material.BARRIER)
                .name(cfg.getString("gui.back-name", "&cBack"))
                .lore(List.of("", CLICK + "To Go Back"))
                .build();
    }

    private ItemStack closeItem() {
        return new ItemBuilder(Material.BARRIER)
                .name(cfg.getString("gui.close-name", "&cClose"))
                .lore(List.of("", CLICK + "To Close"))
                .build();
    }

    private List<String> lore(YamlConfiguration cfg, String path, LeaderboardService.StatsSnapshot stats) {
        List<String> lines = cfg.getStringList(path);
        if (lines.isEmpty()) {
            lines = List.of(color("stats") + "| &fRank: " + stats.prefix());
        }
        return lines.stream()
                .map(l -> l.replace("%name%", stats.name())
                        .replace("%prefix%", stats.prefix())
                        .replace("%kills%", String.valueOf(stats.kills()))
                        .replace("%deaths%", String.valueOf(stats.deaths()))
                        .replace("%tokens%", String.valueOf(stats.tokens()))
                        .replace("%streak%", String.valueOf(stats.bestStreak()))
                        .replace("%team%", stats.team())
                        .replace("%playtime%", service.formatValue("playtime", stats.playMinutes())))
                .toList();
    }

    private String color(String type) {
        return cfg.getString("gui.colors." + type, "&f");
    }

    private static void fill(Inventory inv, int size) {
        ItemStack pane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }
}
