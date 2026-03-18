package org.example.functions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.ArchivedOrder;
import org.example.functions.model.Cart;
import org.example.functions.model.User;
import org.example.functions.service.ArchiveService;
import org.example.functions.service.CartService;
import org.example.functions.service.ComicService;
import org.example.functions.service.DiscountService;
import org.example.functions.service.SessionService;
import org.example.functions.service.UserService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class AdminTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();
    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

    // ─── GET /api/orders ──────────────────────────────────────────────────────

    @FunctionName("getAllOrders")
    public HttpResponseMessage getAllOrders(
        @HttpTrigger(name = "getAllOrders", route = "orders",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            List<Cart> carts = CartService.getServiceInstance().getAllActiveCarts();
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(carts)).build();
        } catch (Exception e) {
            log.error("getAllOrders error", e);
            return serverError(request, e);
        }
    }

    // ─── GET /api/orders/open ─────────────────────────────────────────────────

    @FunctionName("getOpenCarts")
    public HttpResponseMessage getOpenCarts(
        @HttpTrigger(name = "getOpenCarts", route = "orders/open",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            List<Cart> carts = CartService.getServiceInstance().getAllOpenCarts();
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(carts)).build();
        } catch (Exception e) {
            log.error("getOpenCarts error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/fulfill ───────────────────────────────────

    @FunctionName("fulfillOrder")
    public HttpResponseMessage fulfillOrder(
        @HttpTrigger(name = "fulfillOrder", route = "orders/{cartId}/fulfill",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().fulfillCart(cartId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("fulfillOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── DELETE /api/orders/claim/{comicId} ──────────────────────────────────

    @FunctionName("adminUnclaim")
    public HttpResponseMessage adminUnclaim(
        @HttpTrigger(name = "adminUnclaim", route = "orders/claim/{comicId}",
            methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("comicId") String comicId)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            CartService.getServiceInstance().removeItemAdmin(comicId);
            return cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("adminUnclaim error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/awards ────────────────────────────────────────────────────

    @FunctionName("awardComic")
    public HttpResponseMessage awardComic(
        @HttpTrigger(name = "awardComic", route = "awards",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            String comicId = payload.get("comicId").asText();
            String userId = payload.get("userId").asText();

            User user = UserService.getServiceInstance().findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            Cart cart = CartService.getServiceInstance().awardItem(user, comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("awardComic error", e);
            return serverError(request, e);
        }
    }

    // ─── GET /api/orders/archived ─────────────────────────────────────────────

    @FunctionName("getArchivedOrders")
    public HttpResponseMessage getArchivedOrders(
        @HttpTrigger(name = "getArchivedOrders", route = "orders/archived",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            List<ArchivedOrder> orders = ArchiveService.getServiceInstance().getAllArchivedOrders();
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(orders)).build();
        } catch (Exception e) {
            log.error("getArchivedOrders error", e);
            return serverError(request, e);
        }
    }

    // ─── DELETE /api/orders/archived/{orderId}/full ───────────────────────────

    @FunctionName("fullDeleteArchivedOrder")
    public HttpResponseMessage fullDeleteArchivedOrder(
        @HttpTrigger(name = "fullDeleteArchivedOrder", route = "orders/archived/{orderId}/full",
            methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("orderId") String orderId)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            ArchivedOrder order = ArchiveService.getServiceInstance().getArchivedOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Archived order not found: " + orderId));

            // Collect distinct non-null collectionGroups from order items
            List<Integer> groups = order.getItems().stream()
                .map(org.example.functions.model.ArchivedOrderItem::getCollectionGroup)
                .filter(g -> g != null && g > 0)
                .distinct()
                .collect(Collectors.toList());

            ArchiveService.getServiceInstance().deleteArchivedOrder(orderId);

            for (Integer group : groups) {
                try {
                    ComicService.getServiceInstance().deleteSetFully(group);
                } catch (Exception e) {
                    log.warn("fullDeleteArchivedOrder: could not delete set group {}: {}", group, e.getMessage());
                }
            }

            return cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("fullDeleteArchivedOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── DELETE /api/orders/archived/{orderId} ────────────────────────────────

    @FunctionName("deleteArchivedOrder")
    public HttpResponseMessage deleteArchivedOrder(
        @HttpTrigger(name = "deleteArchivedOrder", route = "orders/archived/{orderId}",
            methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("orderId") String orderId)
    {
        if (requireAdmin(request) == null) return unauthorized(request);
        try {
            ArchiveService.getServiceInstance().deleteArchivedOrder(orderId);
            return cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
        } catch (Exception e) {
            log.error("deleteArchivedOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/reset ─────────────────────────────────────────────────────

    @FunctionName("resetDatabase")
    public HttpResponseMessage resetDatabase(
        @HttpTrigger(name = "resetDatabase", route = "reset",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User admin = requireAdmin(request);
        if (admin == null) return unauthorized(request);
        String adminToken = request.getHeaders().get("x-session-token");
        try {
            log.warn("DATABASE RESET initiated by admin {}", admin.getEmail());

            // 1. Migrate any fulfilled carts not yet in archived-orders
            List<Cart> fulfilled = CartService.getServiceInstance().getAllFulfilledCarts();
            ArchiveService.getServiceInstance().migrateAll(fulfilled);

            // 2. Delete all carts (safe now that fulfilled orders are archived)
            CartService.getServiceInstance().deleteAllCarts();

            // 3. Delete all discounts
            DiscountService.getServiceInstance().deleteAllDiscounts();

            // 4. Delete all comics and images
            ComicService.getServiceInstance().deleteAllComicsAndImages();

            // 5. Delete all sessions except admin's current session
            SessionService.getServiceInstance().deleteAllExcept(adminToken);

            log.warn("DATABASE RESET complete.");
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .body("Database reset complete.").build();
        } catch (Exception e) {
            log.error("resetDatabase error", e);
            return serverError(request, e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User requireAdmin(HttpRequestMessage<?> request) {
        String token = request.getHeaders().get("x-session-token");
        String userId = SessionService.getServiceInstance().validateSession(token);
        if (userId == null) return null;
        User user = UserService.getServiceInstance().findById(userId).orElse(null);
        if (user == null || !user.isAdmin()) return null;
        return user;
    }

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", "*");
    }

    private HttpResponseMessage unauthorized(HttpRequestMessage<?> request) {
        return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
            .body("Unauthorized").build();
    }

    private HttpResponseMessage serverError(HttpRequestMessage<?> request, Exception e) {
        return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
            .body(e.getMessage()).build();
    }
}
