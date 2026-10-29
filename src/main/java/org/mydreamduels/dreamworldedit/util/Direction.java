package org.mydreamduels.dreamworldedit.util;

import org.bukkit.Location;

import java.util.List;
import java.util.Locale;

/** The six axis directions, parsed WorldEdit-style ("me" = where the player looks). */
public enum Direction {
    NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);

    public final int dx, dy, dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public BlockVec vec() {
        return new BlockVec(dx, dy, dz);
    }

    public static final List<String> NAMES = List.of("me", "north", "south", "east", "west", "up", "down", "forward", "back", "left", "right");

    public static Direction facing(Location loc) {
        float pitch = loc.getPitch();
        if (pitch < -67.5f) return UP;
        if (pitch > 67.5f) return DOWN;
        float yaw = ((loc.getYaw() % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return SOUTH;
        if (yaw < 135) return WEST;
        if (yaw < 225) return NORTH;
        return EAST;
    }

    private Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    private Direction rightOf() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }

    /** null if the text isn't a direction. */
    public static Direction parse(String text, Location viewer) {
        Direction look = facing(viewer);
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "me", "forward", "f" -> look;
            case "back", "b" -> look.opposite();
            case "right", "r" -> look.rightOf();
            case "left", "l" -> look.rightOf().opposite();
            case "north", "n" -> NORTH;
            case "south", "s" -> SOUTH;
            case "east", "e" -> EAST;
            case "west", "w" -> WEST;
            case "up", "u" -> UP;
            case "down", "d" -> DOWN;
            default -> null;
        };
    }
}
