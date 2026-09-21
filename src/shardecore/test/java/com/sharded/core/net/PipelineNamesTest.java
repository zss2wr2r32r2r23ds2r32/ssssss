package com.sharded.core.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PipelineNamesTest {
    @Test
    void prefersVanillaEncoderName() {
        assertEquals("encoder", PipelineNames.encoderName(List.of("timeout", "splitter", "decoder", "encoder", "packet_handler")));
    }

    @Test
    void usesOutboundConfigDuringProtocolSwitch() {
        assertEquals("outbound_config", PipelineNames.encoderName(List.of("timeout", "outbound_config", "packet_handler")));
    }

    @Test
    void findsEncoderBySubstring() {
        assertEquals("packet_encoder", PipelineNames.encoderName(List.of("timeout", "packet_encoder", "handler")));
    }

    @Test
    void emptyPipelineHasNoEncoder() {
        assertNull(PipelineNames.encoderName(List.of()));
        assertNull(PipelineNames.encoderName(null));
    }
}
