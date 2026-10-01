package org.wgx.advancedarmor.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of a VS ship transform. VS embeds its core API in a nested jar, so the tiny
 * reflective bridge uses that public interface without adding another mod or packing its API.
 * All traversal still reads/writes the original shipyard BlockPos from the same Level.
 */
public record ShipSpace(Matrix4dc toWorld, Matrix4dc toLocal) {
    public static final ShipSpace WORLD = new ShipSpace(new Matrix4d(), new Matrix4d());

    private static final class Api {
        static final MethodHandle MANAGING;
        static final MethodHandle NEARBY;
        static final MethodHandle TO_WORLD;
        static final MethodHandle TO_LOCAL;
        static {
            try {
                Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                Class<?> ship = Class.forName("org.valkyrienskies.core.api.ships.Ship");
                // A Class.getMethod call resolves all VS overload signatures, including ClientLevel,
                // which fails on dedicated servers. MethodHandles resolve only this exact signature.
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MANAGING = lookup.findStatic(utils, "getShipManagingPos", MethodType.methodType(ship, Level.class, BlockPos.class));
                NEARBY = lookup.findStatic(utils, "getShipsIntersecting", MethodType.methodType(Iterable.class, Level.class, AABB.class));
                TO_WORLD = lookup.findVirtual(ship, "getShipToWorld", MethodType.methodType(Matrix4dc.class));
                TO_LOCAL = lookup.findVirtual(ship, "getWorldToShip", MethodType.methodType(Matrix4dc.class));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Valkyrien Skies public transform API is unavailable", ex);
            }
        }
    }

    public static ShipSpace at(Level level, BlockPos pos) {
        Object ship = invoke(Api.MANAGING, level, pos);
        return ship == null ? WORLD : of(ship);
    }

    public static List<ShipSpace> nearby(Level level, Vec3 origin, double distance) {
        List<ShipSpace> spaces = new ArrayList<>();
        spaces.add(WORLD);
        Iterable<?> ships = (Iterable<?>) invoke(Api.NEARBY, level,
                    new AABB(origin.x - distance, origin.y - distance, origin.z - distance,
                            origin.x + distance, origin.y + distance, origin.z + distance));
        for (Object ship : ships) spaces.add(of(ship));
        return spaces;
    }

    private static ShipSpace of(Object ship) {
        return new ShipSpace(new Matrix4d((Matrix4dc) invoke(Api.TO_WORLD, ship)),
                new Matrix4d((Matrix4dc) invoke(Api.TO_LOCAL, ship)));
    }

    private static Object invoke(MethodHandle method, Object... arguments) {
        try { return method.invokeWithArguments(arguments); }
        catch (Error error) { throw error; }
        catch (Throwable exception) { throw new IllegalStateException("Valkyrien Skies transform query failed", exception); }
    }

    public Vec3 localPosition(Vec3 vector) { return transform(toLocal, vector, true); }
    public Vec3 worldPosition(Vec3 vector) { return transform(toWorld, vector, true); }
    public Vec3 localDirection(Vec3 vector) { return transform(toLocal, vector, false); }
    public Vec3 worldDirection(Vec3 vector) { return transform(toWorld, vector, false); }

    private static Vec3 transform(Matrix4dc matrix, Vec3 vector, boolean position) {
        Vector3d result = new Vector3d(vector.x, vector.y, vector.z);
        if (position) matrix.transformPosition(result); else matrix.transformDirection(result);
        return new Vec3(result.x, result.y, result.z);
    }
}
