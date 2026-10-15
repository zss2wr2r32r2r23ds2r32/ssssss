package com.sharded.core.net;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AddEntityPacketPatcher {
   private static final String PACKET = "net.minecraft.network.protocol.game.ClientboundAddEntityPacket";
   private static final String BUNDLE = "net.minecraft.network.protocol.game.ClientboundBundlePacket";
   private static final String VEC3 = "net.minecraft.world.phys.Vec3";
   private final Logger log;
   private final Class<?> packetClass;
   private final Class<?> bundleClass;
   private final Object zero;
   private final List<Field> vecFields;
   private final List<Method> bundleIterables;
   private final AddEntityPacketPatcher.FieldSetter setter;
   private long patched;
   private long failed;

   private AddEntityPacketPatcher(
      Logger log,
      Class<?> packetClass,
      Class<?> bundleClass,
      Object zero,
      List<Field> vecFields,
      List<Method> bundleIterables,
      AddEntityPacketPatcher.FieldSetter setter
   ) {
      this.log = log;
      this.packetClass = packetClass;
      this.bundleClass = bundleClass;
      this.zero = zero;
      this.vecFields = vecFields;
      this.bundleIterables = bundleIterables;
      this.setter = setter;
   }

   public static AddEntityPacketPatcher tryCreate(Logger log) {
      try {
         Class<?> oclass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
         Class<?> oclass1 = Class.forName("net.minecraft.world.phys.Vec3");
         Object object = zeroVector(oclass1);
         List<Field> list = vec3Fields(oclass, oclass1);
         if (list.isEmpty()) {
            log.warning("ClientboundAddEntityPacket has no Vec3 field; spawn-packet patcher is inactive.");
            return null;
         } else {
            for (Field field : list) {
               field.setAccessible(true);
            }

            AddEntityPacketPatcher.FieldSetter addentitypacketpatcher$fieldsetter = AddEntityPacketPatcher.FieldSetter.create();
            Class<?> oclass2 = optionalClass("net.minecraft.network.protocol.game.ClientboundBundlePacket");
            List<Method> list1 = oclass2 == null ? List.of() : iterableMethods(oclass2);
            log.info("Spawn-packet patcher ready (null entity velocity will be written as 0,0,0).");
            return new AddEntityPacketPatcher(log, oclass, oclass2, object, list, list1, addentitypacketpatcher$fieldsetter);
         }
      } catch (Throwable throwable) {
         log.log(Level.WARNING, "Could not install spawn-packet patcher. Players may still be kicked by null add_entity velocity.", throwable);
         return null;
      }
   }

   public Object patch(Object msg) {
      if (msg == null) {
         return null;
      } else {
         try {
            if (this.packetClass.isInstance(msg)) {
               this.patchAddEntity(msg);
               return msg;
            }

            if (this.bundleClass != null && this.bundleClass.isInstance(msg)) {
               this.patchBundle(msg);
            }
         } catch (Throwable throwable) {
            this.failed++;
            if (this.failed <= 5L || this.failed % 100L == 0L) {
               this.log.log(Level.WARNING, "Failed to patch add_entity packet (" + this.failed + " failures)", throwable);
            }
         }

         return msg;
      }
   }

   public long patchedCount() {
      return this.patched;
   }

   private void patchBundle(Object bundle) throws Exception {
      for (Method method : this.bundleIterables) {
         Object object = method.invoke(bundle);
         if (object instanceof Iterable) {
            for (Object object1 : (Iterable)object) {
               if (this.packetClass.isInstance(object1)) {
                  this.patchAddEntity(object1);
               }
            }
         }
      }
   }

   private void patchAddEntity(Object packet) throws Exception {
      boolean flag = false;

      for (Field field : this.vecFields) {
         if (field.get(packet) == null) {
            this.setter.set(field, packet, this.zero);
            flag = true;
         }
      }

      if (flag) {
         this.patched++;
         if (this.patched <= 8L || this.patched % 50L == 0L) {
            this.log
               .warning(
                  "Patched null velocity on add_entity (count="
                     + this.patched
                     + "). A plugin sent a 1.21.9+ spawn packet without movement; this is what kicks players."
               );
         }
      }
   }

   private static Object zeroVector(Class<?> vec3) throws Exception {
      try {
         return vec3.getField("ZERO").get(null);
      } catch (ReflectiveOperationException reflectiveoperationexception) {
         Constructor<?> constructor = vec3.getConstructor(double.class, double.class, double.class);
         return constructor.newInstance(0.0, 0.0, 0.0);
      }
   }

   private static List<Field> vec3Fields(Class<?> packetClass, Class<?> vec3) {
      List<Field> list = new ArrayList<>();

      for (Class<?> oclass = packetClass; oclass != null && oclass != Object.class; oclass = oclass.getSuperclass()) {
         for (Field field : oclass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == vec3) {
               list.add(field);
            }
         }
      }

      return list;
   }

   private static List<Method> iterableMethods(Class<?> bundleClass) {
      List<Method> list = new ArrayList<>();

      for (Method method : bundleClass.getMethods()) {
         if (method.getParameterCount() == 0 && method.getDeclaringClass() != Object.class && Iterable.class.isAssignableFrom(method.getReturnType())) {
            list.add(method);
         }
      }

      return list;
   }

   private static Class<?> optionalClass(String name) {
      try {
         return Class.forName(name);
      } catch (ClassNotFoundException classnotfoundexception) {
         return null;
      }
   }

   @FunctionalInterface
   private interface FieldSetter {
      void set(Field var1, Object var2, Object var3) throws Exception;

      static AddEntityPacketPatcher.FieldSetter create() {
         AddEntityPacketPatcher.FieldSetter addentitypacketpatcher$fieldsetter = unsafeSetter();
         return addentitypacketpatcher$fieldsetter != null ? addentitypacketpatcher$fieldsetter : (field, target, value) -> {
            field.setAccessible(true);

            try {
               field.set(target, value);
            } catch (IllegalAccessException illegalaccessexception) {
               VarHandle varhandle = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(field);
               varhandle.set((Object)target, (Object)value);
            }
         };
      }

      static AddEntityPacketPatcher.FieldSetter unsafeSetter() {
         try {
            Class<?> oclass = Class.forName("sun.misc.Unsafe");
            Field field = oclass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object object = field.get(null);
            Method method = oclass.getMethod("objectFieldOffset", Field.class);
            Method method1 = oclass.getMethod("putObject", Object.class, long.class, Object.class);
            return (fieldx, target, value) -> method1.invoke(object, target, method.invoke(object, fieldx), value);
         } catch (Exception exception) {
            return null;
         }
      }
   }
}
