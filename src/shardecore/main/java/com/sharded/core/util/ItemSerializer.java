package com.sharded.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemSerializer {
   private ItemSerializer() {
   }

   public static String toBase64(ItemStack[] items) {
      try {
         String s;
         try (ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream()) {
            BukkitObjectOutputStream bukkitobjectoutputstream = new BukkitObjectOutputStream(bytearrayoutputstream);

            try {
               bukkitobjectoutputstream.writeInt(items.length);

               for (ItemStack itemstack : items) {
                  bukkitobjectoutputstream.writeObject(itemstack);
               }

               bukkitobjectoutputstream.flush();
               s = Base64.getEncoder().encodeToString(bytearrayoutputstream.toByteArray());
            } catch (Throwable throwable1) {
               try {
                  bukkitobjectoutputstream.close();
               } catch (Throwable throwable) {
                  throwable1.addSuppressed(throwable);
               }

               throw throwable1;
            }

            bukkitobjectoutputstream.close();
         }

         return s;
      } catch (Exception exception) {
         throw new IllegalStateException("Unable to serialize items", exception);
      }
   }

   public static ItemStack[] fromBase64(String data) {
      if (data != null && !data.isEmpty()) {
         try {
            ItemStack[] aitemstack1;
            try (ByteArrayInputStream bytearrayinputstream = new ByteArrayInputStream(Base64.getDecoder().decode(data))) {
               BukkitObjectInputStream bukkitobjectinputstream = new BukkitObjectInputStream(bytearrayinputstream);

               try {
                  int i = bukkitobjectinputstream.readInt();
                  ItemStack[] aitemstack = new ItemStack[i];

                  for (int j = 0; j < i; j++) {
                     aitemstack[j] = (ItemStack)bukkitobjectinputstream.readObject();
                  }

                  aitemstack1 = aitemstack;
               } catch (Throwable throwable1) {
                  try {
                     bukkitobjectinputstream.close();
                  } catch (Throwable throwable) {
                     throwable1.addSuppressed(throwable);
                  }

                  throw throwable1;
               }

               bukkitobjectinputstream.close();
            }

            return aitemstack1;
         } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize items", exception);
         }
      } else {
         return new ItemStack[0];
      }
   }
}
