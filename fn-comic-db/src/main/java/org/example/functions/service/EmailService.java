package org.example.functions.service;

import lombok.extern.slf4j.Slf4j;
import org.example.functions.util.EnvHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
public class EmailService {

    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static EmailService SERVICE_INSTANCE;

    public static EmailService getServiceInstance() {
        if (SERVICE_INSTANCE == null) {
            SERVICE_INSTANCE = new EmailService();
        }
        return SERVICE_INSTANCE;
    }

    /**
     * Sends a plain-text email via SendGrid to one or more recipients.
     *
     * @param toAddresses list of recipient email addresses
     * @param replyTo     reply-to address (may be null)
     * @param subject     email subject
     * @param body        plain-text body
     */
    public void send(List<String> toAddresses, String replyTo, String subject, String body) {
        String apiKey = EnvHelper.getSendGridApiKey();
        String fromEmail = EnvHelper.getSendGridFromEmail();

        if (apiKey == null || fromEmail == null) {
            log.error("SENDGRID_API_KEY or SENDGRID_FROM_EMAIL not configured — email not sent");
            return;
        }
        if (toAddresses == null || toAddresses.isEmpty()) {
            log.warn("No recipients provided — email not sent");
            return;
        }

        try {
            String toArray = toAddresses.stream()
                .map(e -> "{\"email\":\"" + escapeJson(e) + "\"}")
                .collect(java.util.stream.Collectors.joining(","));

            String replyToBlock = replyTo != null
                ? ",\"reply_to\":{\"email\":\"" + escapeJson(replyTo) + "\"}"
                : "";

            String payload = "{"
                + "\"personalizations\":[{\"to\":[" + toArray + "]}],"
                + "\"from\":{\"email\":\"" + escapeJson(fromEmail) + "\"},"
                + replyToBlock
                + "\"subject\":\"" + escapeJson(subject) + "\","
                + "\"content\":[{\"type\":\"text/plain\",\"value\":\"" + escapeJson(body) + "\"}]"
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SENDGRID_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent to {} recipient(s): {}", toAddresses.size(), subject);
            } else {
                log.error("SendGrid returned {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email via SendGrid", e);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
