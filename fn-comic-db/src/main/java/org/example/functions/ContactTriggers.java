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
import org.example.functions.service.EmailService;
import org.example.functions.util.EnvHelper;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ContactTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();
    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

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

            if (EnvHelper.getSmtpUsername() == null || EnvHelper.getSmtpPassword() == null
                    || EnvHelper.getSmtpHost() == null) {
                log.error("SMTP credentials not fully configured");
                return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                    .body("Email service not configured").build();
            }

            String adminEmail = EnvHelper.getAdminEmail();
            if (adminEmail == null) {
                log.warn("ADMIN_EMAIL not configured — no recipient for contact form");
                return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                    .body("No recipients configured").build();
            }

            String subject = "Contact Form: " + senderName;
            String text = "Name: " + senderName + "\nEmail: " + senderEmail + "\n\nMessage:\n" + message;

            EmailService.getServiceInstance().send(List.of(adminEmail), senderEmail, null, subject, text);

            log.info("Contact form submitted by {}", senderEmail);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .body("Message sent").build();

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

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", "*");
    }
}
