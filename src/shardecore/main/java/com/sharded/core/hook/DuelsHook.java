package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class DuelsHook {
   private final ShardedCore plugin;
   private boolean available;
   private Plugin duelsPlugin;

   public DuelsHook(ShardedCore plugin) {
      this.plugin = plugin;

      try {
         Plugin pluginx = Bukkit.getPluginManager().getPlugin("Duels");
         if (pluginx != null && pluginx.isEnabled()) {
            Class.forName("me.realized.duels.DuelsPlugin");
            this.duelsPlugin = pluginx;
            this.available = true;
            plugin.getLogger().info("Hooked into Realized Duels.");
         }
      } catch (Throwable throwable) {
         this.available = false;
      }

      if (!this.available) {
         plugin.getLogger().info("Realized Duels not found — install Duels for /duel and /queue.");
      }
   }

   public boolean isAvailable() {
      return this.available;
   }

   public void refreshCommands() {
      if (this.available) {
         try {
            Field field = this.duelsPlugin.getClass().getDeclaredField("commands");
            field.setAccessible(true);
            Map<String, Object> map = (Map<String, Object>)field.get(this.duelsPlugin);

            for (String s : List.of("duel", "queue", "duels", "spectate")) {
               Object object = map.get(s);
               if (object != null) {
                  Method method = object.getClass().getMethod("register");
                  method.invoke(object);
               }
            }
         } catch (Throwable throwable) {
            this.plugin.getLogger().warning("Failed to refresh Realized Duels commands: " + throwable.getMessage());
         }
      }
   }

   public void openQueue(Player player) {
      if (!this.available) {
         player.sendMessage("Duels is not available on this server.");
      } else {
         this.dispatch("queue", player, "queue", new String[0]);
      }
   }

   private void dispatch(String commandName, CommandSender sender, String label, String[] args) {
      if (this.available) {
         try {
            Field field = this.duelsPlugin.getClass().getDeclaredField("commands");
            field.setAccessible(true);
            Map<String, Object> map = (Map<String, Object>)field.get(this.duelsPlugin);
            Object object = map.get(commandName.toLowerCase());
            if (object == null) {
               return;
            }

            this.invokeRegisteredExecutor(object, sender, label, args);
         } catch (Throwable throwable) {
            this.plugin.getLogger().warning("Failed to dispatch Duels command '" + commandName + "': " + throwable.getMessage());
         }
      }
   }

   private void invokeRegisteredExecutor(Object abstractCommand, CommandSender sender, String label, String[] args) throws ReflectiveOperationException {
      Method method = abstractCommand.getClass().getMethod("isPlayerOnly");
      Method method1 = abstractCommand.getClass().getMethod("getPermission");
      if (Boolean.TRUE.equals(method.invoke(abstractCommand)) && !(sender instanceof Player)) {
         sender.sendMessage("This command can only be executed by a player.");
      } else {
         String s = (String)method1.invoke(abstractCommand);
         if (s != null && !sender.hasPermission(s)) {
            sender.sendMessage("You need the following permission: " + s);
         } else {
            Method method2 = findDeclaredMethod(abstractCommand.getClass(), "executeFirst", CommandSender.class, String.class, String[].class);
            method2.setAccessible(true);
            if (!Boolean.TRUE.equals(method2.invoke(abstractCommand, sender, label, args))) {
               Method method3 = findDeclaredMethod(abstractCommand.getClass(), "execute", CommandSender.class, String.class, String[].class);
               method3.setAccessible(true);
               method3.invoke(abstractCommand, sender, label, args);
            }
         }
      }
   }

   private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
      for (Class<?> oclass = type; oclass != null; oclass = oclass.getSuperclass()) {
         try {
            return oclass.getDeclaredMethod(name, params);
         } catch (NoSuchMethodException nosuchmethodexception) {
         }
      }

      throw new NoSuchMethodException(name);
   }
}
