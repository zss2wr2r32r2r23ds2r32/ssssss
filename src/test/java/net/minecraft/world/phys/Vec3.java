package net.minecraft.world.phys;

public final class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0d, 0.0d, 0.0d);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
