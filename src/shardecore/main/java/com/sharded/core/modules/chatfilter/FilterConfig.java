package com.sharded.core.modules.chatfilter;

import java.util.ArrayList;
import java.util.List;

public final class FilterConfig {
   public boolean scanChat = true;
   public boolean scanPrivateMessages = true;
   public List<String> scanCommands = new ArrayList<>();
   public boolean slowmodeEnabled = true;
   public int slowmodeSeconds = 3;
   public boolean npcCooldownEnabled = true;
   public int npcCooldownSeconds = 2;
   public boolean lengthEnabled = true;
   public int maxCharacters = 128;
   public int maxSameInARow = 5;
   public boolean repeatEnabled = true;
   public int rememberSeconds = 30;
   public int matchPercent = 80;
   public boolean shoutingEnabled = true;
   public int maxUppercase = 8;
   public FilterConfig.ShoutingAction shoutingAction = FilterConfig.ShoutingAction.LOWERCASE;
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
   public float soundBlockedVolume = 0.8F;
   public float soundBlockedPitch = 0.8F;
   public boolean actionbar = false;

   public static enum ShoutingAction {
      LOWERCASE,
      CANCEL;

      public static FilterConfig.ShoutingAction parse(String raw) {
         return raw != null && raw.equalsIgnoreCase("CANCEL") ? CANCEL : LOWERCASE;
      }
   }
}
