package com.sharded.core.modules.chatfilter;

import com.sharded.core.util.Similarity;
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

   public FilterVerdict evaluate(String message, long nowMillis, PlayerFilterState state) {
      String s = message == null ? "" : message;
      if (this.config.slowmodeEnabled && state.lastMessageAt != 0L) {
         long i = (long)this.config.slowmodeSeconds * 1000L;
         long j = nowMillis - state.lastMessageAt;
         if (j < i) {
            int i1 = (int)Math.ceil((double)(i - j) / 1000.0);
            return FilterVerdict.block("slowmode", "CANCEL", s, Math.max(1, i1), 0);
         }
      }

      state.lastMessageAt = nowMillis;
      if (this.config.lengthEnabled && s.length() > this.config.maxCharacters) {
         return FilterVerdict.block("length", "CANCEL", s, 0, this.config.maxCharacters);
      } else if (this.config.lengthEnabled && Similarity.longestSameInARow(s) > this.config.maxSameInARow) {
         return FilterVerdict.block("spamming", "CANCEL", s, 0, 0);
      } else {
         if (this.config.repeatEnabled && !state.lastMessage.isEmpty() && state.lastRememberedAt > 0L) {
            long k = (long)this.config.rememberSeconds * 1000L;
            if (nowMillis - state.lastRememberedAt <= k) {
               int l = Similarity.percent(state.lastMessage, s);
               if (l >= this.config.matchPercent) {
                  return FilterVerdict.block("repeat", "CANCEL", s, 0, 0);
               }
            }
         }

         String s2 = s;
         String s1 = null;
         if (this.config.shoutingEnabled && Similarity.uppercaseCount(s) > this.config.maxUppercase) {
            if (this.config.shoutingAction == FilterConfig.ShoutingAction.CANCEL) {
               return FilterVerdict.block("shouting", "CANCEL", s, 0, 0);
            }

            s2 = s.toLowerCase(Locale.ROOT);
            s1 = "LOWERCASE";
         }

         FilterVerdict filterverdict = null;
         if (this.config.wordsEnabled) {
            for (WordRule wordrule : this.rules) {
               if (this.firstHit(s2, wordrule)) {
                  if (wordrule.action() == WordRule.Action.CANCEL) {
                     this.remember(state, s, nowMillis);
                     return FilterVerdict.block(wordrule.name(), "CANCEL", s, 0, 0);
                  }

                  if (wordrule.action() == WordRule.Action.WARN) {
                     this.remember(state, s, nowMillis);
                     return FilterVerdict.modify(wordrule.name(), "WARN", s, s2);
                  }

                  s2 = this.applyMask(s2, wordrule);
                  filterverdict = FilterVerdict.modify(wordrule.name(), "MASK", s, s2);
               }
            }
         }

         this.remember(state, s, nowMillis);
         if (filterverdict != null) {
            return filterverdict;
         } else if (s1 != null) {
            return FilterVerdict.modify("shouting", s1, s, s2);
         } else {
            return !s2.equals(s) ? FilterVerdict.modify("clean", "ALLOW", s, s2) : FilterVerdict.allow(s);
         }
      }
   }

   public FilterVerdict test(String message) {
      return this.evaluate(message, System.currentTimeMillis(), new PlayerFilterState());
   }

   public WordRule defaultRule() {
      for (WordRule wordrule : this.rules) {
         if ("blocked".equalsIgnoreCase(wordrule.name())) {
            return wordrule;
         }
      }

      return this.rules.isEmpty() ? null : this.rules.getFirst();
   }

   private void remember(PlayerFilterState state, String message, long nowMillis) {
      state.lastMessage = message;
      state.lastRememberedAt = nowMillis;
   }

   private boolean firstHit(String message, WordRule rule) {
      for (String s : rule.words()) {
         if (s != null && !s.isBlank()) {
            Pattern pattern = compileSafe(Similarity.antiBypassRegex(s));
            if (pattern != null && pattern.matcher(message).find()) {
               return true;
            }
         }
      }

      for (String s1 : rule.regex()) {
         Pattern pattern1 = compileSafe(s1);
         if (pattern1 != null && pattern1.matcher(message).find()) {
            return true;
         }
      }

      return false;
   }

   private String applyMask(String message, WordRule rule) {
      String s = message;

      for (String s1 : rule.words()) {
         Pattern pattern = compileSafe(Similarity.antiBypassRegex(s1));
         if (pattern != null) {
            s = pattern.matcher(s).replaceAll(Matcher.quoteReplacement(this.config.mask));
         }
      }

      for (String s2 : rule.regex()) {
         Pattern pattern1 = compileSafe(s2);
         if (pattern1 != null) {
            s = pattern1.matcher(s).replaceAll(Matcher.quoteReplacement(this.config.mask));
         }
      }

      return s;
   }

   private static Pattern compileSafe(String regex) {
      if (regex != null && !regex.isBlank()) {
         try {
            return Pattern.compile(regex);
         } catch (PatternSyntaxException patternsyntaxexception) {
            return null;
         }
      } else {
         return null;
      }
   }

   public List<String> ruleNames() {
      List<String> list = new ArrayList<>();

      for (WordRule wordrule : this.rules) {
         list.add(wordrule.name());
      }

      return list;
   }
}
