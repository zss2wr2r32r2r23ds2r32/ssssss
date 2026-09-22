package com.sharded.core.modules.guide;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuideModule extends SetupFeatureModule implements CommandExecutor, Listener {

    private final Map<Integer, List<String>> slotActions = new HashMap<>();

    public GuideModule(ShardedCore plugin) {
        super(plugin, "guide");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        loadSlots();
        this.registerCommand("guide", this);
    }

    @Override
    public void reload() {
        super.reload();
        loadSlots();
    }

    private void loadSlots() {
        slotActions.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int slot = config.getInt("items." + key + ".slot");
            List<String> actions = new ArrayList<>();
            if (config.isList("items." + key + ".click_commands")) {
                actions.addAll(config.getStringList("items." + key + ".click_commands"));
            } else if (config.isList("items." + key + ".left_click_commands")) {
                actions.addAll(config.getStringList("items." + key + ".left_click_commands"));
            } else {
                String cmd = config.getString("items." + key + ".command", "");
                if (cmd != null && !cmd.isBlank()) {
                    actions.add("[player] " + cmd);
                }
            }
            slotActions.put(slot, actions);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        int size = config.getInt("size", config.getInt("rows", 4) * 9);
        int rows = Math.max(1, size / 9);
        Inventory inventory = GuiHelper.create("guide", config.getString("menu_title", config.getString("gui-title", "&8Guide")), rows);
        if (config.getBoolean("filler.auto-fill", true)) {
            Material fillerMat = MaterialUtil.resolve(config.getString("filler.material", "BLACK_STAINED_GLASS_PANE"));
            if (fillerMat == null) {
                fillerMat = Material.BLACK_STAINED_GLASS_PANE;
            }
            ItemStack filler = ItemBuilder.of(fillerMat).name(config.getString("filler.name", " ")).build();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler.clone());
            }
        }
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String path = "items." + key + ".";
                String preferred = config.getString(path + "material");
                String armorColor = config.getString(path + "armor-color");
                String name = config.getString(path + "display_name", config.getString(path + "name", key));
                List<String> lore = config.getStringList(path + "lore");
                ItemStack stack;
                Material harness = MaterialUtil.resolve(preferred);
                if (preferred != null && preferred.toUpperCase().contains("HARNESS")
                        && (harness == null || harness.name().endsWith("_DYE"))) {
                    stack = ItemBuilder.dyedHorseArmor(armorColor != null ? armorColor : "#FF0067")
                            .name(name).lore(lore).hideExtras().build();
                } else {
                    Material material = MaterialUtil.resolveWithFallbacks(preferred,
                            config.getString(path + "fallback"),
                            config.getString(path + "fallback-2"));
                    if (material == null) {
                        material = Material.PAPER;
                    }
                    stack = ItemBuilder.of(material).name(name).lore(lore).hideExtras().build();
                }
                inventory.setItem(config.getInt(path + "slot"), stack);
            }
        }
        player.openInventory(inventory);
        for (String line : config.getStringList("open_commands")) {
            runAction(player, line);
        }
        if (config.contains("sound-open")) {
            SoundUtil.play(player, config.getString("sound-open"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder) || !"guide".equals(holder.getId())) {
            return;
        }
        event.setCancelled(true);
        List<String> actions = slotActions.get(event.getRawSlot());
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (String action : actions) {
            runAction(player, action);
        }
    }

    private void runAction(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String line = raw.trim();
        if (line.equalsIgnoreCase("[close]")) {
            player.closeInventory();
            return;
        }
        if (line.toLowerCase().startsWith("[sound]")) {
            SoundUtil.play(player, line.substring(7).trim());
            return;
        }
        String cmd = line;
        if (line.toLowerCase().startsWith("[player]")) {
            cmd = line.substring(8).trim();
        } else if (line.toLowerCase().startsWith("[console]")) {
            String console = line.substring(9).trim();
            if (console.startsWith("/")) {
                console = console.substring(1);
            }
            final String run = console;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
            return;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        final String run = cmd;
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(run));
    }
}
