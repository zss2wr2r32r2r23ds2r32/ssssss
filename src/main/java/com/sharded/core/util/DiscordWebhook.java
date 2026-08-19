package com.sharded.core.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends Discord webhook embeds asynchronously (best-effort). */
public final class DiscordWebhook {

    public record Field(String name, String value, boolean inline) {
    }

    private DiscordWebhook() {
    }

    public static void sendAsync(Logger logger, String webhookUrl, String title, String description, int colorRgb) {
        sendEmbedAsync(logger, webhookUrl, title, description, colorRgb, null, null, List.of());
    }

    public static void sendEmbedAsync(Logger logger, String webhookUrl, String title, String description,
                                      int colorRgb, String thumbnailUrl, String footer, List<Field> fields) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        String payload = buildPayload(title, description, colorRgb, thumbnailUrl, footer, fields);
        Thread.ofVirtual().start(() -> post(logger, webhookUrl, payload));
    }

    private static void post(Logger logger, String webhookUrl, String payload) {
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
    }

    private static String buildPayload(String title, String description, int colorRgb,
                                       String thumbnailUrl, String footer, List<Field> fields) {
        StringBuilder embed = new StringBuilder("{");
        embed.append("\"title\":\"").append(escape(title)).append("\"");
        embed.append(",\"description\":\"").append(escape(description)).append("\"");
        embed.append(",\"color\":").append(colorRgb);
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            embed.append(",\"thumbnail\":{\"url\":\"").append(escape(thumbnailUrl)).append("\"}");
        }
        if (footer != null && !footer.isBlank()) {
            embed.append(",\"footer\":{\"text\":\"").append(escape(footer)).append("\"}");
        }
        if (fields != null && !fields.isEmpty()) {
            embed.append(",\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (i > 0) embed.append(',');
                embed.append("{\"name\":\"").append(escape(field.name())).append("\"");
                embed.append(",\"value\":\"").append(escape(field.value())).append("\"");
                embed.append(",\"inline\":").append(field.inline()).append('}');
            }
            embed.append(']');
        }
        embed.append('}');
        return "{\"embeds\":[" + embed + "]}";
    }

    private static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
