package com.sharded.core.net;

import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

public final class ChannelLookup {
   private ChannelLookup() {
   }

   public static Channel of(Player player) {
      if (player == null) {
         return null;
      } else {
         try {
            Object object = player.getClass().getMethod("getHandle").invoke(player);
            Object object1 = named(object, "connection");
            if (object1 == null) {
               return null;
            } else {
               Channel channel = asChannel(named(object1, "channel"));
               if (channel != null) {
                  return channel;
               } else {
                  Object object2 = named(object1, "connection");
                  if (object2 == null) {
                     object2 = object1;
                  }

                  return asChannel(named(object2, "channel"));
               }
            }
         } catch (Exception exception) {
            return null;
         }
      }
   }

   private static Channel asChannel(Object value) {
      return value instanceof Channel channel ? channel : null;
   }

   private static Object named(Object owner, String name) throws IllegalAccessException {
      for (Class<?> oclass = owner.getClass(); oclass != null && oclass != Object.class; oclass = oclass.getSuperclass()) {
         for (Field field : oclass.getDeclaredFields()) {
            if (field.getName().equals(name)) {
               field.setAccessible(true);
               Object object = field.get(owner);
               if (object != null) {
                  return object;
               }
            }
         }

         for (Method method : oclass.getMethods()) {
            if (method.getParameterCount() == 0) {
               String s = method.getName();
               if (s.equals(name) || s.equalsIgnoreCase("get" + name)) {
                  try {
                     Object object1 = method.invoke(owner);
                     if (object1 != null) {
                        return object1;
                     }
                  } catch (Exception exception) {
                  }
               }
            }
         }
      }

      return null;
   }
}
