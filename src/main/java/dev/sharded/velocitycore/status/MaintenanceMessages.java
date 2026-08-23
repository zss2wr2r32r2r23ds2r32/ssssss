package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MaintenanceMessages {

    private MaintenanceMessages() {
    }

    public static Sync decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("empty");
        }
        if (data[0] == 0x01) {
            throw new IllegalArgumentException("whitelist request");
        }

        ByteArrayDataInput input = ByteStreams.newDataInput(data);
        byte first = input.readByte();

        if (first == 3) {
            boolean enabled = input.readBoolean();
            List<String> motdLines = readLines(input);
            String icon = readString(input);
            String versionText = readString(input);
            int protocolVersion = input.readInt();
            boolean hoverEnabled = input.readBoolean();
            List<String> hoverMessages = readLines(input);
            return new Sync(
                    enabled,
                    toComponent(motdLines),
                    icon,
                    versionText,
                    protocolVersion,
                    hoverEnabled,
                    hoverMessages
            );
        }

        if (first == 2) {
            boolean enabled = input.readBoolean();
            List<String> normalLines = readLines(input);
            readString(input);
            readLines(input);
            readString(input);
            String versionText = readString(input);
            int protocolVersion = input.readInt();
            return new Sync(
                    enabled,
                    toComponent(normalLines),
                    "",
                    versionText,
                    protocolVersion,
                    false,
                    List.of()
            );
        }

        boolean enabled = first != 0;
        String maintenanceMotd = readString(input);
        String versionText = readString(input);
        int protocolVersion = input.readInt();
        return new Sync(
                enabled,
                LegacyText.parse(maintenanceMotd),
                "",
                versionText,
                protocolVersion,
                false,
                List.of()
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

    private static String readString(ByteArrayDataInput input) {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record Sync(
            boolean maintenanceEnabled,
            Component motd,
            String icon,
            String versionText,
            int protocolVersion,
            boolean hoverEnabled,
            List<String> hoverMessages
    ) {
    }
}
