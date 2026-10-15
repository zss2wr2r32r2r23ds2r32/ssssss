package com.sharded.core.modules.rules;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class RulesModule extends SetupFeatureModule implements CommandExecutor, Listener {

    public RulesModule(ShardedCore plugin) {
        super(plugin, "rules");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        ensureRulesContent();
        this.registerCommand("rules", this);
    }

    @Override
    public void reload() {
        super.reload();
        ensureRulesContent();
    }

    private void ensureRulesContent() {
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (rules != null && !rules.getKeys(false).isEmpty()) {
            return;
        }
        java.io.InputStream stream = plugin.getResource("modules/rules/config.yml");
        if (stream == null) {
            return;
        }
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection bundledRules = bundled.getConfigurationSection("rules");
        if (bundledRules == null || bundledRules.getKeys(false).isEmpty()) {
            return;
        }
        config.set("rules", null);
        for (String key : bundledRules.getKeys(false)) {
            config.set("rules." + key, bundled.get("rules." + key));
        }
        saveConfig();
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
        Inventory inventory = GuiHelper.create("rules", config.getString("gui-title", "&8Server Rules"),
                config.getInt("rows", 3));
        GuiHelper.fillGlass(inventory);
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (rules != null) {
            for (String key : rules.getKeys(false)) {
                String path = "rules." + key + ".";
                String preferred = config.getString(path + "material");
                String armorColor = config.getString(path + "armor-color", "#FFFFFF");
                Material harness = MaterialUtil.resolve(preferred);
                org.bukkit.inventory.ItemStack stack;
                if (preferred != null && preferred.toUpperCase().contains("HARNESS")
                        && (harness == null || !harness.name().endsWith("_HARNESS"))) {
                    stack = ItemBuilder.dyedHorseArmor(armorColor)
                            .name(config.getString(path + "name", key))
                            .lore(config.getStringList(path + "lore"))
                            .hideExtras()
                            .build();
                } else {
                    Material material = MaterialUtil.resolveWithFallbacks(preferred,
                            config.getString(path + "fallback"),
                            config.getString(path + "fallback-2"));
                    if (material == null) {
                        material = Material.PAPER;
                    }
                    stack = ItemBuilder.of(material)
                            .name(config.getString(path + "name", key))
                            .lore(config.getStringList(path + "lore"))
                            .hideExtras()
                            .build();
                }
                inventory.setItem(config.getInt(path + "slot", 13), stack);
            }
        }
        inventory.setItem(22, GuiHelper.closeButton());
        player.openInventory(inventory);
        SoundUtil.play(player, config.getString("sound-open", "block.note_block.pling"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder && "rules".equals(holder.getId())) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                event.getWhoClicked().closeInventory();
            }
        }
    }
}
