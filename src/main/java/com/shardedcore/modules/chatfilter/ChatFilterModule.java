package com.shardedcore.modules.chatfilter;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatFilterModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private static final Map<Character, Character> LEET = Map.ofEntries(
            Map.entry('4', 'a'), Map.entry('@', 'a'), Map.entry('8', 'b'), Map.entry('3', 'e'),
            Map.entry('6', 'g'), Map.entry('9', 'g'), Map.entry('1', 'i'), Map.entry('!', 'i'),
            Map.entry('|', 'i'), Map.entry('0', 'o'), Map.entry('$', 's'), Map.entry('5', 's'),
            Map.entry('7', 't'), Map.entry('+', 't'), Map.entry('2', 'z')
    );

    private File blockedFile;
    private FileConfiguration blocked;
    private final Map<UUID, Long> lastAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMsg = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMsgAt = new ConcurrentHashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public ChatFilterModule(ShardedCore plugin) {
        super(plugin, "chatfilter");
    }

    @Override
    protected void extraFiles() {
        extraFile("blocked.yml");
    }

    @Override
    public void enable() {
        blockedFile = new File(folder, "blocked.yml");
        loadRules();
        try {
            plugin.toggles().sqlite().run("""
                    CREATE TABLE IF NOT EXISTS chatfilter_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        message TEXT NOT NULL,
                        rule TEXT NOT NULL,
                        action TEXT NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException ignored) {
        }
        registerListener(this);
        registerCommand("chatfilter", this);
        registerCommand("chathistory", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        loadRules();
    }

    private void loadRules() {
        blocked = Configs.load(blockedFile);
        rules.clear();
        ConfigurationSection section = blocked.getConfigurationSection("rules");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            ConfigurationSection rule = section.getConfigurationSection(name);
            if (rule == null || !rule.getBoolean("enabled", true)) continue;
            List<Pattern> patterns = new ArrayList<>();
            for (String raw : rule.getStringList("patterns")) {
                try {
                    patterns.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (Exception ex) {
                    plugin.getLogger().warning("[chatfilter] bad pattern in " + name + ": " + raw);
                }
            }
            rules.add(new Rule(name, rule.getString("action", "CANCEL"), rule.getBoolean("alert", true),
                    rule.getString("bypass", ""), patterns));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.getBoolean("scan.chat", true)) return;
        Player player = event.getPlayer();
        String original = PlainTextComponentSerializer.plainText().serialize(event.message());
        Result result = filter(player, original, false);
        if (result == null) return;
        if (result.cancel) {
            event.setCancelled(true);
            deny(player, result);
            return;
        }
        if (result.rewritten != null) {
            event.message(net.kyori.adventure.text.Component.text(result.rewritten));
        }
        if (result.alert) alert(player, result);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) return;
        String without = message.substring(1);
        int space = without.indexOf(' ');
        String label = (space < 0 ? without : without.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : without.substring(space + 1);
        boolean priv = List.of("msg", "tell", "whisper", "w", "m", "r", "reply", "message").contains(label);
        if (priv && config.getBoolean("scan.private-messages", true) && !rest.isBlank()) {
            Result result = filter(event.getPlayer(), rest, true);
            if (result != null && result.cancel) {
                event.setCancelled(true);
                deny(event.getPlayer(), result);
            }
            return;
        }
        for (String command : config.getStringList("scan.commands")) {
            if (label.equalsIgnoreCase(command) && !rest.isBlank()) {
                Result result = filter(event.getPlayer(), rest, true);
                if (result != null && result.cancel) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), result);
                }
            }
        }
    }

    public Result filter(Player player, String message, boolean command) {
        if (player.hasPermission("shardedcore.chatfilter.bypass")) return null;
        long now = System.currentTimeMillis();
        if (config.getBoolean("slowmode.enabled", true)) {
            int seconds = config.getInt("slowmode.seconds", 3);
            Long last = lastAt.get(player.getUniqueId());
            if (last != null && now - last < seconds * 1000L) {
                Result result = Result.check("SLOWMODE", "CANCEL", message,
                        cfg("messages.slowmode", "").replace("%seconds%",
                                String.valueOf((seconds * 1000L - (now - last) + 999) / 1000)));
                if (!command) return result;
            }
        }
        if (config.getBoolean("length.enabled", true) && message.length() > config.getInt("length.max-characters", 128)) {
            return Result.check("LENGTH", "CANCEL", message,
                    cfg("messages.too-long", "").replace("%max%", String.valueOf(config.getInt("length.max-characters", 128))));
        }
        if (config.getBoolean("length.enabled", true) && repeats(message, config.getInt("length.max-same-in-a-row", 5))) {
            return Result.check("SPAM", "CANCEL", message, cfg("messages.spamming", ""));
        }
        if (config.getBoolean("repeat.enabled", true)) {
            String previous = lastMsg.get(player.getUniqueId());
            Long at = lastMsgAt.get(player.getUniqueId());
            int remember = config.getInt("repeat.remember-seconds", 30);
            if (previous != null && at != null && now - at <= remember * 1000L
                    && similarity(previous, message) >= config.getInt("repeat.match-percent", 80)) {
                return Result.check("REPEAT", "CANCEL", message, cfg("messages.repeating", ""));
            }
        }
        String working = message;
        if (config.getBoolean("shouting.enabled", true)) {
            int caps = 0;
            for (int i = 0; i < working.length(); i++) if (Character.isUpperCase(working.charAt(i))) caps++;
            if (caps > config.getInt("shouting.max-uppercase", 8)) {
                if (cfg("shouting.action", "LOWERCASE").equalsIgnoreCase("CANCEL")) {
                    return Result.check("SHOUTING", "CANCEL", working, cfg("messages.shouting", ""));
                }
                working = working.toLowerCase(Locale.ROOT);
                player.sendMessage(ColorUtil.parse(cfg("messages.shouting", "")));
            }
        }
        if (config.getBoolean("words.enabled", true)) {
            Result hit = applyRules(player, working);
            if (hit != null) {
                lastAt.put(player.getUniqueId(), now);
                lastMsg.put(player.getUniqueId(), message);
                lastMsgAt.put(player.getUniqueId(), now);
                return hit;
            }
        }
        lastAt.put(player.getUniqueId(), now);
        lastMsg.put(player.getUniqueId(), message);
        lastMsgAt.put(player.getUniqueId(), now);
        if (!working.equals(message)) return Result.rewrite(working);
        return null;
    }

    private Result applyRules(Player player, String message) {
        String[] variants = {message, normalize(message), glue(normalize(message))};
        for (Rule rule : rules) {
            if (rule.bypass != null && !rule.bypass.isBlank() && player.hasPermission(rule.bypass)) continue;
            for (String variant : variants) {
                for (Pattern pattern : rule.patterns) {
                    Matcher matcher = pattern.matcher(variant);
                    if (!matcher.find()) continue;
                    String rewritten = message;
                    if (rule.action.equalsIgnoreCase("REPLACE")) {
                        rewritten = pattern.matcher(message).replaceAll(cfg("words.mask", "***"));
                    }
                    boolean cancel = rule.action.equalsIgnoreCase("CANCEL");
                    return new Result(rule.name, rule.action, cancel, rule.alert, rewritten.equals(message) ? null : rewritten,
                            message, cfg("messages.blocked", ""));
                }
            }
        }
        return null;
    }

    private void deny(Player player, Result result) {
        if (result.playerMessage != null && !result.playerMessage.isBlank()) {
            sendRaw(player, result.playerMessage);
        }
        Sounds.play(player, config.getConfigurationSection("sounds.blocked"));
        if (result.alert || (config.getBoolean("alerts.checks", false) && result.check)) alert(player, result);
        log(player, result);
    }

    private void alert(Player player, Result result) {
        if (!config.getBoolean("alerts.enabled", true)) return;
        String line = cfg("alerts.format", "")
                .replace("%player%", player.getName())
                .replace("%message%", result.original)
                .replace("%rule%", result.rule)
                .replace("%action%", result.action);
        Bukkit.getConsoleSender().sendMessage(ColorUtil.parse(line));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("shardedcore.chatfilter.alerts")) staff.sendMessage(ColorUtil.parse(line));
        }
    }

    private void log(Player player, Result result) {
        if (!config.getBoolean("log.enabled", true)) return;
        if (config.getBoolean("log.rules-only", true) && result.check) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.toggles().sqlite().execute(
                        "INSERT INTO chatfilter_log (uuid, name, message, rule, action, at) VALUES (?, ?, ?, ?, ?, ?)",
                        player.getUniqueId().toString(), player.getName(), result.original, result.rule, result.action,
                        System.currentTimeMillis());
                int days = config.getInt("log.keep-days", 14);
                if (days > 0) {
                    plugin.toggles().sqlite().execute("DELETE FROM chatfilter_log WHERE at < ?",
                            System.currentTimeMillis() - days * 86400000L);
                }
            } catch (SQLException ignored) {
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chathistory")) {
            history(sender, args);
            return true;
        }
        if (args.length == 0) {
            sendRaw(sender, "&#FF0000&lCHAT &8▷ &f/chatfilter <regex|similar|test|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reload();
                sendRaw(sender, "&#97F900&lCHAT &8▷ &fReloaded.");
            }
            case "regex" -> regex(sender, args);
            case "similar" -> {
                if (args.length < 3) {
                    send(sender, "messages.usage-similar");
                    return true;
                }
                send(sender, "messages.similar", "first", args[1], "second", args[2],
                        "percent", String.valueOf(similarity(args[1], args[2])));
            }
            case "test" -> {
                if (args.length < 2) {
                    send(sender, "messages.usage-test");
                    return true;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Player dummy = sender instanceof Player player ? player : null;
                Result result = dummy == null ? applyRulesDummy(text) : applyRules(dummy, text);
                if (result == null) send(sender, "messages.test-clean");
                else send(sender, "messages.test-hit", "rule", result.rule, "action", result.action,
                        "result", result.rewritten == null ? text : result.rewritten);
            }
            default -> sendRaw(sender, "&#FF0000&lCHAT &8▷ &f/chatfilter <regex|similar|test|reload>");
        }
        return true;
    }

    private Result applyRulesDummy(String message) {
        String[] variants = {message, normalize(message), glue(normalize(message))};
        for (Rule rule : rules) {
            for (String variant : variants) {
                for (Pattern pattern : rule.patterns) {
                    if (pattern.matcher(variant).find()) {
                        return new Result(rule.name, rule.action, true, rule.alert, null, message, "");
                    }
                }
            }
        }
        return null;
    }

    private void regex(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "messages.usage-regex");
            return;
        }
        String word = args[1].toLowerCase(Locale.ROOT);
        long distinct = word.chars().distinct().count();
        if (word.length() < 3 || distinct < 3) {
            send(sender, "messages.regex-invalid");
            return;
        }
        StringBuilder pattern = new StringBuilder("(?<![\\p{L}\\p{N}])");
        for (char c : word.toCharArray()) {
            if (Character.isLetterOrDigit(c)) pattern.append(c).append('+');
        }
        pattern.append("(?![\\p{L}\\p{N}])");
        List<String> existing = blocked.getStringList("rules.PROFANITY.patterns");
        if (existing.contains(pattern.toString())) {
            send(sender, "messages.regex-known", "word", word, "rule", "PROFANITY");
            return;
        }
        existing.add(pattern.toString());
        blocked.set("rules.PROFANITY.patterns", existing);
        Configs.save(blocked, blockedFile);
        loadRules();
        send(sender, "messages.regex-added", "word", word, "rule", "PROFANITY");
    }

    private void history(CommandSender sender, String[] args) {
        try {
            List<String> lines = plugin.toggles().sqlite().query(
                    "SELECT name, message, rule, action FROM chatfilter_log ORDER BY at DESC LIMIT 15",
                    rs -> {
                        List<String> out = new ArrayList<>();
                        try {
                            while (rs.next()) {
                                out.add("&#FF0000" + rs.getString("name") + " &7» &f" + rs.getString("message")
                                        + " &8(" + rs.getString("rule") + ")");
                            }
                        } catch (SQLException ignored) {
                        }
                        return out;
                    });
            if (lines == null || lines.isEmpty()) {
                send(sender, "messages.history-empty");
                return;
            }
            lines.forEach(line -> sendRaw(sender, line));
        } catch (SQLException ex) {
            send(sender, "messages.history-empty");
        }
    }

    private static boolean repeats(String message, int max) {
        int run = 1;
        for (int i = 1; i < message.length(); i++) {
            if (Character.toLowerCase(message.charAt(i)) == Character.toLowerCase(message.charAt(i - 1))) {
                run++;
                if (run >= max) return true;
            } else run = 1;
        }
        return false;
    }

    private static int similarity(String a, String b) {
        String left = a.toLowerCase(Locale.ROOT);
        String right = b.toLowerCase(Locale.ROOT);
        if (left.equals(right)) return 100;
        int max = Math.max(left.length(), right.length());
        if (max == 0) return 100;
        int distance = levenshtein(left, right);
        return (int) Math.round(100.0 * (1.0 - (double) distance / max));
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    static String normalize(String input) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            char lower = Character.toLowerCase(c);
            lower = LEET.getOrDefault(lower, lower);
            if (Character.isLetterOrDigit(lower)) out.append(lower);
            else out.append(' ');
        }
        return out.toString();
    }

    static String glue(String input) {
        String[] parts = input.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.length() <= 1 && !out.isEmpty()) out.append(part);
            else {
                if (!out.isEmpty()) out.append(' ');
                out.append(part);
            }
        }
        return out.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("chatfilter") && args.length == 1) {
            return Tabs.filter(List.of("regex", "similar", "test", "reload"), args[0]);
        }
        return List.of();
    }

    private record Rule(String name, String action, boolean alert, String bypass, List<Pattern> patterns) {
    }

    public static final class Result {
        final String rule;
        final String action;
        final boolean cancel;
        final boolean alert;
        final String rewritten;
        final String original;
        final String playerMessage;
        final boolean check;

        Result(String rule, String action, boolean cancel, boolean alert, String rewritten, String original, String playerMessage) {
            this(rule, action, cancel, alert, rewritten, original, playerMessage, false);
        }

        Result(String rule, String action, boolean cancel, boolean alert, String rewritten, String original, String playerMessage, boolean check) {
            this.rule = rule;
            this.action = action;
            this.cancel = cancel;
            this.alert = alert;
            this.rewritten = rewritten;
            this.original = original;
            this.playerMessage = playerMessage;
            this.check = check;
        }

        static Result check(String rule, String action, String original, String message) {
            return new Result(rule, action, true, false, null, original, message, true);
        }

        static Result rewrite(String rewritten) {
            return new Result("SHOUTING", "LOWERCASE", false, false, rewritten, rewritten, "", true);
        }
    }
}
