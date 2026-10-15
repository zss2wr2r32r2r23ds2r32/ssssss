package com.sharded.core.modules.chatfilter;

public final class FilterVerdict {
   private final FilterVerdict.Status status;
   private final String rule;
   private final String action;
   private final String original;
   private final String result;
   private final int waitSeconds;
   private final int maxCharacters;

   private FilterVerdict(FilterVerdict.Status status, String rule, String action, String original, String result, int waitSeconds, int maxCharacters) {
      this.status = status;
      this.rule = rule;
      this.action = action;
      this.original = original;
      this.result = result;
      this.waitSeconds = waitSeconds;
      this.maxCharacters = maxCharacters;
   }

   public static FilterVerdict allow(String message) {
      return new FilterVerdict(FilterVerdict.Status.ALLOW, "clean", "ALLOW", message, message, 0, 0);
   }

   public static FilterVerdict modify(String rule, String action, String original, String result) {
      return new FilterVerdict(FilterVerdict.Status.MODIFY, rule, action, original, result, 0, 0);
   }

   public static FilterVerdict block(String rule, String action, String original, int waitSeconds, int maxCharacters) {
      return new FilterVerdict(FilterVerdict.Status.BLOCK, rule, action, original, original, waitSeconds, maxCharacters);
   }

   public FilterVerdict.Status status() {
      return this.status;
   }

   public boolean blocked() {
      return this.status == FilterVerdict.Status.BLOCK;
   }

   public boolean modified() {
      return this.status == FilterVerdict.Status.MODIFY;
   }

   public String rule() {
      return this.rule;
   }

   public String action() {
      return this.action;
   }

   public String original() {
      return this.original;
   }

   public String result() {
      return this.result;
   }

   public int waitSeconds() {
      return this.waitSeconds;
   }

   public int maxCharacters() {
      return this.maxCharacters;
   }

   public boolean isCheck() {
      String s = this.rule;

      return switch (s) {
         case "slowmode", "length", "spamming", "repeat", "shouting" -> true;
         default -> false;
      };
   }

   public static enum Status {
      ALLOW,
      MODIFY,
      BLOCK;
   }
}
