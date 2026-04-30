package org.example.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.User;
import org.example.functions.model.enums.PaymentStatus;
import org.example.functions.service.CartService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class PaymentTriggers {

    private static final ObjectMapper MAPPER = Mappers.STANDARD;
    private static final String SQUARE_PAYMENTS_URL = "https://connect.squareup.com/v2/payments";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // ─── POST /api/payment ────────────────────────────────────────────────────

    @FunctionName("processPayment")
    public HttpResponseMessage processPayment(
        @HttpTrigger(name = "processPayment", route = "payment",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing payment...");
        User user = AuthHelper.requireApproved(request);
        if (user == null) return HttpHelper.unauthorized(request);

        String accessToken = System.getenv("SQUARE_ACCESS_TOKEN");
        String locationId  = System.getenv("SQUARE_LOCATION_ID");
        if (accessToken == null || locationId == null) {
            log.error("Square env vars not configured");
            return HttpHelper.getErrorResponse(request, "Payment service not configured.");
        }

        try {
            JsonNode body = MAPPER.readTree(request.getBody().orElse("{}"));
            String nonce = HttpHelper.requireString(body, "nonce");
            String cartId = HttpHelper.requireString(body, "cartId");
            long amountCents = body.path("amountCents").asLong(0);
            if (amountCents <= 0) {
                return HttpHelper.badRequest(request, "amountCents must be greater than zero.");
            }

            ObjectNode squareBody = MAPPER.createObjectNode();
            squareBody.put("source_id", nonce);
            squareBody.put("idempotency_key", UUID.randomUUID().toString());
            squareBody.put("location_id", locationId);
            ObjectNode money = squareBody.putObject("amount_money");
            money.put("amount", amountCents);
            money.put("currency", "USD");

            HttpRequest squareRequest = HttpRequest.newBuilder()
                .uri(URI.create(SQUARE_PAYMENTS_URL))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("Square-Version", "2024-10-17")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(squareBody)))
                .build();

            HttpResponse<String> squareResponse = HTTP_CLIENT.send(squareRequest,
                HttpResponse.BodyHandlers.ofString());

            JsonNode responseJson = MAPPER.readTree(squareResponse.body());

            if (squareResponse.statusCode() == 200) {
                String paymentId = responseJson.path("payment").path("id").asText("");
                CartService.getServiceInstance().updatePaymentStatus(cartId, PaymentStatus.PAID);
                log.info("Payment {} succeeded for cart={} user={}", paymentId, cartId, user.getId());
                ObjectNode result = MAPPER.createObjectNode();
                result.put("success", true);
                result.put("paymentId", paymentId);
                return HttpHelper.getOkResponse(request, MAPPER.writeValueAsString(result));
            }

            String squareError = responseJson.path("errors").path(0).path("detail").asText("Payment declined.");
            log.warn("Square payment rejected for user={} status={} error={}", user.getId(), squareResponse.statusCode(), squareError);
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", false);
            result.put("error", squareError);
            return HttpHelper.getOkResponse(request, MAPPER.writeValueAsString(result));

        } catch (IllegalArgumentException e) {
            return HttpHelper.badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing payment for user={}", user.getId(), e);
            return HttpHelper.getErrorResponse(request, "An internal error occurred.");
        }
    }

    // ─── OPTIONS /api/payment (CORS preflight) ────────────────────────────────

    @FunctionName("processPaymentOptions")
    public HttpResponseMessage processPaymentOptions(
        @HttpTrigger(name = "processPaymentOptions", route = "payment",
            methods = {HttpMethod.OPTIONS}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        return HttpHelper.cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
    }
}
