package dev.shardedsmp;

import dev.shardedsmp.command.DiamondsCommand;
import dev.shardedsmp.command.GraceCommand;
import dev.shardedsmp.command.ObsidianCommand;
import dev.shardedsmp.game.GameManager;
import dev.shardedsmp.game.GlowManager;
import dev.shardedsmp.game.KillStreakManager;
import dev.shardedsmp.game.ObsidianManager;
import dev.shardedsmp.game.QuestManager;
import dev.shardedsmp.listener.BossListener;
import dev.shardedsmp.listener.CombatListener;
import dev.shardedsmp.listener.EnchantListener;
import dev.shardedsmp.listener.ObsidianListener;
import dev.shardedsmp.listener.PlayerConnectionListener;
import dev.shardedsmp.listener.QuestListener;
import dev.shardedsmp.listener.ToolListener;
import dev.shardedsmp.listener.WorldProtectionListener;
import dev.shardedsmp.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedSMP extends JavaPlugin {
    private GameManager gameManager;
    private ObsidianManager obsidianManager;
    private GlowManager glowManager;
    private KillStreakManager killStreakManager;
    private QuestManager questManager;
    private EnchantListener enchantListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        glowManager = new GlowManager();
        glowManager.setup();
        gameManager = new GameManager(this);
        obsidianManager = new ObsidianManager(this);
        killStreakManager = new KillStreakManager();
        questManager = new QuestManager(this);
        enchantListener = new EnchantListener(this);
        gameManager.load();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(enchantListener, this);
        pluginManager.registerEvents(new ObsidianListener(this), this);
        pluginManager.registerEvents(new CombatListener(this), this);
        pluginManager.registerEvents(new WorldProtectionListener(this), this);
        pluginManager.registerEvents(new QuestListener(this), this);
        pluginManager.registerEvents(new ToolListener(this), this);
        pluginManager.registerEvents(new BossListener(this), this);
        pluginManager.registerEvents(new PlayerConnectionListener(this), this);

        bind("grace", new GraceCommand(this));
        bind("obsidian", new ObsidianCommand(this));
        PluginCommand diamonds = getCommand("diamonds");
        if (diamonds != null) {
            diamonds.setExecutor(new DiamondsCommand(this));
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> gameManager.tickActionBars(), 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> gameManager.tickGlowAndHearts(), 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.ensureEventWither();
            questManager.updateBossBar();
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> gameManager.save(), 20L * 60, 20L * 60);

        if (gameManager.graceStarted() && !gameManager.graceActive()) {
            obsidianManager.scheduleAfterGrace();
        }
        getLogger().info("ShardedSMP enabled.");
    }

    @Override
    public void onDisable() {
        if (obsidianManager != null) {
            obsidianManager.cancel();
        }
        if (questManager != null) {
            questManager.hide();
        }
        if (gameManager != null) {
            gameManager.save();
        }
        if (glowManager != null) {
            glowManager.shutdown();
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command " + name + " is missing from plugin.yml");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public GameManager game() {
        return gameManager;
    }

    public ObsidianManager obsidianManager() {
        return obsidianManager;
    }

    public GlowManager glowManager() {
        return glowManager;
    }

    public KillStreakManager killStreaks() {
        return killStreakManager;
    }

    public QuestManager questManager() {
        return questManager;
    }

    public EnchantListener listenerEnchant() {
        return enchantListener;
    }
}
