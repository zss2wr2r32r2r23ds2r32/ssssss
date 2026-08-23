package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MaintenanceMessages {

    private MaintenanceMessages() {
    }

    public static byte[] encodeLegacy(boolean enabled, String maintenanceMotd, String versionText, int protocolVersion) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeBoolean(enabled);
        writeString(output, maintenanceMotd);
        writeString(output, versionText);
        output.writeInt(protocolVersion);
        return output.toByteArray();
    }

    public static Sync decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("empty");
        }
        if (data[0] == 0x01) {
            throw new IllegalArgumentException("whitelist request");
        }
        ByteArrayDataInput input = ByteStreams.newDataInput(data);
        if (data.length > 1 && (data[0] == 2 || data[0] == (byte) 2)) {
            input.readByte();
            boolean enabled = input.readBoolean();
            List<String> normalLines = readLines(input);
            String normalIcon = readString(input);
            List<String> maintenanceLines = readLines(input);
            String maintenanceIcon = readString(input);
            String versionText = readString(input);
            int protocolVersion = input.readInt();
            return new Sync(
                    enabled,
                    toComponent(normalLines),
                    normalIcon,
                    toComponent(maintenanceLines),
                    maintenanceIcon,
                    versionText,
                    protocolVersion
            );
        }

        boolean enabled = input.readBoolean();
        String maintenanceMotd = readString(input);
        String versionText = readString(input);
        int protocolVersion = input.readInt();
        return new Sync(
                enabled,
                Component.empty(),
                "",
                LegacyText.parse(maintenanceMotd),
                "",
                versionText,
                protocolVersion
        );
    }

    private static Component toComponent(List<String> lines) {
        if (lines.isEmpty()) {
            return Component.empty();
        }
        Component result = LegacyText.parse(lines.getFirst());
        for (int i = 1; i < lines.size(); i++) {
            result = result.append(Component.newline()).append(LegacyText.parse(lines.get(i)));
        }
        return result;
    }

    private static List<String> readLines(ByteArrayDataInput input) {
        int count = input.readInt();
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(readString(input));
        }
        return lines;
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

    public record Sync(
            boolean maintenanceEnabled,
            Component normalMotd,
            String normalIcon,
            Component maintenanceMotd,
            String maintenanceIcon,
            String versionText,
            int protocolVersion
    ) {
    }
}
