package com.shardedcore.util;

import org.bukkit.configuration.ConfigurationSection;

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

    public static List<Integer> of(ConfigurationSection section, String path) {
        if (section == null) return List.of();
        if (section.isList(path)) {
            List<Integer> slots = new ArrayList<>();
            List<?> raw = section.getList(path);
            if (raw == null) return slots;
            for (Object value : raw) {
                if (value instanceof Number number) slots.add(number.intValue());
                else slots.addAll(parse(String.valueOf(value)));
            }
            return slots;
        }
        return parse(section.getString(path, ""));
    }
}
