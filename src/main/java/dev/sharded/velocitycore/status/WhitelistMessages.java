package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.charset.StandardCharsets;

public final class WhitelistMessages {

    public static final byte REQUEST = 0x01;

    private WhitelistMessages() {
    }

    public static boolean isRequest(byte[] data) {
        return data != null && data.length == 1 && data[0] == REQUEST;
    }

    public static Report decode(byte[] data) {
        ByteArrayDataInput input = ByteStreams.newDataInput(data);
        int length = input.readInt();
        byte[] nameBytes = new byte[length];
        input.readFully(nameBytes);
        String serverName = new String(nameBytes, StandardCharsets.UTF_8);
        boolean whitelisted = input.readBoolean();
        return new Report(serverName, whitelisted);
    }

    public static byte[] encode(String serverName, boolean whitelisted) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        byte[] nameBytes = serverName.getBytes(StandardCharsets.UTF_8);
        output.writeInt(nameBytes.length);
        output.write(nameBytes);
        output.writeBoolean(whitelisted);
        return output.toByteArray();
    }

    public record Report(String serverName, boolean whitelisted) {
    }
}
