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

/**
 * Paper 1.21.9+ encodes {@code ClientboundAddEntityPacket} velocity with {@code LpVec3},
 * which NPEs when the vector is null. ProtocolLib, eGlow, hologram plugins, and packet NPCs
 * often reconstruct that packet without setting movement, which kicks the player.
 */
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
    private final FieldSetter setter;
    private long patched;
    private long failed;

    private AddEntityPacketPatcher(
            Logger log,
            Class<?> packetClass,
            Class<?> bundleClass,
            Object zero,
            List<Field> vecFields,
            List<Method> bundleIterables,
            FieldSetter setter
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
            Class<?> packetClass = Class.forName(PACKET);
            Class<?> vec3 = Class.forName(VEC3);
            Object zero = zeroVector(vec3);
            List<Field> vecFields = vec3Fields(packetClass, vec3);
            if (vecFields.isEmpty()) {
                log.warning("ClientboundAddEntityPacket has no Vec3 field; spawn-packet patcher is inactive.");
                return null;
            }
            for (Field field : vecFields) {
                field.setAccessible(true);
            }
            FieldSetter setter = FieldSetter.create();
            Class<?> bundleClass = optionalClass(BUNDLE);
            List<Method> bundleIterables = bundleClass == null ? List.of() : iterableMethods(bundleClass);
            log.info("Spawn-packet patcher ready (null entity velocity will be written as 0,0,0).");
            return new AddEntityPacketPatcher(log, packetClass, bundleClass, zero, vecFields, bundleIterables, setter);
        } catch (Throwable ex) {
            log.log(Level.WARNING, "Could not install spawn-packet patcher. Players may still be kicked by null add_entity velocity.", ex);
            return null;
        }
    }

    public Object patch(Object msg) {
        if (msg == null) {
            return null;
        }
        try {
            if (packetClass.isInstance(msg)) {
                patchAddEntity(msg);
                return msg;
            }
            if (bundleClass != null && bundleClass.isInstance(msg)) {
                patchBundle(msg);
            }
        } catch (Throwable ex) {
            failed++;
            if (failed <= 5 || failed % 100 == 0) {
                log.log(Level.WARNING, "Failed to patch add_entity packet (" + failed + " failures)", ex);
            }
        }
        return msg;
    }

    public long patchedCount() {
        return patched;
    }

    private void patchBundle(Object bundle) throws Exception {
        for (Method method : bundleIterables) {
            Object value = method.invoke(bundle);
            if (!(value instanceof Iterable<?> iterable)) {
                continue;
            }
            for (Object inner : iterable) {
                if (packetClass.isInstance(inner)) {
                    patchAddEntity(inner);
                }
            }
        }
    }

    private void patchAddEntity(Object packet) throws Exception {
        boolean changed = false;
        for (Field field : vecFields) {
            if (field.get(packet) != null) {
                continue;
            }
            setter.set(field, packet, zero);
            changed = true;
        }
        if (!changed) {
            return;
        }
        patched++;
        if (patched <= 8 || patched % 50 == 0) {
            log.warning("Patched null velocity on add_entity (count=" + patched
                    + "). A plugin sent a 1.21.9+ spawn packet without movement; this is what kicks players.");
        }
    }

    private static Object zeroVector(Class<?> vec3) throws Exception {
        try {
            return vec3.getField("ZERO").get(null);
        } catch (ReflectiveOperationException ignored) {
            Constructor<?> ctor = vec3.getConstructor(double.class, double.class, double.class);
            return ctor.newInstance(0.0d, 0.0d, 0.0d);
        }
    }

    private static List<Field> vec3Fields(Class<?> packetClass, Class<?> vec3) {
        List<Field> fields = new ArrayList<>();
        Class<?> cursor = packetClass;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.getType() == vec3) {
                    fields.add(field);
                }
            }
            cursor = cursor.getSuperclass();
        }
        return fields;
    }

    private static List<Method> iterableMethods(Class<?> bundleClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : bundleClass.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (Iterable.class.isAssignableFrom(method.getReturnType())) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface FieldSetter {
        void set(Field field, Object target, Object value) throws Exception;

        static FieldSetter create() {
            FieldSetter unsafe = unsafeSetter();
            if (unsafe != null) {
                return unsafe;
            }
            return (field, target, value) -> {
                field.setAccessible(true);
                try {
                    field.set(target, value);
                    return;
                } catch (IllegalAccessException ignored) {
                }
                VarHandle handle = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                        .unreflectVarHandle(field);
                handle.set(target, value);
            };
        }

        static FieldSetter unsafeSetter() {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                Object unsafe = theUnsafe.get(null);
                Method offset = unsafeClass.getMethod("objectFieldOffset", Field.class);
                Method put = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
                return (field, target, value) -> put.invoke(unsafe, target, offset.invoke(unsafe, field), value);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
