package com.shardedcore.util;

import java.util.ArrayList;
import java.util.List;

public final class Slots {

    private Slots() {
    }

    public static List<Integer> parse(String spec) {
        List<Integer> slots = new ArrayList<>();
        if (spec == null || spec.isBlank()) return slots;
        for (String part : spec.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash < 0) {
                try {
                    slots.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            try {
                int from = Integer.parseInt(token.substring(0, dash).trim());
                int to = Integer.parseInt(token.substring(dash + 1).trim());
                int step = from <= to ? 1 : -1;
                for (int i = from; i != to + step; i += step) slots.add(i);
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }
}
