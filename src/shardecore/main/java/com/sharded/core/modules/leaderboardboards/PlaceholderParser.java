package com.sharded.core.modules.leaderboardboards;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.OfflinePlayer;

final class PlaceholderParser {
   private static final Pattern WRAPPED = Pattern.compile("%leaderboard_([^%]+)%", 2);
   private static final Pattern FIELD_LAST = Pattern.compile("^(.*?)_?(name|value|uuid|head)_(\\d+)$", 2);
   private static final Pattern POS_THEN_FIELD = Pattern.compile("^(.*)_(\\d+)_(name|value|uuid|head)$", 2);
   private static final Pattern RANK_SELF = Pattern.compile("^(.*?)(?:_top)?_(rank|self|value_self)(?:_shorten)?$", 2);
   private static final Pattern SHORT_HEAD = Pattern.compile("%(?:head|player_head)_(\\d+)%", 2);
   private static final Pattern PLAYER_NAME = Pattern.compile("%player_name%", 2);
   private static final Pattern ALIAS_KILLS = Pattern.compile("%statistic_player_kills%", 2);
   private static final Pattern ALIAS_TOKENS = Pattern.compile("%shardedcore_tokens%", 2);
   private static final Pattern ALIAS_ELO = Pattern.compile("%(?:duels_wins|shardedcore_elo)%", 2);

   private PlaceholderParser() {
   }

   static PlaceholderParser.Request parse(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (s.startsWith("%") && s.endsWith("%") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
         }

         if (s.toLowerCase(Locale.ROOT).startsWith("leaderboard_")) {
            s = s.substring("leaderboard_".length());
         }

         if (s.equalsIgnoreCase("refresh")) {
            return new PlaceholderParser.Request("refresh", "refresh", 0);
         }

         Matcher matcher = POS_THEN_FIELD.matcher(s);
         if (matcher.matches()) {
            return new PlaceholderParser.Request(
               matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(3).toLowerCase(Locale.ROOT), Integer.parseInt(matcher.group(2))
            );
         } else {
            Matcher matcher1 = FIELD_LAST.matcher(s);
            if (matcher1.matches()) {
               String s2 = matcher1.group(1).toLowerCase(Locale.ROOT);
               if (s2.endsWith("_")) {
                  s2 = s2.substring(0, s2.length() - 1);
               }

               return new PlaceholderParser.Request(s2, matcher1.group(2).toLowerCase(Locale.ROOT), Integer.parseInt(matcher1.group(3)));
            } else {
               Matcher matcher2 = RANK_SELF.matcher(s);
               if (matcher2.matches()) {
                  String s1 = matcher2.group(2).toLowerCase(Locale.ROOT);
                  if ("value_self".equals(s1)) {
                     s1 = "self";
                  }

                  return new PlaceholderParser.Request(matcher2.group(1).toLowerCase(Locale.ROOT), s1, 0);
               } else {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   static String replacement(PlaceholderParser.Request request, RankingCache cache, OfflinePlayer viewer, String empty) {
      if (request != null && cache != null) {
         String s = empty == null ? "---" : empty;
         if ("refresh".equals(request.field()) || "refresh".equals(request.key())) {
            return formatRefresh(cache);
         }
         String s1 = BoardStyles.statistic(request.key());
         if ("rank".equals(request.field())) {
            if (viewer != null && viewer.getUniqueId() != null) {
               int i = cache.rankOf(s1, viewer.getUniqueId());
               return i < 1 ? s : String.valueOf(i);
            } else {
               return s;
            }
         } else if ("self".equals(request.field())) {
            if (viewer != null && viewer.getUniqueId() != null) {
               CachedEntry cachedentry = cache.ofPlayer(s1, viewer.getUniqueId());
               return cachedentry == null ? s : cachedentry.getFormattedValue();
            } else {
               return s;
            }
         } else {
            return request.isHead() ? cache.field(s1, "name", request.position()) : cache.field(s1, request.field(), request.position());
         }
      } else {
         return empty == null ? "---" : empty;
      }
   }

   static String resolveLine(String template, RankingCache cache, OfflinePlayer viewer, String empty) {
      if (template != null && !template.isBlank()) {
         String s = empty == null ? "---" : empty;
         Matcher matcher = WRAPPED.matcher(template);
         StringBuffer stringbuffer = new StringBuffer();

         while (matcher.find()) {
            PlaceholderParser.Request placeholderparser$request = parse(matcher.group(1));
            String s1 = replacement(placeholderparser$request, cache, viewer, s);
            matcher.appendReplacement(stringbuffer, Matcher.quoteReplacement(s1 == null ? "" : s1));
         }

         matcher.appendTail(stringbuffer);
         String s2 = stringbuffer.toString();
         String s3 = viewer != null && viewer.getName() != null && !viewer.getName().isBlank() ? viewer.getName() : s;
         s2 = s2.replace("%leaderboard_refresh%", formatRefresh(cache));
         s2 = PLAYER_NAME.matcher(s2).replaceAll(Matcher.quoteReplacement(s3));
         s2 = ALIAS_KILLS.matcher(s2).replaceAll(Matcher.quoteReplacement(replacement(new PlaceholderParser.Request("kills", "self", 0), cache, viewer, s)));
         s2 = ALIAS_TOKENS.matcher(s2).replaceAll(Matcher.quoteReplacement(replacement(new PlaceholderParser.Request("tokens", "self", 0), cache, viewer, s)));
         return ALIAS_ELO.matcher(s2).replaceAll(Matcher.quoteReplacement(replacement(new PlaceholderParser.Request("elo", "self", 0), cache, viewer, s)));
      } else {
         return "";
      }
   }

   static String formatRefresh(RankingCache cache) {
      long ms = cache == null ? 120000L : cache.millisUntilRefresh();
      long total = Math.max(0L, ms / 1000L);
      long minutes = total / 60L;
      long seconds = total % 60L;
      if (minutes > 0L) {
         return minutes + "m " + seconds + "s";
      }
      return seconds + "s";
   }

   static PlaceholderParser.LineSpec line(String raw) {
      if (raw == null) {
         return new PlaceholderParser.LineSpec("", 0);
      } else {
         int i = 0;
         Matcher matcher = WRAPPED.matcher(raw);
         StringBuffer stringbuffer = new StringBuffer();

         while (matcher.find()) {
            PlaceholderParser.Request placeholderparser$request = parse(matcher.group(1));
            if (placeholderparser$request != null && placeholderparser$request.isHead()) {
               if (i <= 0) {
                  i = placeholderparser$request.position();
               }

               matcher.appendReplacement(stringbuffer, "");
            } else {
               matcher.appendReplacement(stringbuffer, Matcher.quoteReplacement(matcher.group()));
            }
         }

         matcher.appendTail(stringbuffer);
         String text = stringbuffer.toString();
         Matcher matcher1 = SHORT_HEAD.matcher(text);

         for (stringbuffer = new StringBuffer(); matcher1.find(); matcher1.appendReplacement(stringbuffer, "")) {
            if (i <= 0) {
               i = Integer.parseInt(matcher1.group(1));
            }
         }

         matcher1.appendTail(stringbuffer);
         text = stringbuffer.toString().replaceAll(" {2,}", " ").strip();
         return new PlaceholderParser.LineSpec(text, i);
      }
   }

   static boolean isSpacer(String raw) {
      if (raw == null) {
         return true;
      } else {
         String s = raw.strip();
         return s.isEmpty() || s.equals("&r") || s.equals("&f") || s.equals("§r");
      }
   }

   static record LineSpec(String text, int headRank) {
      boolean hasHead() {
         return this.headRank > 0;
      }
   }

   static record Request(String key, String field, int position) {
      boolean isHead() {
         return "head".equals(this.field);
      }
   }
}
