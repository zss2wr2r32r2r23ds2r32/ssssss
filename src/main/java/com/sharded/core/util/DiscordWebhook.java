package com.sharded.core.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends Discord webhook embeds asynchronously (best-effort). */
public final class DiscordWebhook {

    private DiscordWebhook() {
    }

    public static void sendAsync(Logger logger, String webhookUrl, String title, String description, int colorRgb) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        String payload = buildPayload(title, description, colorRgb);
        Thread.ofVirtual().start(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
                connection.getInputStream().close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Discord webhook failed: " + e.getMessage());
            }
        });
    }

    private static String buildPayload(String title, String description, int colorRgb) {
        String safeTitle = escape(title);
        String safeDesc = escape(description);
        return """
                {"embeds":[{"title":"%s","description":"%s","color":%d}]}
                """.formatted(safeTitle, safeDesc, colorRgb).trim();
    }

    private static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
