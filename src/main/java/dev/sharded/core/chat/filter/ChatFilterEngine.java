package dev.sharded.core.chat.filter;

import dev.sharded.core.util.Similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatFilterEngine {
    private final FilterConfig config;
    private final List<WordRule> rules;

    public ChatFilterEngine(FilterConfig config, List<WordRule> rules) {
        this.config = config;
        this.rules = List.copyOf(rules);
    }

    public FilterConfig config() {
        return config;
    }

    public List<WordRule> rules() {
        return rules;
    }

    public FilterVerdict evaluate(String message, long nowMillis, PlayerFilterState state) {
        String original = message == null ? "" : message;

        if (config.slowmodeEnabled && state.lastMessageAt != PlayerFilterState.NEVER) {
            long waitMs = config.slowmodeSeconds * 1000L;
            long elapsed = nowMillis - state.lastMessageAt;
            if (elapsed < waitMs) {
                int remain = (int) Math.ceil((waitMs - elapsed) / 1000.0);
                return FilterVerdict.block("slowmode", "CANCEL", original, Math.max(1, remain), 0);
            }
        }

        state.lastMessageAt = nowMillis;

        if (config.lengthEnabled && original.length() > config.maxCharacters) {
            return FilterVerdict.block("length", "CANCEL", original, 0, config.maxCharacters);
        }

        if (config.lengthEnabled && Similarity.longestSameInARow(original) > config.maxSameInARow) {
            return FilterVerdict.block("spamming", "CANCEL", original, 0, 0);
        }

        if (config.repeatEnabled && !state.lastMessage.isEmpty() && state.lastRememberedAt > 0) {
            long window = config.rememberSeconds * 1000L;
            if (nowMillis - state.lastRememberedAt <= window) {
                int match = Similarity.percent(state.lastMessage, original);
                if (match >= config.matchPercent) {
                    return FilterVerdict.block("repeat", "CANCEL", original, 0, 0);
                }
            }
        }

        String working = original;
        String shoutingAction = null;
        if (config.shoutingEnabled && Similarity.uppercaseCount(working) > config.maxUppercase) {
            if (config.shoutingAction == FilterConfig.ShoutingAction.CANCEL) {
                return FilterVerdict.block("shouting", "CANCEL", original, 0, 0);
            }
            working = working.toLowerCase(Locale.ROOT);
            shoutingAction = "LOWERCASE";
        }

        FilterVerdict wordHit = null;
        if (config.wordsEnabled) {
            for (WordRule rule : rules) {
                Hit hit = firstHit(working, rule);
                if (hit == null) {
                    continue;
                }
                if (rule.action() == WordRule.Action.CANCEL) {
                    remember(state, original, nowMillis);
                    return FilterVerdict.block(rule.name(), "CANCEL", original, 0, 0);
                }
                working = applyMask(working, rule);
                wordHit = FilterVerdict.modify(rule.name(), "MASK", original, working);
            }
        }

        remember(state, original, nowMillis);

        if (wordHit != null) {
            return FilterVerdict.modify(wordHit.rule(), "MASK", original, working);
        }
        if (shoutingAction != null) {
            return FilterVerdict.modify("shouting", shoutingAction, original, working);
        }
        if (!working.equals(original)) {
            return FilterVerdict.modify("clean", "ALLOW", original, working);
        }
        return FilterVerdict.allow(original);
    }

    public FilterVerdict test(String message) {
        return evaluate(message, System.currentTimeMillis(), new PlayerFilterState());
    }

    public WordRule defaultRule() {
        for (WordRule rule : rules) {
            if ("blocked".equalsIgnoreCase(rule.name())) {
                return rule;
            }
        }
        return rules.isEmpty() ? null : rules.getFirst();
    }

    private void remember(PlayerFilterState state, String message, long nowMillis) {
        state.lastMessage = message;
        state.lastRememberedAt = nowMillis;
    }

    private Hit firstHit(String message, WordRule rule) {
        for (String word : rule.words()) {
            if (word == null || word.isBlank()) {
                continue;
            }
            Pattern pattern = compileSafe(Similarity.antiBypassRegex(word));
            if (pattern != null && pattern.matcher(message).find()) {
                return new Hit(word);
            }
        }
        for (String regex : rule.regex()) {
            Pattern pattern = compileSafe(regex);
            if (pattern != null && pattern.matcher(message).find()) {
                return new Hit(regex);
            }
        }
        return null;
    }

    private String applyMask(String message, WordRule rule) {
        String masked = message;
        for (String word : rule.words()) {
            Pattern pattern = compileSafe(Similarity.antiBypassRegex(word));
            if (pattern != null) {
                masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(config.mask));
            }
        }
        for (String regex : rule.regex()) {
            Pattern pattern = compileSafe(regex);
            if (pattern != null) {
                masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(config.mask));
            }
        }
        return masked;
    }

    private static Pattern compileSafe(String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private record Hit(String token) {
    }

    public List<String> ruleNames() {
        List<String> names = new ArrayList<>();
        for (WordRule rule : rules) {
            names.add(rule.name());
        }
        return names;
    }
}
