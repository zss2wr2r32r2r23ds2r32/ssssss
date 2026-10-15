package com.shardedcore.modules.kits;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.*;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.*;

public final class KitsModule extends Module implements CommandExecutor, TabCompleter, Listener {
    public record KitDefinition(String name, String displayName, String iconMaterial, int slot, long cooldownSeconds, String permission, List<String> lore, Map<Integer, ItemStack> items, boolean custom) {}
    private KitsDatabase database; private KitsGuiHandler guiHandler; private YamlConfiguration kitsYaml; private final Map<String, KitDefinition> kitCache = new LinkedHashMap<>();
    public KitsModule(ShardedCore plugin) { super(plugin, "kits"); }
    org.bukkit.configuration.file.FileConfiguration moduleConfig() { return config; }
    Collection<KitDefinition> allKits() { return kitCache.values(); }
    KitDefinition kit(String name) { return kitCache.get(name.toLowerCase(Locale.ROOT)); }

    @Override
    public void enable() {
        try { database = new KitsDatabase(plugin, moduleFolder); } catch (Exception e) { throw new IllegalStateException("Could not open kits database", e); }
        reloadResources(); guiHandler = new KitsGuiHandler(this); registerListener(this);
        registerCommand("kits", this); registerCommand("kit", this);
    }
    @Override
    public void disable() { if (database != null) database.close(); database = null; kitCache.clear(); guiHandler = null; cleanup(); }
    void reloadResources() {
        syncResource("modules/kits/kits.yml", new File(moduleFolder, "kits.yml"));
        kitsYaml = YamlConfiguration.loadConfiguration(new File(moduleFolder,"kits.yml"));
        reloadKitCache();
    }
    private void reloadKitCache() {
        kitCache.clear();
        ConfigurationSection root = kitsYaml.getConfigurationSection("kits");
        if (root != null) for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name); if (s != null) kitCache.put(name.toLowerCase(Locale.ROOT), loadYamlKit(name, s));
        }
        for (String name : database.customKitNames()) {
            KitsDatabase.KitEntry e = database.getCustomKit(name); if (e == null) continue;
            kitCache.put(name, new KitDefinition(name, "&f"+name, e.iconMaterial(), 29, e.cooldownSeconds(), e.permission(), List.of("&7Custom kit"), KitsDatabase.deserializeContents(e.contents()), true));
        }
    }
    private KitDefinition loadYamlKit(String name, ConfigurationSection section) {
        Map<Integer, ItemStack> items = new HashMap<>();
        for (Object raw : section.getList("items", List.of())) if (raw instanceof Map<?,?> map) {
            Material mat = Material.matchMaterial(String.valueOf(map.get("material")).toUpperCase(Locale.ROOT));
            if (mat != null) items.put(Integer.parseInt(String.valueOf(map.get("slot"))), new ItemStack(mat, map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1));
        }
        return new KitDefinition(name.toLowerCase(Locale.ROOT), section.getString("display-name", name), section.getString("icon","CHEST"), section.getInt("slot",11),
                section.getLong("cooldown-seconds", config.getLong("default-cooldown-seconds",86400)), section.getString("permission","shardedcore.kits."+name), section.getStringList("lore"), items, false);
    }
    long remainingCooldown(Player player, String kitName, long cooldownSeconds) {
        long last = database.lastClaim(player.getUniqueId(), kitName); if (last <= 0) return 0;
        return Math.max(0, cooldownSeconds*1000L - (System.currentTimeMillis()-last));
    }
    void claimKit(Player player, String kitName) {
        KitDefinition kit = kit(kitName);
        if (kit == null) { send(player,"kit-not-found","%kit%",kitName); return; }
        if (!kit.permission().isBlank() && !player.hasPermission(kit.permission())) { send(player,"no-permission"); return; }
        if (remainingCooldown(player, kit.name(), kit.cooldownSeconds()) > 0) { send(player,"cooldown","%kit%",kit.displayName(),"%time%",Text.time(remainingCooldown(player,kit.name(),kit.cooldownSeconds())/1000)); return; }
        if (!giveKitItems(player, kit)) { send(player,"no-space"); return; }
        database.setClaim(player.getUniqueId(), kit.name(), System.currentTimeMillis()); send(player,"claimed","%kit%",kit.displayName()); player.closeInventory();
    }
    void giveKitTo(Player target, String kitName, CommandSender notifier) {
        KitDefinition kit = kit(kitName); if (kit == null) { send(notifier,"kit-not-found","%kit%",kitName); return; }
        giveKitItems(target, kit); send(target,"kit-received","%kit%",kit.displayName()); send(notifier,"kit-given","%kit%",kit.displayName(),"%player%",target.getName());
    }
    private boolean giveKitItems(Player player, KitDefinition kit) {
        for (ItemStack stack : kit.items().values()) if (stack != null && !stack.getType().isAir() && !player.getInventory().addItem(stack.clone()).isEmpty()) return false;
        return true;
    }
    void saveLayout(Player player, String kitName, Map<Integer, ItemStack> items) {
        if (!player.hasPermission("shardedcore.kits.admin")) { send(player,"no-permission"); return; }
        database.saveKit(kitName, "CHEST", config.getLong("default-cooldown-seconds",86400), "shardedcore.kits."+kitName, KitsDatabase.serializeContents(items));
        reloadKitCache(); send(player,"layout-saved","%kit%",kitName);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        KitsGuiHandler.KitsGuiHolder holder = TrackedInventories.lookup(event.getView().getTopInventory(), KitsGuiHandler.KitsGuiHolder.class);
        if (holder == null) return;
        if (holder.editing) { if (event.getRawSlot() >= holder.inventory.getSize()-9) { event.setCancelled(true); if (event.getSlot()==holder.inventory.getSize()-5) guiHandler.handleClick(player,holder,event.getSlot()); } return; }
        event.setCancelled(true); if (event.getClickedInventory()!=event.getView().getTopInventory()) return;
        guiHandler.handleClick(player, holder, event.getSlot());
    }
    @EventHandler public void onClose(InventoryCloseEvent event) { TrackedInventories.untrack(event.getInventory(), KitsGuiHandler.KitsGuiHolder.class); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kits")) {
            if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
            if (!player.hasPermission("shardedcore.command.kits")) { send(player,"no-permission"); return true; }
            guiHandler.openMain(player); return true;
        }
        if (args.length == 0) { send(sender,"create-usage"); return true; }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "layout" -> handleLayout(sender, args);
            default -> { if (sender instanceof Player p && kit(args[0]) != null) { guiHandler.openPreview(p, args[0]); yield true; } send(sender,"kit-not-found","%kit%",args[0]); yield true; }
        };
    }
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
        if (!player.hasPermission("shardedcore.kits.admin")) { send(player,"no-permission"); return true; }
        if (args.length < 2) { send(player,"create-usage"); return true; }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (kit(name) != null) { send(player,"already-exists","%kit%",name); return true; }
        guiHandler.openLayout(player, name); send(player,"kit-created","%kit%",name); return true;
    }
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.kits.admin")) { send(sender,"no-permission"); return true; }
        if (args.length < 2) { send(sender,"delete-usage"); return true; }
        if (!database.deleteKit(args[1])) { send(sender,"kit-not-found","%kit%",args[1]); return true; }
        reloadKitCache(); send(sender,"kit-deleted","%kit%",args[1]); return true;
    }
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.kits.admin")) { send(sender,"no-permission"); return true; }
        if (args.length < 3) { send(sender,"give-usage"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage(Text.c("&cPlayer not found.")); return true; }
        giveKitTo(target, args[2], sender); return true;
    }
    private boolean handleList(CommandSender sender) {
        send(sender,"kit-list-header");
        for (KitDefinition kit : allKits()) send(sender,"kit-list-line","%kit%",kit.displayName(),"%cooldown%",Text.time(kit.cooldownSeconds()));
        return true;
    }
    private boolean handleLayout(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
        if (!player.hasPermission("shardedcore.kits.admin")) { send(player,"no-permission"); return true; }
        if (args.length < 2) { send(player,"create-usage"); return true; }
        guiHandler.openLayout(player, args[1]); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("create","delete","give","list","layout"));
            allKits().forEach(k -> opts.add(k.name()));
            return TabCompleteHelper.filter(opts, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete")||args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("layout")))
            return TabCompleteHelper.filter(allKits().stream().map(KitDefinition::name).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return OfflinePlayers.onlinePlayers(args[1]);
        return List.of();
    }
}
