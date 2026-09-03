package com.shardedcore.eventcore.util;

/**
 * Primitive open-addressed hash set for packed block positions.
 *
 * <p>Player block placements are tracked for the whole duration of an event, so
 * a {@code HashSet<Long>} would box every entry and cost roughly 48 bytes per
 * position plus pointer chasing on every lookup. This implementation stores the
 * keys in a flat {@code long[]} with linear probing, so tracking a placement is
 * a couple of array reads and lookups stay in cache.</p>
 *
 * <p>Not thread safe: all access happens on the main thread from block events.</p>
 */
public final class LongHashSet {

    private static final long EMPTY = 0L;
    private static final float LOAD_FACTOR = 0.70f;

    private long[] keys;
    private int mask;
    private int size;
    private int threshold;
    private boolean containsZero;

    public LongHashSet() {
        this(1024);
    }

    public LongHashSet(int expected) {
        int capacity = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
        this.keys = new long[capacity];
        this.mask = capacity - 1;
        this.threshold = (int) (capacity * LOAD_FACTOR);
    }

    /** Packs a block position the same way vanilla packs {@code BlockPos}. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed << 0 >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static int spread(long value) {
        long h = value * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    public boolean add(long value) {
        if (value == EMPTY) {
            if (containsZero) {
                return false;
            }
            containsZero = true;
            size++;
            return true;
        }
        int index = spread(value) & mask;
        long current;
        while ((current = keys[index]) != EMPTY) {
            if (current == value) {
                return false;
            }
            index = (index + 1) & mask;
        }
        keys[index] = value;
        if (++size >= threshold) {
            grow();
        }
        return true;
    }

    public boolean contains(long value) {
        if (value == EMPTY) {
            return containsZero;
        }
        int index = spread(value) & mask;
        long current;
        while ((current = keys[index]) != EMPTY) {
            if (current == value) {
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    public boolean remove(long value) {
        if (value == EMPTY) {
            if (!containsZero) {
                return false;
            }
            containsZero = false;
            size--;
            return true;
        }
        int index = spread(value) & mask;
        long current;
        while ((current = keys[index]) != EMPTY) {
            if (current == value) {
                shiftAfterRemoval(index);
                size--;
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    /** Backward-shift deletion, which keeps probe chains intact without tombstones. */
    private void shiftAfterRemoval(int pos) {
        while (true) {
            int last = pos;
            pos = (pos + 1) & mask;
            long current;
            while (true) {
                current = keys[pos];
                if (current == EMPTY) {
                    keys[last] = EMPTY;
                    return;
                }
                int ideal = spread(current) & mask;
                if (last <= pos ? (last >= ideal || ideal > pos) : (last >= ideal && ideal > pos)) {
                    break;
                }
                pos = (pos + 1) & mask;
            }
            keys[last] = current;
        }
    }

    private void grow() {
        long[] old = keys;
        int capacity = old.length << 1;
        keys = new long[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
        for (long value : old) {
            if (value == EMPTY) {
                continue;
            }
            int index = spread(value) & mask;
            while (keys[index] != EMPTY) {
                index = (index + 1) & mask;
            }
            keys[index] = value;
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        java.util.Arrays.fill(keys, EMPTY);
        containsZero = false;
        size = 0;
    }

    /** Copies the live keys into a dense array, in unspecified order. */
    public long[] toArray() {
        long[] out = new long[size];
        int cursor = 0;
        if (containsZero) {
            out[cursor++] = EMPTY;
        }
        for (long value : keys) {
            if (value != EMPTY) {
                out[cursor++] = value;
            }
        }
        return out;
    }
}
