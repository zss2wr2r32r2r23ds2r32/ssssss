package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cosmetic hat wardrobe — equip ItemsAdder hats as helmets with enchants. */
public final class WardrobeModule extends Module implements CommandExecutor, TabCompleter {

    private static final String MENU_TITLE = "Wardrobe";
    private final Map<String, HatOption> hats = new LinkedHashMap<>();
    private final Map<UUID, PreviewState> previews = new HashMap<>();
    private WardrobeDatabase database;
    private NamespacedKey hatKey;

    private static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PreviewState(
            Location returnLocation,
            GameMode gameMode,
            ItemStack savedHelmet,
            float walkSpeed,
            float flySpeed,
            boolean allowFlight,
            boolean flying,
            int actionBarTaskId,
            UUID cameraId,
            HatOption hat
    ) {
    }

    public WardrobeModule(ShardedCore plugin) {
        super(plugin, "wardrobe");
    }

    @Override
    protected void onEnable() {
        hatKey = new NamespacedKey(plugin, "wardrobe_hat");
        try {
            database = new WardrobeDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open wardrobe database", e);
        }
        loadHats();
        registerCommand("wardrobe", this);
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : new ArrayList<>(previews.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) endPreview(player, false);
        }
        previews.clear();
        if (database != null) database.close();
        database = null;
    }

    private void loadHats() {
        hats.clear();
        var section = config.getConfigurationSection("hats");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var hat = section.getConfigurationSection(id);
            if (hat == null) continue;
            hats.put(id, new HatOption(
                    id,
                    hat.getInt("slot", 0),
                    hat.getString("permission", "sharded.wardrobe." + id),
                    hat.getString("itemsadder-id", "HATS:" + id.toUpperCase(Locale.ROOT)),
                    hat.getString("material", "HATS:" + id.toUpperCase(Locale.ROOT)),
                    hat.getString("display-name", id),
                    hat.getStringList("lore")
            ));
        }
    }

    public boolean isPreviewing(Player player) {
        return previews.containsKey(player.getUniqueId());
    }

    public boolean isWardrobeHat(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.STRING);
    }

    /** Called from token shop via [wardrobe_unlock] hat_id */
    public boolean unlock(Player player, String hatId) {
        HatOption hat = hats.get(hatId.toLowerCase(Locale.ROOT));
        if (hat == null) return false;
        if (database != null) database.unlock(player.getUniqueId(), hat.id());
        send(player, "unlocked", "%hat%", hat.displayName());
        return true;
    }

    public boolean owns(Player player, HatOption hat) {
        if (player.hasPermission(hat.permission())) return true;
        return database != null && database.isUnlocked(player.getUniqueId(), hat.id());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            if (!isPreviewing(player)) {
                send(player, "preview-not-active");
                return true;
            }
            endPreview(player, true);
            return true;
        }
        if (isPreviewing(player)) {
            send(player, "preview-active");
            return true;
        }
        if (!player.hasPermission("sharded.wardrobe.use")) {
            send(player, "no-permission");
            return true;
        }
        openMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player && isPreviewing(player)) {
            return List.of("leave").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 1) {
            return List.of("leave").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 3);
        Inventory inventory = plugin.getServer().createInventory(new MenuHolder(), rows * 9, Text.c(MENU_TITLE));
        Material fillerMat = Material.matchMaterial(config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(fillerMat).name(" ").hideAll().build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());

        Map<String, String> ph = equipPlaceholders(player);
        for (HatOption hat : hats.values()) {
            ItemStack stack = resolveDisplayItem(hat);
            inventory.setItem(hat.slot(), new ItemBuilder(stack)
                    .name(apply(hat.displayName(), ph))
                    .lore(apply(hat.lore(), ph))
                    .hideAll()
                    .build());
        }

        int removeSlot = config.getInt("remove.slot", 4);
        inventory.setItem(removeSlot, new ItemBuilder(Material.BARRIER)
                .name(config.getString("remove.display-name", "&c&lREMOVE HAT"))
                .lore(config.getStringList("remove.lore"))
                .hideAll()
                .build());

        player.openInventory(inventory);
    }

    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String yes = config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
        String no = config.getString("placeholders.owned-no", "&#FF2727&nNo");
        for (HatOption hat : hats.values()) {
            map.put("wardrobe_owned_" + hat.id(), owns(player, hat) ? yes : no);
        }
        String equipped = database == null ? "" : database.getEquipped(player.getUniqueId());
        map.put("equipped_hat", equipped == null || equipped.isBlank()
                ? config.getString("placeholders.none", "&7None") : equipped);
        return map;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == config.getInt("remove.slot", 4)) {
            player.closeInventory();
            unequip(player);
            send(player, "removed");
            return;
        }

        for (HatOption hat : hats.values()) {
            if (hat.slot() != event.getSlot()) continue;
            if (!owns(player, hat)) {
                player.closeInventory();
                send(player, "not-owned", "%hat%", hat.displayName());
                return;
            }
            player.closeInventory();
            if (event.getClick().isRightClick()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> startPreview(player, hat));
            } else {
                HatOption chosen = hat;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    equip(player, chosen);
                    send(player, "equipped", "%hat%", chosen.displayName());
                });
            }
            return;
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPreviewing(event.getPlayer()) || isWardrobeHat(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHatMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) return;
        if (isPreviewing(player)) {
            event.setCancelled(true);
            return;
        }
        if (isWardrobeHat(event.getCurrentItem()) || isWardrobeHat(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isWardrobeHat(hotbar)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 39) {
            if (isWardrobeHat(player.getInventory().getHelmet())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack clicked = event.getCurrentItem();
            if (isWardrobeHat(clicked)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHatDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) return;
        if (event.getWhoClicked() instanceof Player player && isPreviewing(player)) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (isWardrobeHat(stack)) {
                event.setCancelled(true);
                return;
            }
        }
        if (isWardrobeHat(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PreviewState state = previews.get(player.getUniqueId());
        if (state == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            event.setCancelled(true);
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewInteract(PlayerInteractEvent event) {
        if (isPreviewing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewSwap(PlayerSwapHandItemsEvent event) {
        if (isPreviewing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewFlight(PlayerToggleFlightEvent event) {
        if (isPreviewing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isPreviewing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreviewCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isPreviewing(player)) return;
        String msg = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (msg.equals("/wardrobe leave") || msg.startsWith("/wardrobe leave ")) return;
        event.setCancelled(true);
        send(player, "preview-active");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endPreview(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        endPreview(event.getEntity(), false);
        event.getDrops().removeIf(this::isWardrobeHat);
        PlayerInventory inv = event.getEntity().getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isWardrobeHat(inv.getItem(i))) inv.setItem(i, null);
        }
        if (isWardrobeHat(inv.getHelmet())) inv.setHelmet(null);
        ItemStack off = inv.getItemInOffHand();
        if (isWardrobeHat(off)) inv.setItemInOffHand(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleReequip(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleReequip(event.getPlayer());
    }

    private void startPreview(Player player, HatOption hat) {
        if (isPreviewing(player)) {
            send(player, "preview-active");
            return;
        }
        if (!isPreviewWorld(player.getWorld())) {
            send(player, "preview-spawn-only");
            return;
        }
        if (!owns(player, hat)) {
            send(player, "not-owned", "%hat%", hat.displayName());
            return;
        }

        Location previewLoc = previewLocation(player.getWorld());
        if (previewLoc == null) {
            send(player, "preview-world-missing");
            return;
        }

        ItemStack previewHat = buildHatItem(hat);
        if (previewHat == null) {
            send(player, "item-missing", "%hat%", hat.displayName());
            return;
        }

        Location returnLoc = player.getLocation().clone();
        GameMode gm = player.getGameMode();
        float walkSpeed = player.getWalkSpeed();
        float flySpeed = player.getFlySpeed();
        boolean allowFlight = player.getAllowFlight();
        boolean flying = player.isFlying();

        ItemStack savedHelmet = player.getInventory().getHelmet();
        if (savedHelmet != null) savedHelmet = savedHelmet.clone();

        player.getInventory().setHelmet(previewHat);
        player.teleport(previewLoc);

        double distance = config.getDouble("preview.camera-distance", 4.0);
        double height = config.getDouble("preview.camera-height", 1.65);
        Vector back = previewLoc.getDirection().clone().multiply(-distance);
        Location camLoc = previewLoc.clone().add(back).add(0, height, 0);

        ArmorStand camera = player.getWorld().spawn(camLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(false);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(false);
            stand.setPersistent(false);
        });
        Vector toPlayer = player.getEyeLocation().toVector().subtract(camera.getLocation().toVector());
        if (toPlayer.lengthSquared() > 0) {
            Location look = camera.getLocation().clone();
            look.setDirection(toPlayer);
            camera.teleport(look);
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(camera);

        String actionBar = config.getString("preview.actionbar", "&7Do &f/wardrobe leave &7to stop previewing");
        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !previews.containsKey(player.getUniqueId())) return;
            player.sendActionBar(Text.c(actionBar));
        }, 0L, 20L).getTaskId();

        previews.put(player.getUniqueId(), new PreviewState(
                returnLoc, gm, savedHelmet, walkSpeed, flySpeed, allowFlight, flying, taskId, camera.getUniqueId(), hat));
        send(player, "preview-started", "%hat%", hat.displayName());
    }

    private void endPreview(Player player, boolean notify) {
        PreviewState state = previews.remove(player.getUniqueId());
        if (state == null) return;

        plugin.getServer().getScheduler().cancelTask(state.actionBarTaskId);
        removeCamera(state.cameraId());

        player.setSpectatorTarget(null);
        player.setGameMode(state.gameMode());
        player.setWalkSpeed(state.walkSpeed());
        player.setFlySpeed(state.flySpeed());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.flying());

        player.getInventory().setHelmet(state.savedHelmet());
        player.teleport(state.returnLocation());

        if (notify) send(player, "preview-ended");
        scheduleReequip(player);
    }

    private void removeCamera(UUID cameraId) {
        if (cameraId == null) return;
        for (World world : plugin.getServer().getWorlds()) {
            Entity entity = world.getEntity(cameraId);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }

    private boolean isPreviewWorld(World world) {
        List<String> allowed = config.getStringList("preview.worlds");
        if (allowed.isEmpty()) {
            allowed = List.of("spawn", "world");
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        for (String entry : allowed) {
            if (name.equals(entry.toLowerCase(Locale.ROOT))) return true;
        }
        var spawnSelect = plugin.modules().get(com.sharded.core.modules.spawnselect.SpawnSelectModule.class);
        if (spawnSelect != null && spawnSelect.isEnabled()) {
            Location main = spawnSelect.mainSpawn();
            if (main != null && main.getWorld() != null
                    && name.equals(main.getWorld().getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Location previewLocation(World world) {
        double x = config.getDouble("preview.x", 34);
        double y = config.getDouble("preview.y", 80);
        double z = config.getDouble("preview.z", 60);
        float yaw = (float) config.getDouble("preview.yaw", 180);
        float pitch = (float) config.getDouble("preview.pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void scheduleReequip(Player player) {
        if (database == null) return;
        for (long delay : new long[] {1L, 10L, 40L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> reequipIfNeeded(player), delay);
        }
    }

    private void reequipIfNeeded(Player player) {
        if (!player.isOnline() || isPreviewing(player)) return;
        String equipped = database.getEquipped(player.getUniqueId());
        if (equipped == null || equipped.isBlank()) return;
        HatOption hat = hats.get(equipped);
        if (hat == null || !owns(player, hat)) return;
        ItemStack current = player.getInventory().getHelmet();
        if (isWardrobeHat(current)) return;
        equipSilent(player, hat);
    }

    public void equip(Player player, HatOption hat) {
        equipSilent(player, hat);
        if (database != null) database.setEquipped(player.getUniqueId(), hat.id());
    }

    private void equipSilent(Player player, HatOption hat) {
        ItemStack hatItem = buildHatItem(hat);
        if (hatItem == null) {
            send(player, "item-missing", "%hat%", hat.displayName());
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getHelmet();
        if (isWardrobeHat(current)) {
            inv.setHelmet(null);
        } else if (current != null && !current.getType().isAir()) {
            Map<Integer, ItemStack> leftover = inv.addItem(current.clone());
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        inv.setHelmet(hatItem);
    }

    private void unequip(Player player) {
        PlayerInventory inv = player.getInventory();
        if (isWardrobeHat(inv.getHelmet())) {
            inv.setHelmet(null);
        }
        removeWardrobeHatsFromInventory(player);
        if (database != null) database.setEquipped(player.getUniqueId(), "");
    }

    private void removeWardrobeHatsFromInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isWardrobeHat(inv.getItem(i))) inv.setItem(i, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (isWardrobeHat(off)) inv.setItemInOffHand(null);
    }

    private ItemStack resolveDisplayItem(HatOption hat) {
        ItemStack stack = ItemsAdderHook.resolve(hat.itemsadderId());
        if (stack == null) stack = ItemsAdderHook.resolve(hat.material());
        if (stack == null) stack = ItemsAdderHook.parseItem("itemsadder-" + hat.itemsadderId());
        if (stack != null) return stack.clone();
        Material mat = Material.matchMaterial(hat.material());
        if (mat == null) mat = Material.PAPER;
        return new ItemStack(mat);
    }

    private ItemStack buildHatItem(HatOption hat) {
        ItemStack stack = resolveDisplayItem(hat);
        if (stack == null || stack.getType().isAir()) return null;
        stack = stack.clone();
        int prot = config.getInt("enchantments.protection", 4);
        int unb = config.getInt("enchantments.unbreaking", 3);
        double armor = config.getDouble("attributes.armor", 3.0);
        double toughness = config.getDouble("attributes.armor-toughness", 0.0);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, prot, true);
            meta.addEnchant(Enchantment.UNBREAKING, unb, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setUnbreakable(false);
            NamespacedKey armorKey = new NamespacedKey(plugin, "wardrobe_armor_" + hat.id());
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                    armorKey, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            if (toughness > 0) {
                NamespacedKey toughKey = new NamespacedKey(plugin, "wardrobe_toughness_" + hat.id());
                meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                        toughKey, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(hatKey, PersistentDataType.STRING, hat.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> apply(List<String> lines, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(apply(line, ph));
        return out;
    }

    private String apply(String line, Map<String, String> ph) {
        String out = line;
        for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("%" + e.getKey() + "%", e.getValue());
        return out;
    }

    private record HatOption(String id, int slot, String permission, String itemsadderId,
                             String material, String displayName, List<String> lore) {
    }
}
