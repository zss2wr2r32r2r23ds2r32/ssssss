package dev.sharded.core.chat.filter;

import java.util.ArrayList;
import java.util.List;

public final class FilterConfig {
    public boolean scanChat = true;
    public boolean scanPrivateMessages = true;
    public List<String> scanCommands = new ArrayList<>();

    public boolean slowmodeEnabled = true;
    public int slowmodeSeconds = 3;

    public boolean lengthEnabled = true;
    public int maxCharacters = 128;
    public int maxSameInARow = 5;

    public boolean repeatEnabled = true;
    public int rememberSeconds = 30;
    public int matchPercent = 80;

    public boolean shoutingEnabled = true;
    public int maxUppercase = 8;
    public ShoutingAction shoutingAction = ShoutingAction.LOWERCASE;

    public boolean wordsEnabled = true;
    public String mask = "***";

    public boolean alertsEnabled = true;
    public boolean alertChecks = false;
    public String alertFormat = "&#FF0000&lFILTER &7▷ &f%player% &7» &f%message% &8(%rule% %action%)";

    public boolean logEnabled = true;
    public boolean logRulesOnly = true;
    public int keepDays = 14;

    public boolean soundBlockedEnabled = true;
    public String soundBlocked = "block.note_block.bass";
    public float soundBlockedVolume = 0.8f;
    public float soundBlockedPitch = 0.8f;

    public boolean soundClickEnabled = true;
    public String soundClick = "ui.button.click";
    public float soundClickVolume = 1.0f;
    public float soundClickPitch = 1.2f;

    public boolean actionbar = false;

    public enum ShoutingAction {
        LOWERCASE,
        CANCEL;

        public static ShoutingAction parse(String raw) {
            if (raw != null && raw.equalsIgnoreCase("CANCEL")) {
                return CANCEL;
            }
            return LOWERCASE;
        }
    }
}
