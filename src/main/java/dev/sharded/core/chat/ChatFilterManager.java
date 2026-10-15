package dev.sharded.core.chat;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.chat.filter.ChatFilterEngine;
import dev.sharded.core.chat.filter.FilterConfig;
import dev.sharded.core.chat.filter.FilterVerdict;
import dev.sharded.core.chat.filter.PlayerFilterState;
import dev.sharded.core.chat.filter.WordRule;
import dev.sharded.core.util.ColorUtil;
import dev.sharded.core.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatFilterManager {
    private final ShardedCore plugin;
    private final Map<UUID, PlayerFilterState> states = new ConcurrentHashMap<>();
    private File filterFile;
    private File blockedFile;
    private File historyFile;
    private FileConfiguration filterYaml;
    private FileConfiguration blockedYaml;
    private FileConfiguration historyYaml;
    private ChatFilterEngine engine;
    private FilterConfig config = new FilterConfig();

    public ChatFilterManager(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("chat-filter.yml", false);
        plugin.saveResource("blocked.yml", false);
        filterFile = new File(plugin.getDataFolder(), "chat-filter.yml");
        blockedFile = new File(plugin.getDataFolder(), "blocked.yml");
        historyFile = new File(plugin.getDataFolder(), "chat-history.yml");
        filterYaml = YamlConfiguration.loadConfiguration(filterFile);
        blockedYaml = YamlConfiguration.loadConfiguration(blockedFile);
        historyYaml = historyFile.exists() ? YamlConfiguration.loadConfiguration(historyFile) : new YamlConfiguration();
        config = readConfig();
        engine = new ChatFilterEngine(config, readRules());
        pruneHistory();
    }

    public FilterConfig config() {
        return config;
    }

    public ChatFilterEngine engine() {
        return engine;
    }

    public FileConfiguration filterYaml() {
        return filterYaml;
    }

    public FilterVerdict check(Player player, String message) {
        PlayerFilterState state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerFilterState());
        return engine.evaluate(message, System.currentTimeMillis(), state);
    }

    public void apply(Player player, FilterVerdict verdict) {
        if (verdict.status() == FilterVerdict.Status.ALLOW) {
            return;
        }
        boolean wordRule = "CANCEL".equals(verdict.action()) && !verdict.isCheck()
                || "MASK".equals(verdict.action());
        boolean shouldAlert = config.alertsEnabled && (wordRule || (config.alertChecks && verdict.isCheck()));
        boolean shouldLog = config.logEnabled && (wordRule || (!config.logRulesOnly && verdict.blocked()));
        if (verdict.blocked()) {
            sendDenied(player, verdict);
            if (config.soundBlockedEnabled) {
                SoundUtil.play(player, config.soundBlocked, config.soundBlockedVolume, config.soundBlockedPitch);
            }
        }
        if (shouldAlert) {
            alert(player, verdict);
        }
        if (shouldLog) {
            log(player, verdict);
        }
    }

    public String message(String key, String... replacements) {
        String template = filterYaml.getString("messages." + key, key);
        return ColorUtil.apply(template, replacements);
    }

    public Component component(String key, String... replacements) {
        return ColorUtil.parse(message(key, replacements));
    }

    public void sendDenied(Player player, FilterVerdict verdict) {
        Component component = switch (verdict.rule()) {
            case "slowmode" -> component("slowmode", "%seconds%", String.valueOf(verdict.waitSeconds()));
            case "length" -> component("too-long", "%max%", String.valueOf(verdict.maxCharacters()));
            case "spamming" -> component("spamming");
            case "repeat" -> component("repeating");
            case "shouting" -> component("shouting");
            default -> component("blocked");
        };
        if (config.actionbar) {
            player.sendActionBar(component);
        } else {
            player.sendMessage(component);
        }
    }

    public void alert(Player player, FilterVerdict verdict) {
        String line = ColorUtil.apply(
                config.alertFormat,
                "%player%", player.getName(),
                "%message%", verdict.original(),
                "%rule%", verdict.rule(),
                "%action%", verdict.action()
        );
        Component component = ColorUtil.parse(line);
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("shardedcore.chatfilter.alerts")) {
                staff.sendMessage(component);
            }
        }
    }

    public void log(Player player, FilterVerdict verdict) {
        List<Map<String, Object>> entries = readEntries();
        entries.add(Map.of(
                "time", Instant.now().toString(),
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "message", verdict.original(),
                "rule", verdict.rule(),
                "action", verdict.action(),
                "result", verdict.result()
        ));
        historyYaml.set("entries", entries);
        saveHistory();
    }

    public List<Map<String, Object>> history(String playerName) {
        List<Map<String, Object>> entries = readEntries();
        if (playerName == null || playerName.isBlank()) {
            return entries;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (playerName.equalsIgnoreCase(String.valueOf(entry.get("player")))) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public boolean addRegex(String word, StringBuilder ruleOut) {
        WordRule rule = engine.defaultRule();
        if (rule == null) {
            return false;
        }
        ruleOut.append(rule.name());
        String pattern = dev.sharded.core.util.Similarity.antiBypassRegex(word);
        List<String> regex = new ArrayList<>(blockedYaml.getStringList("rules." + rule.name() + ".regex"));
        if (regex.stream().anyMatch(existing -> existing.equalsIgnoreCase(pattern) || existing.equalsIgnoreCase(word))) {
            return false;
        }
        if (rule.containsWord(word) || rule.containsWord(pattern)) {
            return false;
        }
        regex.add(pattern);
        blockedYaml.set("rules." + rule.name() + ".regex", regex);
        saveBlocked();
        engine = new ChatFilterEngine(config, readRules());
        return true;
    }

    public void playClick(Player player) {
        if (config.soundClickEnabled) {
            SoundUtil.play(player, config.soundClick, config.soundClickVolume, config.soundClickPitch);
        }
    }

    public boolean scansCommand(String label) {
        String stripped = strip(label);
        for (String command : config.scanCommands) {
            if (strip(command).equalsIgnoreCase(stripped)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPrivateMessage(String label) {
        return switch (strip(label).toLowerCase(Locale.ROOT)) {
            case "msg", "tell", "w", "whisper", "r", "reply", "message", "pm", "dm", "m", "t", "emsg", "etell" -> true;
            default -> false;
        };
    }

    public static String strip(String label) {
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    public List<String> helpLines() {
        return filterYaml.getStringList("messages.help");
    }

    private FilterConfig readConfig() {
        FilterConfig cfg = new FilterConfig();
        cfg.scanChat = filterYaml.getBoolean("scan.chat", true);
        cfg.scanPrivateMessages = filterYaml.getBoolean("scan.private-messages", true);
        cfg.scanCommands = new ArrayList<>(filterYaml.getStringList("scan.commands"));
        cfg.slowmodeEnabled = filterYaml.getBoolean("slowmode.enabled", true);
        cfg.slowmodeSeconds = filterYaml.getInt("slowmode.seconds", 3);
        cfg.lengthEnabled = filterYaml.getBoolean("length.enabled", true);
        cfg.maxCharacters = filterYaml.getInt("length.max-characters", 128);
        cfg.maxSameInARow = filterYaml.getInt("length.max-same-in-a-row", 5);
        cfg.repeatEnabled = filterYaml.getBoolean("repeat.enabled", true);
        cfg.rememberSeconds = filterYaml.getInt("repeat.remember-seconds", 30);
        cfg.matchPercent = filterYaml.getInt("repeat.match-percent", 80);
        cfg.shoutingEnabled = filterYaml.getBoolean("shouting.enabled", true);
        cfg.maxUppercase = filterYaml.getInt("shouting.max-uppercase", 8);
        cfg.shoutingAction = FilterConfig.ShoutingAction.parse(filterYaml.getString("shouting.action"));
        cfg.wordsEnabled = filterYaml.getBoolean("words.enabled", true);
        cfg.mask = filterYaml.getString("words.mask", "***");
        cfg.alertsEnabled = filterYaml.getBoolean("alerts.enabled", true);
        cfg.alertChecks = filterYaml.getBoolean("alerts.checks", false);
        cfg.alertFormat = filterYaml.getString("alerts.format", cfg.alertFormat);
        cfg.logEnabled = filterYaml.getBoolean("log.enabled", true);
        cfg.logRulesOnly = filterYaml.getBoolean("log.rules-only", true);
        cfg.keepDays = filterYaml.getInt("log.keep-days", 14);
        cfg.soundBlockedEnabled = filterYaml.getBoolean("sounds.blocked.enabled", true);
        cfg.soundBlocked = filterYaml.getString("sounds.blocked.sound", "block.note_block.bass");
        cfg.soundBlockedVolume = (float) filterYaml.getDouble("sounds.blocked.volume", 0.8);
        cfg.soundBlockedPitch = (float) filterYaml.getDouble("sounds.blocked.pitch", 0.8);
        cfg.soundClickEnabled = filterYaml.getBoolean("sounds.click.enabled", true);
        cfg.soundClick = filterYaml.getString("sounds.click.sound", "ui.button.click");
        cfg.soundClickVolume = (float) filterYaml.getDouble("sounds.click.volume", 1.0);
        cfg.soundClickPitch = (float) filterYaml.getDouble("sounds.click.pitch", 1.2);
        cfg.actionbar = filterYaml.getBoolean("messages.actionbar", false);
        return cfg;
    }

    private List<WordRule> readRules() {
        List<WordRule> rules = new ArrayList<>();
        ConfigurationSection section = blockedYaml.getConfigurationSection("rules");
        if (section == null) {
            return rules;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection rule = section.getConfigurationSection(name);
            if (rule == null) {
                continue;
            }
            rules.add(new WordRule(
                    name,
                    WordRule.Action.parse(rule.getString("action", "CANCEL")),
                    rule.getStringList("words"),
                    rule.getStringList("regex")
            ));
        }
        return rules;
    }

    private List<Map<String, Object>> readEntries() {
        List<Map<String, Object>> entries = new ArrayList<>();
        List<?> raw = historyYaml.getList("entries", List.of());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                entries.add(copy);
            }
        }
        return entries;
    }

    private void pruneHistory() {
        long cutoff = System.currentTimeMillis() - config.keepDays * 86_400_000L;
        List<Map<String, Object>> entries = readEntries();
        Iterator<Map<String, Object>> it = entries.iterator();
        while (it.hasNext()) {
            Object time = it.next().get("time");
            try {
                if (Instant.parse(String.valueOf(time)).toEpochMilli() < cutoff) {
                    it.remove();
                }
            } catch (RuntimeException ignored) {
            }
        }
        historyYaml.set("entries", entries);
        saveHistory();
    }

    private void saveBlocked() {
        try {
            blockedYaml.save(blockedFile);
        } catch (IOException ignored) {
        }
    }

    private void saveHistory() {
        try {
            historyYaml.save(historyFile);
        } catch (IOException ignored) {
        }
    }
}
