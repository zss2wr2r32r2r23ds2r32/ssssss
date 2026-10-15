package dev.sharded.core.util;

import java.util.Locale;

public final class Similarity {
    private Similarity() {
    }

    public static int percent(String left, String right) {
        if (left == null || right == null) {
            return 0;
        }
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 100;
        }
        if (a.equals(b)) {
            return 100;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        return Math.max(dicePercent(a, b), levenshteinPercent(a, b));
    }

    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' && (out.isEmpty() || out.charAt(out.length() - 1) != ' ')) {
                out.append(' ');
            }
        }
        return out.toString().trim();
    }

    public static int uniqueLetterCount(String word) {
        boolean[] seen = new boolean[32];
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (c < 'a' || c > 'z') {
                continue;
            }
            int bit = c - 'a';
            if (!seen[bit]) {
                seen[bit] = true;
                count++;
            }
        }
        return count;
    }

    public static int longestSameInARow(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int best = 1;
        int run = 1;
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == text.charAt(i - 1)) {
                run++;
                if (run > best) {
                    best = run;
                }
            } else {
                run = 1;
            }
        }
        return best;
    }

    public static int uppercaseCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                count++;
            }
        }
        return count;
    }

    public static String antiBypassRegex(String word) {
        StringBuilder pattern = new StringBuilder("(?i)");
        boolean first = true;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                continue;
            }
            if (!first) {
                pattern.append("[\\W_]*");
            }
            first = false;
            pattern.append(leetClass(c));
        }
        return pattern.toString();
    }

    private static String leetClass(char raw) {
        char c = Character.toLowerCase(raw);
        return switch (c) {
            case 'a' -> "[a4@]";
            case 'b' -> "[b8]";
            case 'e' -> "[e3]";
            case 'g' -> "[g6]";
            case 'i' -> "[i1!l]";
            case 'l' -> "[l1!]";
            case 'o' -> "[o0]";
            case 's' -> "[s5$]";
            case 't' -> "[t7]";
            default -> "[" + Character.toLowerCase(c) + Character.toUpperCase(c) + "]";
        };
    }

    private static int dicePercent(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 100 : 0;
        }
        java.util.HashMap<String, Integer> left = bigrams(a);
        java.util.HashMap<String, Integer> right = bigrams(b);
        int overlap = 0;
        int total = 0;
        for (var e : left.entrySet()) {
            total += e.getValue();
            overlap += Math.min(e.getValue(), right.getOrDefault(e.getKey(), 0));
        }
        for (var e : right.entrySet()) {
            total += e.getValue();
        }
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((2.0 * overlap * 100.0) / total);
    }

    private static java.util.HashMap<String, Integer> bigrams(String text) {
        java.util.HashMap<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < text.length() - 1; i++) {
            String g = text.substring(i, i + 2);
            map.merge(g, 1, Integer::sum);
        }
        return map;
    }

    private static int levenshteinPercent(String a, String b) {
        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 100;
        }
        return (int) Math.round((1.0 - (distance / (double) max)) * 100.0);
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    public static String stripLegacy(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)[§&][0-9a-fk-orx]|&#[0-9a-f]{6}", "");
    }

    public static Locale locale() {
        return Locale.US;
    }
}
