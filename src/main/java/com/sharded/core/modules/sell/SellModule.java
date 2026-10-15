package com.sharded.core.modules.sell;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.economy.*;
import com.sharded.core.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.io.File;
import java.util.List;
import java.util.Locale;

public final class SellModule extends Module implements CommandExecutor, TabCompleter {
    private SellPrices prices;
    private SellGuiHandler guiHandler;
    private YamlConfiguration worthConfig, multiplierConfig;

    public SellModule(ShardedCore plugin) { super(plugin, "sell"); }
    org.bukkit.configuration.file.YamlConfiguration config() { return config; }
    SellPrices prices() { return prices; }
    YamlConfiguration worthConfig() { return worthConfig; }
    YamlConfiguration multiplierConfig() { return multiplierConfig; }
    EconomyService economy() { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? null : m.service(); }
    String formatMoney(long a) { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? Numbers.format(a) : m.formatBalance(a); }

    @Override protected void onEnable() {
        reloadResources(); guiHandler = new SellGuiHandler(this);
        registerCommand("sell", this); registerCommand("worth", this); registerCommand("sellmulti", this);
    }
    @Override protected void onDisable() { prices = null; guiHandler = null; }
    void reloadResources() {
        for (String f : List.of("config.yml","messages.yml","prices.yml","multiplier.yml","worth.yml")) syncJarResource(f);
        loadConfigs();
        File folder = moduleFolder();
        prices = new SellPrices(new File(folder,"prices.yml"), new File(folder,"multiplier.yml"));
        worthConfig = YamlConfiguration.loadConfiguration(new File(folder,"worth.yml"));
        multiplierConfig = YamlConfiguration.loadConfiguration(new File(folder,"multiplier.yml"));
    }

    void processSell(Player player, Inventory inventory) {
        EconomyService eco = economy();
        if (eco == null) { send(player,"no-economy"); return; }
        double mult = prices.multiplier(player); long total = 0; int confirm = config.getInt("gui.sell.confirm-slot", 49);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == confirm || slot >= 45) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            long unit = prices.price(stack.getType());
            if (unit <= 0) { send(player,"no-price"); return; }
            total += (long)(unit * stack.getAmount() * mult);
        }
        if (total <= 0) { send(player,"nothing-to-sell"); return; }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == confirm || slot >= 45) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir() && prices.price(stack.getType()) > 0) inventory.setItem(slot, null);
        }
        eco.add(player.getUniqueId(), total); send(player,"sold","%money%",formatMoney(total)); player.closeInventory();
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        SellGuiHandler.SellGuiHolder holder = TrackedInventories.lookup(event.getView().getTopInventory(), SellGuiHandler.SellGuiHolder.class);
        if (holder == null) return;
        if (holder.type == SellGuiHandler.MenuType.SELL) {
            int confirm = config.getInt("gui.sell.confirm-slot", 49);
            if (event.getRawSlot() == confirm) { event.setCancelled(true); guiHandler.handleClick(player, holder, event.getSlot()); return; }
            if (event.getRawSlot() >= 0 && event.getRawSlot() < 45) return;
            if (event.getClickedInventory() == event.getView().getTopInventory()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        SellGuiHandler.SellGuiHolder holder = TrackedInventories.untrack(event.getInventory(), SellGuiHandler.SellGuiHolder.class);
        if (holder == null || holder.type != SellGuiHandler.MenuType.SELL) return;
        int confirm = config.getInt("gui.sell.confirm-slot", 49);
        for (int slot = 0; slot < event.getInventory().getSize(); slot++) {
            if (slot == confirm || slot >= 45) continue;
            ItemStack stack = event.getInventory().getItem(slot);
            if (stack != null && !stack.getType().isAir()) { player.getInventory().addItem(stack).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i)); event.getInventory().setItem(slot, null); }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
        if (!player.hasPermission("sharded.sell.use")) { send(player,"no-permission"); return true; }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sell" -> { guiHandler.openSell(player); yield true; }
            case "worth" -> { guiHandler.openWorth(player, 0); yield true; }
            case "sellmulti" -> { guiHandler.openMultiplier(player); send(player,"multiplier-current","%multiplier%",String.valueOf(prices.multiplier(player))); yield true; }
            default -> false;
        };
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return List.of(); }
}
