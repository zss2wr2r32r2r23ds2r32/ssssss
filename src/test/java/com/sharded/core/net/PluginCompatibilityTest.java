package com.sharded.core.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCompatibilityTest {
    @Test
    void flagsLegacyProtocolLibBuilds() {
        assertTrue(PluginCompatibility.looksOldProtocolLib("5.3.0"));
        assertTrue(PluginCompatibility.looksOldProtocolLib("5.4.0-SNAPSHOT"));
        assertTrue(PluginCompatibility.looksOldProtocolLib("4.8.0"));
        assertFalse(PluginCompatibility.looksOldProtocolLib("5.5.0-SNAPSHOT-5a9afed"));
        assertFalse(PluginCompatibility.looksOldProtocolLib("5.4.1"));
        assertFalse(PluginCompatibility.looksOldProtocolLib(null));
    }
}
