package dev.shardedsmp.listener;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.game.EnchantManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class EnchantListener implements Listener {
    private final ShardedSMP plugin;

    public EnchantListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        int protCap = EnchantManager.protectionCap(plugin.game().phase());
        int sharpCap = EnchantManager.sharpnessCap(plugin.game().phase());
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        if (toAdd.getOrDefault(Enchantment.PROTECTION, 0) > protCap) {
            toAdd.put(Enchantment.PROTECTION, protCap);
        }
        if (toAdd.getOrDefault(Enchantment.SHARPNESS, 0) > sharpCap) {
            toAdd.put(Enchantment.SHARPNESS, sharpCap);
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (cap(result)) {
            event.setResult(result);
        }
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (cap(result)) {
            event.setResult(result);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(PrepareItemCraftEvent event) {
        cap(event.getInventory().getResult());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareResult(PrepareResultEvent event) {
        cap(event.getResult());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            cap(item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        cap(event.getItem().getItemStack());
    }

    public boolean cap(ItemStack item) {
        return EnchantManager.capItem(item, plugin.game().phase());
    }
}
