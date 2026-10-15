package dev.shardedsmp.game;

import dev.shardedsmp.GamePhase;
import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.item.ObsidianItems;
import dev.shardedsmp.util.ColorUtil;
import dev.shardedsmp.util.Keys;
import dev.shardedsmp.util.LocationUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class GameManager {
    private final ShardedSMP plugin;
    private final File dataFile;
    private GamePhase phase = GamePhase.IDLE;
    private boolean graceActive;
    private long graceEndMillis;
    private int obsidianSpawned;
    private final Set<Integer> foundIds = new HashSet<>();
    private int diamondsMined;
    private boolean netherOpen;
    private boolean endOpen;
    private boolean witherSpawned;
    private boolean witherKilled;
    private boolean dragonKilled;
    private UUID eventWitherId;
    private Location netherPortalLocation;
    private Location endPortalLocation;
    private final Set<UUID> steakGiven = new HashSet<>();
    private final Set<String> placedDiamondOres = new HashSet<>();
    private int holderCycleIndex;
    private int holderCycleTicks;
    private String temporaryActionBar;
    private long temporaryActionBarUntil;

    public GameManager(ShardedSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public ShardedSMP plugin() {
        return plugin;
    }

    public GamePhase phase() {
        return phase;
    }

    public boolean graceActive() {
        return graceActive && System.currentTimeMillis() < graceEndMillis;
    }

    public boolean graceStarted() {
        return graceEndMillis > 0 || graceActive;
    }

    public boolean hasReceivedSteak(Player player) {
        return steakGiven.contains(player.getUniqueId());
    }

    public long graceRemainingMillis() {
        return Math.max(0L, graceEndMillis - System.currentTimeMillis());
    }

    public int obsidianSpawned() {
        return obsidianSpawned;
    }

    public int obsidianFound() {
        return foundIds.size();
    }

    public int obsidianTotal() {
        return plugin.getConfig().getInt("obsidian.total", 10);
    }

    public int diamondsMined() {
        return diamondsMined;
    }

    public int diamondsNeeded() {
        return plugin.getConfig().getInt("quest.diamonds", 500);
    }

    public boolean netherOpen() {
        return netherOpen;
    }

    public boolean endOpen() {
        return endOpen;
    }

    public boolean witherKilled() {
        return witherKilled;
    }

    public boolean dragonKilled() {
        return dragonKilled;
    }

    public World overworld() {
        String name = plugin.getConfig().getString("world", "world");
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL) {
                return candidate;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    public World netherWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NETHER) {
                return world;
            }
        }
        return null;
    }

    public World endWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                return world;
            }
        }
        return null;
    }

    public double borderSize() {
        return plugin.getConfig().getDouble("border-size", 1500);
    }

    public double borderPadding() {
        return plugin.getConfig().getDouble("border-padding", 16);
    }

    public void applyWorldBorder() {
        World world = overworld();
        if (world == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(world.getSpawnLocation());
        border.setSize(borderSize());
        border.setDamageAmount(0.2);
        border.setDamageBuffer(2);
        border.setWarningDistance(8);
    }

    public void load() {
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        phase = GamePhase.valueOf(data.getString("phase", "IDLE"));
        graceActive = data.getBoolean("grace-active");
        graceEndMillis = data.getLong("grace-end-millis");
        obsidianSpawned = data.getInt("obsidian-spawned");
        foundIds.clear();
        foundIds.addAll(data.getIntegerList("found-ids"));
        diamondsMined = data.getInt("diamonds-mined");
        netherOpen = data.getBoolean("nether-open");
        endOpen = data.getBoolean("end-open");
        witherSpawned = data.getBoolean("wither-spawned");
        witherKilled = data.getBoolean("wither-killed");
        dragonKilled = data.getBoolean("dragon-killed");
        String wither = data.getString("event-wither");
        eventWitherId = wither == null || wither.isEmpty() ? null : UUID.fromString(wither);
        netherPortalLocation = readLocation(data, "nether-portal");
        endPortalLocation = readLocation(data, "end-portal");
        steakGiven.clear();
        for (String id : data.getStringList("steak-given")) {
            steakGiven.add(UUID.fromString(id));
        }
        placedDiamondOres.clear();
        placedDiamondOres.addAll(data.getStringList("placed-diamond-ores"));
        if (graceStarted()) {
            applyWorldBorder();
        }
        if (graceActive && System.currentTimeMillis() >= graceEndMillis) {
            endGrace(false);
        }
    }

    public void save() {
        FileConfiguration data = new YamlConfiguration();
        data.set("phase", phase.name());
        data.set("grace-active", graceActive);
        data.set("grace-end-millis", graceEndMillis);
        data.set("obsidian-spawned", obsidianSpawned);
        data.set("found-ids", new ArrayList<>(foundIds));
        data.set("diamonds-mined", diamondsMined);
        data.set("nether-open", netherOpen);
        data.set("end-open", endOpen);
        data.set("wither-spawned", witherSpawned);
        data.set("wither-killed", witherKilled);
        data.set("dragon-killed", dragonKilled);
        data.set("event-wither", eventWitherId == null ? "" : eventWitherId.toString());
        writeLocation(data, "nether-portal", netherPortalLocation);
        writeLocation(data, "end-portal", endPortalLocation);
        List<String> steak = new ArrayList<>();
        for (UUID id : steakGiven) {
            steak.add(id.toString());
        }
        data.set("steak-given", steak);
        data.set("placed-diamond-ores", new ArrayList<>(placedDiamondOres));
        try {
            plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save data.yml: " + exception.getMessage());
        }
    }

    public boolean startGrace() {
        if (graceStarted() && phase != GamePhase.IDLE) {
            return false;
        }
        applyWorldBorder();
        int minutes = plugin.getConfig().getInt("grace-minutes", 30);
        graceActive = true;
        graceEndMillis = System.currentTimeMillis() + minutes * 60_000L;
        steakGiven.clear();
        setPhase(GamePhase.PHASE_1);
        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayerForGrace(player);
        }
        save();
        plugin.obsidianManager().scheduleAfterGrace();
        plugin.getLogger().info("Grace started for " + minutes + " minutes.");
        return true;
    }

    public void setupPlayerForGrace(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        World world = overworld();
        if (world != null) {
            Location destination = LocationUtil.randomSafeLocation(world, borderPadding(), 50);
            player.teleport(destination);
        }
        giveSteak(player);
    }

    public void giveSteak(Player player) {
        if (steakGiven.contains(player.getUniqueId())) {
            return;
        }
        ensureSteak(player);
        steakGiven.add(player.getUniqueId());
        save();
    }

    public void ensureSteak(Player player) {
        int amount = plugin.getConfig().getInt("grace-steak-amount", 32);
        int owned = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COOKED_BEEF) {
                owned += item.getAmount();
            }
        }
        if (owned < amount) {
            player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, amount - owned));
        }
    }

    public void endGrace(boolean announce) {
        if (!graceActive && System.currentTimeMillis() < graceEndMillis) {
            return;
        }
        graceActive = false;
        if (announce) {
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(ColorUtil.color("&fGrace has ended. &cPvP is now enabled.")));
        }
        save();
        plugin.obsidianManager().scheduleAfterGrace();
    }

    public void setPhase(GamePhase next) {
        if (phase == next) {
            return;
        }
        phase = next;
        if (next.number() > 0) {
            showPhaseTitle(next.number());
        }
        if (plugin.questManager() != null) {
            plugin.questManager().updateBossBar();
        }
        save();
    }

    public void showPhaseTitle(int number) {
        String title = plugin.getConfig().getString("messages.phase-title", "&#FF0000&lPHASE %phase%")
                .replace("%phase%", String.valueOf(number));
        String subtitle = plugin.getConfig().getString("messages.phase-subtitle", "&fHas begun");
        Title adventure = Title.title(
                ColorUtil.color(title),
                ColorUtil.color(subtitle),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(800))
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(adventure);
        }
    }

    public void markObsidianSpawned() {
        obsidianSpawned++;
        save();
    }

    public boolean markObsidianFound(int pieceId) {
        if (pieceId < 0 || pieceId >= 1000 || foundIds.contains(pieceId)) {
            return false;
        }
        foundIds.add(pieceId);
        if (graceStarted()) {
            int neededForTwo = plugin.getConfig().getInt("obsidian.phase-two-found", 5);
            if (phase == GamePhase.PHASE_1 && foundIds.size() >= neededForTwo) {
                setPhase(GamePhase.PHASE_2);
            }
            if (foundIds.size() >= obsidianTotal() && !netherOpen) {
                openNether();
            }
        }
        save();
        return true;
    }

    public boolean canStartObsidianSpawns() {
        if (!graceStarted() || graceActive()) {
            return false;
        }
        if (obsidianSpawned >= obsidianTotal()) {
            return false;
        }
        return Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("obsidian.required-players", 5);
    }

    public void openNether() {
        if (netherOpen) {
            return;
        }
        netherOpen = true;
        setPhase(GamePhase.PHASE_3);
        netherPortalLocation = buildNetherPortal();
        if (netherPortalLocation != null) {
            showTemporaryActionBar(formatLocationMessage("messages.nether-portal", netherPortalLocation), 12);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(
                    ColorUtil.color(formatLocationMessage("messages.nether-portal", netherPortalLocation))));
        }
        spawnEventWither();
        save();
    }

    public void openEnd() {
        if (endOpen) {
            return;
        }
        endOpen = true;
        setPhase(GamePhase.PHASE_4);
        endPortalLocation = buildEndPortal();
        if (endPortalLocation != null) {
            showTemporaryActionBar(formatLocationMessage("messages.end-portal", endPortalLocation), 15);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(
                    ColorUtil.color(formatLocationMessage("messages.end-portal", endPortalLocation))));
        }
        save();
    }

    public boolean addDiamond() {
        if (phase.number() < 3 || endOpen) {
            return false;
        }
        diamondsMined++;
        plugin.questManager().updateBossBar();
        if (diamondsMined >= diamondsNeeded()) {
            openEnd();
        }
        save();
        return true;
    }

    public void markPlacedDiamondOre(Location location) {
        placedDiamondOres.add(blockKey(location));
        save();
    }

    public boolean isPlayerPlacedDiamondOre(Location location) {
        return placedDiamondOres.contains(blockKey(location));
    }

    public void removePlacedDiamondOre(Location location) {
        if (placedDiamondOres.remove(blockKey(location))) {
            save();
        }
    }

    public boolean isEventWither(UUID uuid) {
        return eventWitherId != null && eventWitherId.equals(uuid);
    }

    public void onEventWitherKilled() {
        witherKilled = true;
        eventWitherId = null;
        save();
    }

    public void onDragonKilled() {
        if (dragonKilled) {
            return;
        }
        dragonKilled = true;
        setPhase(GamePhase.PHASE_5);
        save();
    }

    public void showTemporaryActionBar(String message, int seconds) {
        this.temporaryActionBar = message;
        this.temporaryActionBarUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public String currentActionBar() {
        if (temporaryActionBar != null && System.currentTimeMillis() < temporaryActionBarUntil) {
            return temporaryActionBar;
        }
        if (graceActive()) {
            return plugin.getConfig().getString("messages.grace-actionbar", "&fGrace ends in &#FF0000%time%")
                    .replace("%time%", formatTime(graceRemainingMillis()));
        }
        if (obsidianSpawned >= obsidianTotal()) {
            return holderActionBar();
        }
        if (phase == GamePhase.PHASE_3 && !endOpen) {
            return plugin.getConfig().getString("messages.diamonds-progress", "&bDiamonds &f%current%&7/&f%needed%")
                    .replace("%current%", String.valueOf(diamondsMined))
                    .replace("%needed%", String.valueOf(diamondsNeeded()));
        }
        return null;
    }

    private String holderActionBar() {
        List<Holder> holders = currentHolders();
        if (holders.isEmpty()) {
            return plugin.getConfig().getString("messages.ground-actionbar", "&fA Piece of &#FF0000Obsidian &fis still on the ground");
        }
        holderCycleTicks++;
        if (holderCycleTicks >= 3) {
            holderCycleTicks = 0;
            holderCycleIndex = (holderCycleIndex + 1) % holders.size();
        }
        holderCycleIndex = holderCycleIndex % holders.size();
        Holder holder = holders.get(holderCycleIndex);
        return plugin.getConfig().getString("messages.holder-actionbar", "&fObsidian Holder &#FF0000%player% &7x%count%")
                .replace("%player%", holder.name)
                .replace("%count%", String.valueOf(holder.count));
    }

    public void tickActionBars() {
        if (graceActive && System.currentTimeMillis() >= graceEndMillis) {
            endGrace(true);
        }
        String message = currentActionBar();
        if (message == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(ColorUtil.color(message));
        }
    }

    public void tickGlowAndHearts() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasPiece = hasSpecialObsidian(player);
            plugin.glowManager().setPlayerGlowing(player, hasPiece);
            updateDragonEggHearts(player);
        }
    }

    public boolean hasSpecialObsidian(Player player) {
        if (ObsidianItems.isSpecial(player.getItemOnCursor())) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (ObsidianItems.isSpecial(item)) {
                return true;
            }
        }
        return false;
    }

    public int countSpecialObsidian(Player player) {
        int count = 0;
        if (ObsidianItems.isSpecial(player.getItemOnCursor())) {
            count++;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (ObsidianItems.isSpecial(item)) {
                count++;
            }
        }
        return count;
    }

    public void updateDragonEggHearts(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        boolean hasEgg = hasDragonEgg(player);
        AttributeModifier existing = health.getModifier(Keys.eggHearts);
        if (hasEgg && existing == null) {
            health.addModifier(new AttributeModifier(Keys.eggHearts, 4.0, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!hasEgg && existing != null) {
            health.removeModifier(Keys.eggHearts);
            if (player.getHealth() > health.getValue()) {
                player.setHealth(health.getValue());
            }
        }
    }

    public boolean hasDragonEgg(Player player) {
        if (isDragonEgg(player.getItemOnCursor())) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDragonEgg(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDragonEgg(ItemStack item) {
        return item != null && item.getType() == Material.DRAGON_EGG;
    }

    public Location spawnObsidianItem(Location location, int pieceId, boolean countTowardTotal) {
        World world = location.getWorld();
        ItemStack stack = ObsidianItems.createPiece(pieceId);
        Item dropped = world.dropItem(location, stack);
        dropped.setUnlimitedLifetime(true);
        dropped.setPersistent(true);
        dropped.setInvulnerable(true);
        dropped.setCanMobPickup(false);
        dropped.setPickupDelay(40);
        dropped.setUnlimitedLifetime(true);
        dropped.setGravity(true);
        plugin.glowManager().glowEntity(dropped);
        if (countTowardTotal) {
            markObsidianSpawned();
        }
        String message = formatLocationMessage("messages.obsidian-spawn", location);
        showTemporaryActionBar(message, 10);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(ColorUtil.color(message)));
        return dropped.getLocation();
    }

    public int nextPieceId() {
        return obsidianSpawned + 1;
    }

    public void ensureEventWither() {
        if (!netherOpen || witherKilled || !witherSpawned) {
            return;
        }
        if (eventWitherId != null && Bukkit.getEntity(eventWitherId) instanceof Wither) {
            return;
        }
        spawnEventWither();
    }

    public void spawnEventWither() {
        World nether = netherWorld();
        if (nether == null) {
            plugin.getLogger().warning("Nether world is not loaded; event wither was not spawned.");
            return;
        }
        Location spawn = nether.getSpawnLocation().clone();
        spawn.setY(Math.max(40, Math.min(100, spawn.getY())));
        buildBedrockArena(spawn);
        Location witherLoc = spawn.clone().add(0.5, 2, 0.5);
        nether.getChunkAt(witherLoc).addPluginChunkTicket(plugin);
        if (eventWitherId != null) {
            var existing = Bukkit.getEntity(eventWitherId);
            if (existing != null) {
                existing.remove();
            }
        }
        Wither wither = nether.spawn(witherLoc, Wither.class, spawned -> {
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setAI(false);
            spawned.customName(ColorUtil.color("&#FF0000&lNether Wither"));
            spawned.setCustomNameVisible(true);
            spawned.getPersistentDataContainer().set(Keys.eventWither, PersistentDataType.BOOLEAN, true);
        });
        eventWitherId = wither.getUniqueId();
        witherSpawned = true;
        save();
    }

    private void buildBedrockArena(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.BEDROCK);
                world.getBlockAt(cx + x, cy + 8, cz + z).setType(Material.BEDROCK);
                for (int y = 1; y <= 7; y++) {
                    boolean wall = x == -6 || x == 6 || z == -6 || z == 6;
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (wall) {
                        if (y >= 2 && y <= 5 && (Math.abs(x) == 6 && Math.abs(z) <= 1 || Math.abs(z) == 6 && Math.abs(x) <= 1)) {
                            block.setType(Material.IRON_BARS);
                        } else {
                            block.setType(Material.BEDROCK);
                        }
                    } else {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private Location buildNetherPortal() {
        World world = overworld();
        if (world == null) {
            return null;
        }
        Location base = LocationUtil.randomSafeLocation(world, borderPadding() + 8, 60);
        int x = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (frame) {
                    block.setType(Material.OBSIDIAN);
                } else {
                    block.setType(Material.AIR);
                }
            }
        }
        world.getBlockAt(x + 1, y + 1, z).setType(Material.FIRE);
        Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
        portal.setAxis(org.bukkit.Axis.X);
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(x + dx, y + dy, z).setBlockData(portal);
            }
        }
        return new Location(world, x + 1.5, y + 1, z + 0.5);
    }

    private Location buildEndPortal() {
        World world = overworld();
        if (world == null) {
            return null;
        }
        Location base = LocationUtil.randomSafeLocation(world, borderPadding() + 12, 60);
        int x = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE);
            }
        }
        placeEndFrame(world, x, y + 1, z - 2, BlockFace.SOUTH);
        placeEndFrame(world, x + 1, y + 1, z - 2, BlockFace.SOUTH);
        placeEndFrame(world, x - 1, y + 1, z - 2, BlockFace.SOUTH);
        placeEndFrame(world, x, y + 1, z + 2, BlockFace.NORTH);
        placeEndFrame(world, x + 1, y + 1, z + 2, BlockFace.NORTH);
        placeEndFrame(world, x - 1, y + 1, z + 2, BlockFace.NORTH);
        placeEndFrame(world, x - 2, y + 1, z, BlockFace.EAST);
        placeEndFrame(world, x - 2, y + 1, z + 1, BlockFace.EAST);
        placeEndFrame(world, x - 2, y + 1, z - 1, BlockFace.EAST);
        placeEndFrame(world, x + 2, y + 1, z, BlockFace.WEST);
        placeEndFrame(world, x + 2, y + 1, z + 1, BlockFace.WEST);
        placeEndFrame(world, x + 2, y + 1, z - 1, BlockFace.WEST);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.END_PORTAL);
            }
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private void placeEndFrame(World world, int x, int y, int z, BlockFace facing) {
        EndPortalFrame frame = (EndPortalFrame) Material.END_PORTAL_FRAME.createBlockData();
        frame.setFacing(facing);
        frame.setEye(true);
        world.getBlockAt(x, y, z).setBlockData(frame);
    }

    public String formatLocationMessage(String path, Location location) {
        String template = plugin.getConfig().getString(path, "&fLocation X%x% y%y% Z%z%");
        return template
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ()));
    }

    private String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private List<Holder> currentHolders() {
        List<Holder> holders = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            int count = countSpecialObsidian(player);
            if (count > 0) {
                holders.add(new Holder(player.getName(), count));
            }
        }
        holders.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return holders;
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Location readLocation(FileConfiguration data, String path) {
        String worldName = data.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"));
    }

    private void writeLocation(FileConfiguration data, String path, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
    }

    private record Holder(String name, int count) {
    }
}
