package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.charset.StandardCharsets;

public final class MaintenanceMessages {

    private MaintenanceMessages() {
    }

    public static byte[] encode(boolean enabled, String maintenanceMotd, String versionText, int protocolVersion) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeBoolean(enabled);
        writeString(output, maintenanceMotd);
        writeString(output, versionText);
        output.writeInt(protocolVersion);
        return output.toByteArray();
    }

    public static Sync decode(byte[] data) {
        ByteArrayDataInput input = ByteStreams.newDataInput(data);
        boolean enabled = input.readBoolean();
        String maintenanceMotd = readString(input);
        String versionText = readString(input);
        int protocolVersion = input.readInt();
        return new Sync(enabled, maintenanceMotd, versionText, protocolVersion);
    }

    private static void writeString(ByteArrayDataOutput output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(ByteArrayDataInput input) {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record Sync(boolean enabled, String maintenanceMotd, String versionText, int protocolVersion) {
    }
}
