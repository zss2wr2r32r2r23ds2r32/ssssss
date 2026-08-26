package com.sharded.core.modules.shop;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.economy.*;
import com.sharded.core.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import java.util.Locale;

public final class ShopModule extends Module implements CommandExecutor {
    private ShopCatalog catalog; private ShopDatabase database; private ShopGuiHandler guiHandler; private long lastClick;
    public ShopModule(ShardedCore plugin) { super(plugin, "shop"); }
    ShopCatalog catalog() { return catalog; }
    EconomyService economy() { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? null : m.service(); }
    String formatMoney(long a) { EconomyModule m = plugin.modules().get(EconomyModule.class); return m == null ? Numbers.format(a) : m.formatBalance(a); }

    @Override protected void onEnable() {
        try { database = new ShopDatabase(plugin, moduleFolder()); } catch (Exception e) { throw new IllegalStateException("Could not open shop database", e); }
        reloadResources(); guiHandler = new ShopGuiHandler(this); registerCommand("shop", this);
    }
    @Override protected void onDisable() { if (database != null) database.close(); database = null; catalog = null; guiHandler = null; }
    void reloadResources() {
        syncJarResource("config.yml"); syncJarResource("messages.yml"); syncJarResource("buyingmenu.yml");
        for (String s : new String[]{"blockshop","farmshop","gearshop","mobdrops","redstoneshop","spawnershop","premiumshop"}) syncJarResource(s+"/config.yml");
        loadConfigs(); catalog = new ShopCatalog(moduleFolder());
    }

    void purchase(Player player, String sectionId, ShopCatalog.ShopItem shopItem, int amount) {
        long now = System.currentTimeMillis();
        if (!player.hasPermission("sharded.shop.fastbuy") && now-lastClick < catalog.mainConfig().getLong("fast-buy-millis",200)) return;
        lastClick = now;
        EconomyService eco = economy();
        if (eco == null) { send(player,"no-economy"); playConfiguredSound(player,"error"); return; }
        long total = shopItem.price()*amount;
        if (eco.getBalance(player.getUniqueId()) < total) { send(player,"cannot-afford","%total%",formatMoney(total)); playConfiguredSound(player,"error"); return; }
        ItemStack stack = new ItemStack(shopItem.material(), amount);
        if (!player.getInventory().addItem(stack).isEmpty()) { send(player,"no-space"); playConfiguredSound(player,"error"); return; }
        if (!eco.take(player.getUniqueId(), total)) { player.getInventory().removeItem(stack); send(player,"cannot-afford","%total%",formatMoney(total)); playConfiguredSound(player,"error"); return; }
        database.record(player.getUniqueId(), sectionId, shopItem.key(), amount, total);
        send(player,"bought","%amount%",String.valueOf(amount),"%item%",ItemStackUtil.formatMaterial(shopItem.material()),"%total%",formatMoney(total));
        playConfiguredSound(player,"buy"); player.closeInventory(); guiHandler.openSection(player, sectionId, 0);
    }

    void playConfiguredSound(Player player, String key) {
        if (!catalog.mainConfig().getBoolean("sounds."+key+".enabled", true)) return;
        try { player.playSound(player.getLocation(), Sound.valueOf(catalog.mainConfig().getString("sounds."+key+".sound","UI_BUTTON_CLICK").toUpperCase(Locale.ROOT).replace('.','_')),
                (float)catalog.mainConfig().getDouble("sounds."+key+".volume",1),(float)catalog.mainConfig().getDouble("sounds."+key+".pitch",1)); } catch (IllegalArgumentException ignored) {}
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ShopGuiHandler.ShopGuiHolder holder = TrackedInventories.lookup(event.getView().getTopInventory(), ShopGuiHandler.ShopGuiHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        ShopGuiHandler.ShopGuiHolder holder = TrackedInventories.untrack(event.getInventory(), ShopGuiHandler.ShopGuiHolder.class);
        if (holder != null && event.getPlayer() instanceof Player player) playConfiguredSound(player,"close");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
        if (!player.hasPermission("sharded.shop.use")) { send(sender,"no-permission"); return true; }
        guiHandler.openMain(player); return true;
    }
}
