package com.sharded.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void deliver(CommandSender to, Component component, MessageUtil.Delivery mode) {
      if (mode == null) {
         mode = MessageUtil.Delivery.CHAT;
      }

      if (to instanceof Player player) {
         switch (mode) {
            case ACTIONBAR:
               player.sendActionBar(component);
               break;
            case BOTH:
               player.sendMessage(component);
               player.sendActionBar(component);
               break;
            default:
               to.sendMessage(component);
         }
      } else {
         to.sendMessage(component);
      }
   }

   public static void deliver(CommandSender to, String legacyMessage, MessageUtil.Delivery mode) {
      deliver(to, Text.c(legacyMessage), mode);
   }

   public static enum Delivery {
      CHAT,
      ACTIONBAR,
      BOTH;

      public static MessageUtil.Delivery parse(String raw) {
         if (raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("inherit")) {
            String s = raw.trim().toLowerCase().replace('-', '_');

            return switch (s) {
               case "actionbar", "action_bar" -> ACTIONBAR;
               case "both" -> BOTH;
               default -> CHAT;
            };
         } else {
            return null;
         }
      }
   }
}
