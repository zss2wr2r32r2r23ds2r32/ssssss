package com.sharded.core.modules.chat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public final class ChatToggleModule extends Module implements CommandExecutor {
   public static final String STATE_KEY = "chat-enabled";

   public ChatToggleModule(ShardedCore plugin) {
      super(plugin, "chat");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("chattoggle", this);
      this.registerListener(this);
   }

   public boolean isChatEnabled(Player player) {
      return this.plugin.stateStore().getBool(player.getUniqueId(), "chat-enabled", true);
   }

   public void setChatEnabled(Player player, boolean enabled) {
      this.plugin.stateStore().setBool(player.getUniqueId(), "chat-enabled", enabled);
      this.send(player, enabled ? "chat-enabled" : "chat-disabled", new String[0]);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (args.length > 0 && !args[0].equalsIgnoreCase("toggle")) {
            return true;
         } else if (!player.hasPermission("sharded.chat.toggle")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.setChatEnabled(player, !this.isChatEnabled(player));
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      event.viewers().removeIf(viewer -> !(viewer instanceof Player player) ? false : !this.isChatEnabled(player) && player != event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onMentions(AsyncChatEvent event) {
      if (this.config.getBoolean("mentions.enabled", true)) {
         Component component = event.message();
         String s = PlainTextComponentSerializer.plainText().serialize(component);
         boolean flag = false;

         for (Player player : Bukkit.getOnlinePlayers()) {
            String s1 = player.getName();
            if (s1 != null && !s1.isBlank() && s.contains(s1)) {
               component = this.underlineName(component, s1);
               flag = true;
            }
         }

         if (flag) {
            event.message(component);
         }
      }
   }

   private Component underlineName(Component source, String name) {
      String s = PlainTextComponentSerializer.plainText().serialize(source);
      int i = s.indexOf(name);
      if (i < 0) {
         return source;
      } else {
         if (source instanceof TextComponent textcomponent && textcomponent.children().isEmpty()) {
            String s1 = textcomponent.content();
            int j = s1.indexOf(name);
            if (j < 0) {
               return source;
            }

            Component component = Component.text(s1.substring(0, j)).style(textcomponent.style());
            Component component1 = ((TextComponent)((TextComponent)Component.text(name).style(textcomponent.style())).decorate(TextDecoration.UNDERLINED))
               .clickEvent(ClickEvent.suggestCommand(name));
            Component component2 = this.underlineName(Component.text(s1.substring(j + name.length())).style(textcomponent.style()), name);
            return component.append(component1).append(component2);
         }

         return source;
      }
   }
}
