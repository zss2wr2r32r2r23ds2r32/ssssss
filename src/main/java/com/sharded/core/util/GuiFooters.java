package com.sharded.core.util;

/** Shared GUI click footers — only {@code CLICK} is bold + caps. */
public final class GuiFooters {

    public static final String YELLOW = "&x&F&F&B&A&0&0";
    public static final String RED = "&x&F&F&2&7&2&7";
    public static final String GREEN = "&x&9&F&F&0&0&0";

    private GuiFooters() {
    }

    public static String click(String color, String action) {
        return color + "▷ " + color + "&l&nCLICK&r " + color + action;
    }

    public static String yellow(String action) {
        return click(YELLOW, action);
    }

    public static String confirm() {
        return yellow("To Confirm");
    }

    public static String cancel() {
        return yellow("To Cancel");
    }

    public static String apply() {
        return yellow("To Apply");
    }

    public static String create() {
        return yellow("To Create");
    }

    public static String view() {
        return yellow("To View");
    }

    public static String navigate() {
        return yellow("To Navigate");
    }

    public static String close() {
        return yellow("To Close");
    }
}
