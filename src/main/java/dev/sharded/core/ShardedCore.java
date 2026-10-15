package dev.sharded.core;

import com.sharded.core.net.SpawnPacketFix;
import dev.sharded.core.chat.ChatCommands;
import dev.sharded.core.chat.ChatFilterListener;
import dev.sharded.core.chat.ChatFilterManager;
import dev.sharded.core.chat.ChatFormatListener;
import dev.sharded.core.grave.GraveManager;
import dev.sharded.core.message.MessageService;
import dev.sharded.core.mobs.MobToggleService;
import dev.sharded.core.pickup.MobPickupListener;
import dev.sharded.core.rollback.InventoryRollbackService;
import dev.sharded.core.rollback.JoinedPlayerCache;
import dev.sharded.core.rtp.RtpService;
import dev.sharded.core.spawn.FirstJoinListener;
import dev.sharded.core.spawn.SpawnCommands;
import dev.sharded.core.spawn.SpawnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedCore extends JavaPlugin {
    private MessageService messages;
    private SpawnManager spawns;
    private JoinedPlayerCache players;
    private ChatFilterManager chatFilter;
    private GraveManager graves;
    private MobToggleService mobs;
    private InventoryRollbackService rollback;
    private RtpService rtp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
        spawns = new SpawnManager(this);
        players = new JoinedPlayerCache(this);
        chatFilter = new ChatFilterManager(this);
        graves = new GraveManager(this);
        mobs = new MobToggleService(this);
        rollback = new InventoryRollbackService(this);
        rtp = new RtpService(this);
        reloadAll();

        getServer().getPluginManager().registerEvents(players, this);
        getServer().getPluginManager().registerEvents(new FirstJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MobPickupListener(this), this);
        getServer().getPluginManager().registerEvents(rtp, this);
        getServer().getPluginManager().registerEvents(graves, this);
        getServer().getPluginManager().registerEvents(mobs, this);
        getServer().getPluginManager().registerEvents(new ChatFilterListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatFormatListener(this), this);
        getServer().getPluginManager().registerEvents(rollback, this);

        SpawnCommands spawnCommands = new SpawnCommands(this);
        bind("spawn", spawnCommands, null);
        bind("setspawn", spawnCommands, null);
        bind("rtp", rtp, null);
        bind("mobtoggle", mobs, null);
        ChatCommands chatCommands = new ChatCommands(this);
        bind("chatfilter", chatCommands, chatCommands);
        bind("chathistory", chatCommands, chatCommands);
        bind("invrollback", rollback, rollback);
        bind("shardedcore", new CoreCommand(this), null);

        SpawnPacketFix.install(this);
        getLogger().info("ShardedCore enabled.");
    }

    @Override
    public void onDisable() {
        SpawnPacketFix.uninstall();
        if (graves != null) {
            graves.save();
        }
        if (mobs != null) {
            mobs.save();
        }
        if (players != null) {
            players.save();
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.load();
        spawns.load();
        players.load();
        chatFilter.load();
        graves.load();
        mobs.load();
        rollback.load();
    }

    public MessageService messages() {
        return messages;
    }

    public SpawnManager spawns() {
        return spawns;
    }

    public JoinedPlayerCache players() {
        return players;
    }

    public ChatFilterManager chatFilter() {
        return chatFilter;
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tab) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        if (tab != null) {
            command.setTabCompleter(tab);
        }
    }
}
