package org.example.functions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.User;
import org.example.functions.service.UserService;
import org.example.functions.util.EnvHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ContactTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();
    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";
    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";

    // ─── POST /api/contact ────────────────────────────────────────────────────

    @FunctionName("submitContactForm")
    public HttpResponseMessage submitContactForm(
        @HttpTrigger(name = "submitContactForm", route = "contact",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        try {
            String body = request.getBody().orElse("{}");
            JsonNode json = OBJECT_MAPPER.readTree(body);
            String senderName = getString(json, "name");
            String senderEmail = getString(json, "email");
            String message = getString(json, "message");

            if (senderName == null || senderEmail == null || message == null) {
                return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("name, email, and message are required").build();
            }

            String apiKey = EnvHelper.getSendGridApiKey();
            String fromEmail = EnvHelper.getSendGridFromEmail();
            if (apiKey == null || fromEmail == null) {
                log.error("SENDGRID_API_KEY or SENDGRID_FROM_EMAIL not configured");
                return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                    .body("Email service not configured").build();
            }

            List<User> admins = UserService.getServiceInstance().getAdminUsers();
            if (admins.isEmpty()) {
                log.warn("No admin users found to receive contact form submission");
                return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                    .body("No recipients configured").build();
            }

            String toArray = admins.stream()
                .map(u -> "{\"email\":\"" + u.getEmail() + "\"}")
                .collect(Collectors.joining(","));

            String subject = "Contact Form: " + senderName;
            String text = "Name: " + senderName + "\nEmail: " + senderEmail + "\n\nMessage:\n" + message;

            String payload = "{"
                + "\"personalizations\":[{\"to\":[" + toArray + "]}],"
                + "\"from\":{\"email\":\"" + fromEmail + "\"},"
                + "\"reply_to\":{\"email\":\"" + senderEmail + "\",\"name\":\"" + senderName + "\"},"
                + "\"subject\":\"" + subject + "\","
                + "\"content\":[{\"type\":\"text/plain\",\"value\":\"" + escapeJson(text) + "\"}]"
                + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest sgRequest = HttpRequest.newBuilder()
                .uri(URI.create(SENDGRID_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> sgResponse = client.send(sgRequest, HttpResponse.BodyHandlers.ofString());
            if (sgResponse.statusCode() >= 200 && sgResponse.statusCode() < 300) {
                log.info("Contact form email sent to {} admin(s) from {}", admins.size(), senderEmail);
                return cors(request.createResponseBuilder(HttpStatus.OK))
                    .body("Message sent").build();
            } else {
                log.error("SendGrid returned {}: {}", sgResponse.statusCode(), sgResponse.body());
                return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                    .body("Failed to send message").build();
            }

        } catch (Exception e) {
            log.error("Error processing contact form", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage()).build();
        }
    }

    private String getString(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String val = n.asText().trim();
        return val.isEmpty() ? null : val;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", "*");
    }
}
