package com.sharded.core.modules.playtimerewards;

import com.sharded.core.util.EventRewards;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PlaytimeRewardsGuiHandler implements Listener {

    static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record RewardTier(String id, long requiredMinutes, int slot, ConfigurationSection section) {
    }

    private final PlaytimeRewardsModule module;

    PlaytimeRewardsGuiHandler(PlaytimeRewardsModule module) {
        this.module = module;
    }

    void open(Player player) {
        int size = Math.max(9, Math.min(54, module.config().getInt("gui.size", 54)));
        Holder holder = new Holder();
        String title = PlaceholderUtil.apply(player, module.config().getString("gui.title", "&8Playtime Rewards"));
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(title));
        holder.inventory = inv;
        fill(inv, size);

        long minutes = playtimeMinutes(player);
        for (RewardTier tier : tiers()) {
            if (tier.slot() < 0 || tier.slot() >= size) continue;
            inv.setItem(tier.slot(), tierItem(player, tier, minutes));
        }
        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class) == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        RewardTier tier = tiers().stream().filter(t -> t.slot() == slot).findFirst().orElse(null);
        if (tier == null) return;

        long minutes = playtimeMinutes(player);
        if (minutes < tier.requiredMinutes()) {
            module.send(player, "not-enough-playtime",
                    "%required%", Text.formatPlaytime(tier.requiredMinutes()),
                    "%playtime%", Text.formatPlaytime(minutes));
            return;
        }
        if (module.database().isClaimed(player.getUniqueId(), tier.id())) {
            module.send(player, "already-claimed");
            return;
        }
        module.database().markClaimed(player.getUniqueId(), tier.id());
        EventRewards.grant(module.plugin(), player.getUniqueId(), tier.section());
        module.send(player, "claimed", "%reward%", tier.section().getString("name", tier.id()));
        open(player);
    }

    private long playtimeMinutes(Player player) {
        return Text.ticksToMinutes(player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    private ItemStack tierItem(Player player, RewardTier tier, long minutes) {
        Material material = Material.matchMaterial(tier.section().getString("material", "CLOCK"));
        if (material == null) material = Material.CLOCK;

        boolean claimed = module.database().isClaimed(player.getUniqueId(), tier.id());
        boolean unlocked = minutes >= tier.requiredMinutes();
        String name = tier.section().getString("name", "&e" + Text.formatPlaytime(tier.requiredMinutes()));
        List<String> loreKey = claimed ? List.of("lore-claimed") : unlocked ? List.of("lore-unlocked", "lore") : List.of("lore-locked", "lore");
        List<String> lore = new ArrayList<>();
        for (String key : loreKey) {
            List<String> lines = tier.section().getStringList(key);
            if (lines.isEmpty() && tier.section().isString(key)) {
                lines = List.of(tier.section().getString(key));
            }
            for (String line : lines) {
                lore.add(replaceTierPlaceholders(player, line, tier, minutes, claimed, unlocked));
            }
        }
        if (lore.isEmpty()) {
            lore.add(replaceTierPlaceholders(player, "&7Requires &f%required%", tier, minutes, claimed, unlocked));
        }
        ItemBuilder builder = new ItemBuilder(material).name(name).lore(lore);
        if (unlocked && !claimed) builder.glow(true);
        return builder.build();
    }

    private String replaceTierPlaceholders(Player player, String line, RewardTier tier, long minutes, boolean claimed, boolean unlocked) {
        return PlaceholderUtil.apply(player, Text.apply(line,
                "%required%", Text.formatPlaytime(tier.requiredMinutes()),
                "%playtime%", Text.formatPlaytime(minutes),
                "%required_minutes%", String.valueOf(tier.requiredMinutes()),
                "%playtime_minutes%", String.valueOf(minutes),
                "%status%", claimed ? "Claimed" : unlocked ? "Ready" : "Locked"));
    }

    private List<RewardTier> tiers() {
        ConfigurationSection rewards = module.config().getConfigurationSection("rewards");
        if (rewards == null) return List.of();
        List<RewardTier> list = new ArrayList<>();
        for (String id : rewards.getKeys(false)) {
            ConfigurationSection section = rewards.getConfigurationSection(id);
            if (section == null) continue;
            long required = section.getLong("required-minutes", section.getLong("minutes", 0L));
            int slot = section.getInt("slot", -1);
            list.add(new RewardTier(id, required, slot, section));
        }
        list.sort(Comparator.comparingLong(RewardTier::requiredMinutes));
        return list;
    }

    private void fill(Inventory inv, int size) {
        Material fillerMat = Material.matchMaterial(module.config().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(fillerMat).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
    }
}
