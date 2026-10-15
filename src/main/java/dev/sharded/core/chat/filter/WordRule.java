package dev.sharded.core.chat.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WordRule {
    private final String name;
    private final Action action;
    private final List<String> words;
    private final List<String> regex;

    public WordRule(String name, Action action, List<String> words, List<String> regex) {
        this.name = name;
        this.action = action;
        this.words = new ArrayList<>(words);
        this.regex = new ArrayList<>(regex);
    }

    public String name() {
        return name;
    }

    public Action action() {
        return action;
    }

    public List<String> words() {
        return words;
    }

    public List<String> regex() {
        return regex;
    }

    public boolean containsWord(String word) {
        String needle = word.toLowerCase(Locale.ROOT);
        for (String known : words) {
            if (known.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        for (String known : regex) {
            if (known.equalsIgnoreCase(needle) || known.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    public enum Action {
        MASK,
        CANCEL;

        public static Action parse(String raw) {
            if (raw != null && raw.equalsIgnoreCase("MASK")) {
                return MASK;
            }
            return CANCEL;
        }
    }
}
