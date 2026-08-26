package com.sharded.core.modules.coinflip;

import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class CoinflipGuiHandler {

    enum MenuType { MAIN, ANIMATION, STATS, HISTORY }

    static final class CoinflipGuiHolder implements InventoryHolder {
        final MenuType type;
        final long gameId;
        final int page;
        Inventory inventory;

        CoinflipGuiHolder(MenuType type, long gameId, int page) {
            this.type = type;
            this.gameId = gameId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CoinflipModule module;

    CoinflipGuiHandler(CoinflipModule module) {
        this.module = module;
    }

    void openMain(Player player) {
        CoinflipGuiHolder holder = new CoinflipGuiHolder(MenuType.MAIN, 0, 0);
        int size = module.config().getInt("gui.main.size", 54);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(module.guiRaw("main-title")));
        holder.inventory = inv;
        fill(inv, "gui.main.filler");

        List<CoinflipDatabase.OpenGame> games = module.database().listOpenGames();
        List<Integer> slots = module.config().getIntegerList("gui.main.game-slots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        for (int i = 0; i < Math.min(games.size(), slots.size()); i++) {
            CoinflipDatabase.OpenGame game = games.get(i);
            inv.setItem(slots.get(i), gameItem(game));
        }

        inv.setItem(module.config().getInt("gui.main.create-slot", 49),
                button(Material.GOLD_INGOT, module.guiRaw("create-name"), module.guiRawList("create-lore")));
        inv.setItem(module.config().getInt("gui.main.stats-slot", 48),
                button(Material.BOOK, module.guiRaw("stats-name"), module.guiRawList("stats-lore")));
        inv.setItem(module.config().getInt("gui.main.history-slot", 50),
                button(Material.PAPER, module.guiRaw("history-name"), module.guiRawList("history-lore")));

        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void openStats(Player player) {
        CoinflipGuiHolder holder = new CoinflipGuiHolder(MenuType.STATS, 0, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(module.guiRaw("stats-title")));
        holder.inventory = inv;
        fill(inv, "gui.stats.filler");

        CoinflipDatabase.Stats stats = module.database().stats(player.getUniqueId());
        inv.setItem(13, new ItemBuilder(Material.PLAYER_HEAD)
                .name(module.guiRaw("stats-head-name", "%player%", player.getName()))
                .lore(module.guiRawList("stats-head-lore",
                        "%wins%", String.valueOf(stats.wins()),
                        "%losses%", String.valueOf(stats.losses()),
                        "%won%", module.formatMoney(stats.won()),
                        "%lost%", module.formatMoney(stats.lost()),
                        "%profit%", module.formatMoney(stats.won() - stats.lost())))
                .build());

        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void openHistory(Player player, int page) {
        int pageSize = module.config().getInt("gui.history.page-size", 21);
        List<CoinflipDatabase.HistoryEntry> entries = module.database().history(player.getUniqueId(), 100);
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));

        CoinflipGuiHolder holder = new CoinflipGuiHolder(MenuType.HISTORY, 0, page);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.c(module.guiRaw("history-title", "%page%", String.valueOf(page + 1))));
        holder.inventory = inv;
        fill(inv, "gui.history.filler");

        List<Integer> slots = module.config().getIntegerList("gui.history.entry-slots");
        if (slots.isEmpty()) {
            slots = new ArrayList<>();
            for (int row = 1; row <= 3; row++) {
                for (int col = 1; col <= 7; col++) slots.add(row * 9 + col);
            }
        }
        int start = page * pageSize;
        for (int i = 0; i < slots.size() && start + i < entries.size(); i++) {
            inv.setItem(slots.get(i), historyItem(player.getUniqueId(), entries.get(start + i)));
        }
        if (page > 0) {
            inv.setItem(45, button(Material.ARROW, module.guiRaw("prev-name"), module.guiRawList("prev-lore")));
        }
        if (start + pageSize < entries.size()) {
            inv.setItem(53, button(Material.ARROW, module.guiRaw("next-name"), module.guiRawList("next-lore")));
        }

        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void openAnimation(Player challenger, CoinflipDatabase.OpenGame game) {
        CoinflipGuiHolder holder = new CoinflipGuiHolder(MenuType.ANIMATION, game.id(), 0);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(module.guiRaw("animation-title")));
        holder.inventory = inv;
        fill(inv, "gui.animation.filler");
        inv.setItem(11, HeadUtil.namedHead(OfflinePlayers.name(game.creator())));
        inv.setItem(13, new ItemBuilder(Material.GOLD_INGOT)
                .name(module.guiRaw("pot-name", "%amount%", module.formatMoney(game.amount())))
                .lore(module.guiRawList("pot-lore", "%amount%", module.formatMoney(game.amount())))
                .build());
        inv.setItem(15, HeadUtil.namedHead(challenger.getName()));
        TrackedInventories.track(inv, holder);
        challenger.openInventory(inv);
    }

    void handleClick(Player player, CoinflipGuiHolder holder, int slot) {
        switch (holder.type) {
            case MAIN -> handleMainClick(player, slot);
            case STATS -> player.closeInventory();
            case HISTORY -> handleHistoryClick(player, holder.page, slot);
            case ANIMATION -> {
            }
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == module.config().getInt("gui.main.create-slot", 49)) {
            player.closeInventory();
            module.promptCreate(player);
            return;
        }
        if (slot == module.config().getInt("gui.main.stats-slot", 48)) {
            openStats(player);
            return;
        }
        if (slot == module.config().getInt("gui.main.history-slot", 50)) {
            openHistory(player, 0);
            return;
        }
        List<Integer> slots = module.config().getIntegerList("gui.main.game-slots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        int index = slots.indexOf(slot);
        if (index < 0) return;
        List<CoinflipDatabase.OpenGame> games = module.database().listOpenGames();
        if (index >= games.size()) return;
        module.joinGame(player, games.get(index));
    }

    private void handleHistoryClick(Player player, int page, int slot) {
        if (slot == 45) {
            openHistory(player, page - 1);
            return;
        }
        if (slot == 53) {
            openHistory(player, page + 1);
        }
    }

    private ItemStack gameItem(CoinflipDatabase.OpenGame game) {
        ItemStack head = HeadUtil.namedHead(OfflinePlayers.name(game.creator()));
        return new ItemBuilder(head)
                .name(module.guiRaw("game-name", "%player%", OfflinePlayers.name(game.creator())))
                .lore(module.guiRawList("game-lore",
                        "%player%", OfflinePlayers.name(game.creator()),
                        "%amount%", module.formatMoney(game.amount())))
                .build();
    }

    private ItemStack historyItem(java.util.UUID viewer, CoinflipDatabase.HistoryEntry entry) {
        boolean won = viewer.equals(entry.winner());
        Material mat = won ? Material.LIME_DYE : Material.RED_DYE;
        return new ItemBuilder(mat)
                .name(module.guiRaw(won ? "history-win-name" : "history-loss-name"))
                .lore(module.guiRawList("history-entry-lore",
                        "%opponent%", OfflinePlayers.name(won ? entry.loser() : entry.winner()),
                        "%amount%", module.formatMoney(entry.amount()),
                        "%result%", won ? module.raw("result-win") : module.raw("result-loss")))
                .build();
    }

    private void fill(Inventory inv, String path) {
        Material filler = Material.matchMaterial(module.config().getString(path + ".material", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null) filler = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane = new ItemBuilder(filler).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(name).lore(lore).build();
    }
}
