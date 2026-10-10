package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFilterModule implements Module, Listener, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private FileConfiguration blockedConfig;
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessageText = new ConcurrentHashMap<>();
    private final List<FilterRule> rules = new ArrayList<>();
    private final List<HistoryEntry> history = Collections.synchronizedList(new ArrayList<>());
    private final Map<Character, Character> leetMap = new HashMap<>();

    @Override
    public String getId() {
        return "chat-filter";
    }

    @Override
    public String getDisplayName() {
        return "Chat Filter";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        buildLeetMap();
        loadBlockedRules();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("chatfilter") != null) {
            plugin.getCommand("chatfilter").setExecutor(this);
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (plugin.getCommand("chatfilter") != null) {
            plugin.getCommand("chatfilter").setExecutor(null);
        }
        rules.clear();
        lastMessageAt.clear();
        lastMessageText.clear();
    }

    private void buildLeetMap() {
        leetMap.clear();
        putLeet('4', 'a');
        putLeet('@', 'a');
        putLeet('3', 'e');
        putLeet('1', 'i');
        putLeet('!', 'i');
        putLeet('|', 'i');
        putLeet('0', 'o');
        putLeet('5', 's');
        putLeet('$', 's');
        putLeet('7', 't');
        putLeet('+', 't');
        putLeet('8', 'b');
        putLeet('9', 'g');
        putLeet('2', 'z');
        putLeet('6', 'g');
    }

    private void putLeet(char from, char to) {
        leetMap.put(from, to);
        leetMap.put(Character.toUpperCase(from), to);
    }

    private void loadBlockedRules() {
        rules.clear();
        File file = new File(plugin.getDataFolder(), "blocked.yml");
        if (!file.exists()) {
            plugin.saveResource("blocked.yml", false);
        }
        blockedConfig = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource("blocked.yml");
        if (def != null) {
            blockedConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }

        ConfigurationSection rulesSection = blockedConfig.getConfigurationSection("rules");
        if (rulesSection == null) {
            return;
        }
        for (String key : rulesSection.getKeys(false)) {
            ConfigurationSection section = rulesSection.getConfigurationSection(key);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            List<Pattern> patterns = new ArrayList<>();
            for (String raw : section.getStringList("patterns")) {
                try {
                    patterns.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Invalid chat filter pattern in " + key + ": " + ex.getMessage());
                }
            }
            if (patterns.isEmpty()) {
                continue;
            }
            rules.add(new FilterRule(
                    key,
                    section.getString("action", "CANCEL").toUpperCase(Locale.ROOT),
                    section.getBoolean("alert", true),
                    section.getString("bypass", ""),
                    patterns
            ));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastMessageAt.remove(uuid);
        lastMessageText.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("scan.chat", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("shardedlobbycore.chatfilter.bypass")) {
            return;
        }

        FilterResult result = filterMessage(player, event.getMessage(), false);
        if (result.cancelled()) {
            event.setCancelled(true);
            return;
        }
        if (result.modified() != null) {
            event.setMessage(result.modified());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("shardedlobbycore.chatfilter.bypass")) {
            return;
        }

        String raw = event.getMessage().substring(1);
        String[] parts = raw.split(" ", 2);
        String rawLabel = parts[0].toLowerCase(Locale.ROOT);
        final String label = rawLabel.contains(":")
                ? rawLabel.substring(rawLabel.indexOf(':') + 1)
                : rawLabel;

        boolean isPrivate = Set.of("msg", "tell", "w", "whisper", "message", "r", "reply").contains(label);
        List<String> scanCommands = config.getStringList("scan.commands");
        boolean scanCommand = scanCommands.stream().anyMatch(c -> c.equalsIgnoreCase(label));

        if (isPrivate && config.getBoolean("scan.private-messages", true)) {
            String body = parts.length > 1 ? parts[1] : "";
            // skip target name for msg/tell/w
            if (!label.equals("r") && !label.equals("reply") && body.contains(" ")) {
                body = body.substring(body.indexOf(' ') + 1);
            }
            FilterResult result = filterMessage(player, body, true);
            if (result.cancelled()) {
                event.setCancelled(true);
            }
            return;
        }

        if (scanCommand && parts.length > 1) {
            FilterResult result = filterMessage(player, parts[1], true);
            if (result.cancelled()) {
                event.setCancelled(true);
            }
        }
    }

    private FilterResult filterMessage(Player player, String message, boolean commandContext) {
        UUID uuid = player.getUniqueId();

        // Slowmode
        if (config.getBoolean("slowmode.enabled", true) && !commandContext) {
            long now = System.currentTimeMillis();
            long waitMs = config.getLong("slowmode.seconds", 3) * 1000L;
            Long last = lastMessageAt.get(uuid);
            if (last != null && now - last < waitMs) {
                long remain = Math.max(1, (waitMs - (now - last) + 999) / 1000);
                deny(player, "slowmode", "CANCEL", message, false,
                        config.getString("messages.slowmode").replace("%seconds%", String.valueOf(remain)));
                return FilterResult.cancel();
            }
        }

        // Length
        if (config.getBoolean("length.enabled", true)) {
            int max = config.getInt("length.max-characters", 128);
            if (message.length() > max) {
                deny(player, "length", "CANCEL", message, false,
                        config.getString("messages.too-long").replace("%max%", String.valueOf(max)));
                return FilterResult.cancel();
            }
            int maxSame = config.getInt("length.max-same-in-a-row", 5);
            if (hasSameCharSpam(message, maxSame)) {
                deny(player, "spamming", "CANCEL", message, false,
                        config.getString("messages.spamming"));
                return FilterResult.cancel();
            }
        }

        // Repeat
        if (config.getBoolean("repeat.enabled", true) && !commandContext) {
            String previous = lastMessageText.get(uuid);
            Long last = lastMessageAt.get(uuid);
            long rememberMs = config.getLong("repeat.remember-seconds", 30) * 1000L;
            if (previous != null && last != null && System.currentTimeMillis() - last <= rememberMs) {
                int percent = similarityPercent(previous, message);
                if (percent >= config.getInt("repeat.match-percent", 80)) {
                    deny(player, "repeat", "CANCEL", message, false,
                            config.getString("messages.repeating"));
                    return FilterResult.cancel();
                }
            }
        }

        // Shouting
        String working = message;
        if (config.getBoolean("shouting.enabled", true)) {
            int upper = countUppercase(working);
            if (upper > config.getInt("shouting.max-uppercase", 8)) {
                String action = config.getString("shouting.action", "LOWERCASE").toUpperCase(Locale.ROOT);
                if ("CANCEL".equals(action)) {
                    deny(player, "shouting", "CANCEL", working, false,
                            config.getString("messages.shouting"));
                    return FilterResult.cancel();
                }
                working = working.toLowerCase(Locale.ROOT);
                if (config.getBoolean("alerts.checks", false)) {
                    alertStaff(player, working, "shouting", "LOWERCASE");
                }
            }
        }

        // Word rules
        if (config.getBoolean("words.enabled", true)) {
            for (FilterRule rule : rules) {
                if (rule.bypass != null && !rule.bypass.isEmpty() && player.hasPermission(rule.bypass)) {
                    continue;
                }
                RuleHit hit = applyRule(rule, working);
                if (hit == null) {
                    continue;
                }
                if ("CANCEL".equals(rule.action) || "WARN".equals(rule.action) && hit.cancelled) {
                    deny(player, rule.name, rule.action, working, rule.alert,
                            config.getString("messages.blocked"));
                    if ("CANCEL".equals(rule.action)) {
                        return FilterResult.cancel();
                    }
                }
                if ("REPLACE".equals(rule.action)) {
                    working = hit.result;
                    if (rule.alert) {
                        alertStaff(player, working, rule.name, rule.action);
                    }
                    logHit(player, working, rule.name, rule.action);
                }
            }
        }

        if (!commandContext) {
            lastMessageAt.put(uuid, System.currentTimeMillis());
            lastMessageText.put(uuid, message);
        }

        if (!working.equals(message)) {
            return FilterResult.modify(working);
        }
        return FilterResult.ok();
    }

    private RuleHit applyRule(FilterRule rule, String message) {
        String mask = config.getString("words.mask", "***");
        List<String> variants = List.of(message, normalize(message), glue(normalize(message)));
        for (String variant : variants) {
            for (Pattern pattern : rule.patterns) {
                Matcher matcher = pattern.matcher(variant);
                if (!matcher.find()) {
                    continue;
                }
                if ("REPLACE".equals(rule.action)) {
                    String replaced = pattern.matcher(message).replaceAll(Matcher.quoteReplacement(mask));
                    // also try on normalized if raw didn't match
                    if (replaced.equals(message)) {
                        replaced = maskWords(message, pattern, mask);
                    }
                    return new RuleHit(replaced, false);
                }
                return new RuleHit(message, true);
            }
        }
        return null;
    }

    private String maskWords(String original, Pattern pattern, String mask) {
        Matcher matcher = pattern.matcher(normalize(original));
        if (!matcher.find()) {
            return original;
        }
        return pattern.matcher(original).replaceAll(Matcher.quoteReplacement(mask));
    }

    private String normalize(String input) {
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nfd.length(); i++) {
            char c = nfd.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            char mapped = leetMap.getOrDefault(c, Character.toLowerCase(c));
            if (Character.isLetterOrDigit(mapped)) {
                out.append(mapped);
            } else {
                out.append(' ');
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private String glue(String normalized) {
        // Glue short single-letter tokens: "f u c k" -> "fuck", keep longer words
        String[] parts = normalized.split(" ");
        StringBuilder out = new StringBuilder();
        StringBuilder shortRun = new StringBuilder();
        for (String part : parts) {
            if (part.length() <= 1) {
                shortRun.append(part);
            } else {
                if (shortRun.length() > 0) {
                    if (!out.isEmpty()) {
                        out.append(' ');
                    }
                    out.append(shortRun);
                    shortRun.setLength(0);
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(part);
            }
        }
        if (shortRun.length() > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(shortRun);
        }
        return out.toString();
    }

    private boolean hasSameCharSpam(String message, int maxSame) {
        if (message.isEmpty()) {
            return false;
        }
        int run = 1;
        char prev = message.charAt(0);
        for (int i = 1; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.toLowerCase(c) == Character.toLowerCase(prev)) {
                run++;
                if (run > maxSame) {
                    return true;
                }
            } else {
                run = 1;
                prev = c;
            }
        }
        return false;
    }

    private int countUppercase(String message) {
        int count = 0;
        for (char c : message.toCharArray()) {
            if (Character.isUpperCase(c)) {
                count++;
            }
        }
        return count;
    }

    private int similarityPercent(String a, String b) {
        String left = a.toLowerCase(Locale.ROOT).trim();
        String right = b.toLowerCase(Locale.ROOT).trim();
        if (left.equals(right)) {
            return 100;
        }
        int distance = levenshtein(left, right);
        int max = Math.max(left.length(), right.length());
        if (max == 0) {
            return 100;
        }
        return (int) Math.round((1.0 - (distance / (double) max)) * 100.0);
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private void deny(Player player, String rule, String action, String message, boolean forceAlert, String playerMessage) {
        sendPlayerMessage(player, playerMessage);
        playSound(player, "blocked");
        boolean alert = forceAlert || config.getBoolean("alerts.enabled", true)
                && (forceAlert || config.getBoolean("alerts.checks", false)
                || !Set.of("slowmode", "length", "spamming", "repeat", "shouting").contains(rule));
        if (forceAlert || (config.getBoolean("alerts.enabled", true) && (
                forceAlert
                        || (!Set.of("slowmode", "length", "spamming", "repeat", "shouting").contains(rule))
                        || config.getBoolean("alerts.checks", false)))) {
            alertStaff(player, message, rule, action);
        }
        logHit(player, message, rule, action);
    }

    private void sendPlayerMessage(Player player, String message) {
        if (message == null) {
            return;
        }
        if (config.getBoolean("messages.actionbar", false)) {
            MessageUtil.sendActionBar(player, message);
        } else {
            MessageUtil.sendFormatted(player, message);
        }
    }

    private void alertStaff(Player player, String message, String rule, String action) {
        if (!config.getBoolean("alerts.enabled", true)) {
            return;
        }
        String format = config.getString("alerts.format",
                        "&#FF0000&lFILTER &8▷ &f%player% &7» &f%message% &8(%rule% %action%)")
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%rule%", rule)
                .replace("%action%", action);
        Bukkit.getConsoleSender().sendMessage(MessageUtil.colorize(format));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("shardedlobbycore.chatfilter.alerts")) {
                MessageUtil.sendFormatted(staff, format);
            }
        }
    }

    private void logHit(Player player, String message, String rule, String action) {
        if (!config.getBoolean("log.enabled", true)) {
            return;
        }
        boolean rulesOnly = config.getBoolean("log.rules-only", true);
        if (rulesOnly && Set.of("slowmode", "length", "spamming", "repeat", "shouting").contains(rule)) {
            return;
        }
        history.add(0, new HistoryEntry(player.getName(), message, rule, action, Instant.now().getEpochSecond()));
        int keepDays = config.getInt("log.keep-days", 14);
        long cutoff = Instant.now().getEpochSecond() - (keepDays * 86400L);
        history.removeIf(entry -> entry.epochSeconds < cutoff);
        if (history.size() > 500) {
            history.subList(500, history.size()).clear();
        }
    }

    private void playSound(Player player, String key) {
        if (!config.getBoolean("sounds." + key + ".enabled", true)) {
            return;
        }
        String soundName = config.getString("sounds." + key + ".sound", "BLOCK_NOTE_BLOCK_BASS");
        final Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (IllegalArgumentException ex) {
            playSoundResolved(player, Sound.BLOCK_NOTE_BLOCK_BASS, key);
            return;
        }
        playSoundResolved(player, sound, key);
    }

    private void playSoundResolved(Player player, Sound sound, String key) {
        float volume = (float) config.getDouble("sounds." + key + ".volume", 0.8);
        float pitch = (float) config.getDouble("sounds." + key + ".pitch", 0.8);
        Bukkit.getScheduler().runTask(plugin, () -> player.playSound(player.getLocation(), sound, volume, pitch));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedlobbycore.chatfilter.admin")) {
            MessageUtil.sendRaw(sender, "&#FF0000&lERROR &8▷ &fNo permission.");
            return true;
        }
        if (args.length == 0) {
            MessageUtil.sendRaw(sender, "&#FF0000&lFILTER &8▷ &f/chatfilter <reload|test|history|similar|regex>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                loadBlockedRules();
                MessageUtil.sendRaw(sender, "&#94FF00&lFILTER &8▷ &fReloaded blocked.yml rules.");
            }
            case "test" -> {
                if (args.length < 2) {
                    MessageUtil.sendRaw(sender, config.getString("messages.usage-test"));
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                for (FilterRule rule : rules) {
                    RuleHit hit = applyRule(rule, message);
                    if (hit != null) {
                        MessageUtil.sendRaw(sender, config.getString("messages.test-hit")
                                .replace("%rule%", rule.name)
                                .replace("%action%", rule.action)
                                .replace("%result%", hit.result));
                        return true;
                    }
                }
                MessageUtil.sendRaw(sender, config.getString("messages.test-clean"));
            }
            case "history" -> {
                if (history.isEmpty()) {
                    MessageUtil.sendRaw(sender, config.getString("messages.history-empty"));
                    return true;
                }
                int limit = Math.min(15, history.size());
                for (int i = 0; i < limit; i++) {
                    HistoryEntry entry = history.get(i);
                    MessageUtil.sendRaw(sender, "&#FF0000&lFILTER &8▷ &f" + entry.player
                            + " &7» &f" + entry.message + " &8(" + entry.rule + " " + entry.action + ")");
                }
            }
            case "similar" -> {
                if (args.length < 3) {
                    MessageUtil.sendRaw(sender, config.getString("messages.usage-similar"));
                    return true;
                }
                int percent = similarityPercent(args[1], args[2]);
                MessageUtil.sendRaw(sender, config.getString("messages.similar")
                        .replace("%first%", args[1])
                        .replace("%second%", args[2])
                        .replace("%percent%", String.valueOf(percent)));
            }
            case "regex" -> {
                if (args.length < 2) {
                    MessageUtil.sendRaw(sender, config.getString("messages.usage-regex"));
                    return true;
                }
                String word = args[1].toLowerCase(Locale.ROOT);
                if (word.chars().filter(Character::isLetter).distinct().count() < 3) {
                    MessageUtil.sendRaw(sender, config.getString("messages.regex-invalid"));
                    return true;
                }
                String ruleName = "PROFANITY";
                List<String> patterns = blockedConfig.getStringList("rules." + ruleName + ".patterns");
                String built = "(?<![\\p{L}\\p{N}])" + word.chars()
                        .mapToObj(c -> (char) c + "+")
                        .reduce("", String::concat)
                        + "(?:s|es|ed|er|ers|ing|in|y|z|a)?(?![\\p{L}\\p{N}])";
                if (patterns.stream().anyMatch(p -> p.contains(word))) {
                    MessageUtil.sendRaw(sender, config.getString("messages.regex-known")
                            .replace("%word%", word).replace("%rule%", ruleName));
                    return true;
                }
                patterns.add(built);
                blockedConfig.set("rules." + ruleName + ".patterns", patterns);
                try {
                    blockedConfig.save(new File(plugin.getDataFolder(), "blocked.yml"));
                    loadBlockedRules();
                    MessageUtil.sendRaw(sender, config.getString("messages.regex-added")
                            .replace("%word%", word).replace("%rule%", ruleName));
                } catch (IOException e) {
                    MessageUtil.sendRaw(sender, "&#FF0000&lERROR &8▷ &fCould not save blocked.yml");
                }
            }
            default -> MessageUtil.sendRaw(sender, "&#FF0000&lFILTER &8▷ &f/chatfilter <reload|test|history|similar|regex>");
        }
        return true;
    }

    private record FilterRule(String name, String action, boolean alert, String bypass, List<Pattern> patterns) {
    }

    private record RuleHit(String result, boolean cancelled) {
    }

    private record HistoryEntry(String player, String message, String rule, String action, long epochSeconds) {
    }

    private record FilterResult(boolean cancelled, String modified) {
        static FilterResult ok() {
            return new FilterResult(false, null);
        }

        static FilterResult cancel() {
            return new FilterResult(true, null);
        }

        static FilterResult modify(String message) {
            return new FilterResult(false, message);
        }
    }
}
