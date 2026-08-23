package dev.sharded.velocitycore.backend;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.charset.StandardCharsets;

public final class WhitelistMessages {

    public static final String CHANNEL = "shardedvelocitycore:whitelist";

    private WhitelistMessages() {
    }

    public static byte[] encode(String serverName, boolean whitelisted) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        byte[] nameBytes = serverName.getBytes(StandardCharsets.UTF_8);
        output.writeInt(nameBytes.length);
        output.write(nameBytes);
        output.writeBoolean(whitelisted);
        return output.toByteArray();
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

    public record Report(String serverName, boolean whitelisted) {
    }
}
