package com.sharded.core.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DiscordWebhook {
   private DiscordWebhook() {
   }

   public static void sendAsync(Logger logger, String webhookUrl, String title, String description, int colorRgb) {
      sendEmbedAsync(logger, webhookUrl, title, description, colorRgb, null, null, List.of());
   }

   public static void sendEmbedAsync(
      Logger logger, String webhookUrl, String title, String description, int colorRgb, String thumbnailUrl, String footer, List<DiscordWebhook.Field> fields
   ) {
      if (webhookUrl != null && !webhookUrl.isBlank()) {
         String s = buildPayload(title, description, colorRgb, thumbnailUrl, footer, fields);
         Thread.ofVirtual().start(() -> post(logger, webhookUrl, s));
      }
   }

   private static void post(Logger logger, String webhookUrl, String payload) {
      try {
         HttpURLConnection httpurlconnection = (HttpURLConnection)URI.create(webhookUrl).toURL().openConnection();
         httpurlconnection.setRequestMethod("POST");
         httpurlconnection.setRequestProperty("Content-Type", "application/json");
         httpurlconnection.setDoOutput(true);
         byte[] abyte = payload.getBytes(StandardCharsets.UTF_8);
         httpurlconnection.setFixedLengthStreamingMode(abyte.length);

         try (OutputStream outputstream = httpurlconnection.getOutputStream()) {
            outputstream.write(abyte);
         }

         httpurlconnection.getInputStream().close();
      } catch (Exception exception) {
         logger.log(Level.WARNING, "Discord webhook failed: " + exception.getMessage());
      }
   }

   private static String buildPayload(String title, String description, int colorRgb, String thumbnailUrl, String footer, List<DiscordWebhook.Field> fields) {
      StringBuilder stringbuilder = new StringBuilder("{");
      stringbuilder.append("\"title\":\"").append(escape(title)).append("\"");
      stringbuilder.append(",\"description\":\"").append(escape(description)).append("\"");
      stringbuilder.append(",\"color\":").append(colorRgb);
      if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
         stringbuilder.append(",\"thumbnail\":{\"url\":\"").append(escape(thumbnailUrl)).append("\"}");
      }

      if (footer != null && !footer.isBlank()) {
         stringbuilder.append(",\"footer\":{\"text\":\"").append(escape(footer)).append("\"}");
      }

      if (fields != null && !fields.isEmpty()) {
         stringbuilder.append(",\"fields\":[");

         for (int i = 0; i < fields.size(); i++) {
            DiscordWebhook.Field discordwebhook$field = fields.get(i);
            if (i > 0) {
               stringbuilder.append(',');
            }

            stringbuilder.append("{\"name\":\"").append(escape(discordwebhook$field.name())).append("\"");
            stringbuilder.append(",\"value\":\"").append(escape(discordwebhook$field.value())).append("\"");
            stringbuilder.append(",\"inline\":").append(discordwebhook$field.inline()).append('}');
         }

         stringbuilder.append(']');
      }

      stringbuilder.append('}');
      return "{\"embeds\":[" + stringbuilder + "]}";
   }

   private static String escape(String input) {
      return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
   }

   public static record Field(String name, String value, boolean inline) {
   }
}
