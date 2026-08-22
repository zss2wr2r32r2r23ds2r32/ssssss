package com.shardedmc.lobbycore.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NbsDurationParser {

    private static final Pattern GNBS_TICK = Pattern.compile("^\\s*-\\s*(\\d+)!", Pattern.MULTILINE);
    private static final double GNBS_TICKS_PER_SECOND = 657.0;

    private NbsDurationParser() {
    }

    public static int parseDurationSeconds(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        String name = file.getFileName().toString().toLowerCase();

        if (name.endsWith(".gnbs")) {
            return parseGnbs(Files.readString(file));
        }

        if (data.length < 8) {
            throw new IOException("File too small");
        }

        if (data[0] == 0 && data[1] == 0 && data[2] == 5 && data[3] == 16) {
            return parseV5Header(data);
        }

        return parseNamedHeader(data);
    }

    private static int parseGnbs(String content) {
        int cumulative = 0;
        Matcher matcher = GNBS_TICK.matcher(content);
        while (matcher.find()) {
            cumulative += Integer.parseInt(matcher.group(1));
        }
        if (cumulative <= 0) {
            return -1;
        }
        return clampSeconds(cumulative / GNBS_TICKS_PER_SECOND);
    }

    private static int parseV5Header(byte[] data) {
        int ticks = readShort(data, 4);
        if (ticks <= 0) {
            ticks = readShort(data, 6);
        }
        if (ticks <= 0) {
            return -1;
        }
        return clampSeconds(ticks / 20.0);
    }

    private static int parseNamedHeader(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int field0 = buffer.getInt();
        int nameLength = buffer.getInt();

        if (nameLength > 0 && nameLength < 80 && buffer.remaining() >= nameLength) {
            buffer.position(buffer.position() + nameLength);
            skipString(buffer);
            skipString(buffer);
            skipString(buffer);

            if (buffer.remaining() < 2) {
                return fallbackFromField0(field0);
            }

            short tempo = buffer.getShort();
            if (tempo <= 0) {
                return clampSeconds(readShort(data, 0) / 20.0);
            }

            return clampSeconds(field0 / (double) tempo / 20.0);
        }

        return fallbackFromField0(field0);
    }

    private static int fallbackFromField0(int field0) {
        if (field0 <= 0) {
            return -1;
        }
        return clampSeconds(field0 / 5248.0);
    }

    private static void skipString(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int length = buffer.getInt();
        if (length > 0 && length < 500 && buffer.remaining() >= length) {
            buffer.position(buffer.position() + length);
        }
    }

    private static int readShort(byte[] data, int offset) {
        if (offset + 2 > data.length) {
            return 0;
        }
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int clampSeconds(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return -1;
        }
        return Math.max(30, Math.min(600, (int) Math.round(seconds)));
    }
}
