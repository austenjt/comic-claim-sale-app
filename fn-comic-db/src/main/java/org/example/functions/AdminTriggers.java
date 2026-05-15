package org.example.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.example.functions.model.ArchivedOrderItem;
import org.example.functions.model.Cart;
import org.example.functions.model.User;
import org.example.functions.model.enums.PaymentStatus;
import org.example.functions.service.ArchiveService;
import org.example.functions.service.CartService;
import org.example.functions.service.ComicService;
import org.example.functions.service.DiscountService;
import org.example.functions.service.UserService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class AdminTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    // ─── GET /api/orders ──────────────────────────────────────────────────────

    @FunctionName("getAllOrders")
    public HttpResponseMessage getAllOrders(
        @HttpTrigger(name = "getAllOrders", route = "orders",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().fulfillCart(cartId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("fulfillOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/receive-trade ─────────────────────────────

    @FunctionName("receiveTradeItem")
    public HttpResponseMessage receiveTradeItem(
        @HttpTrigger(name = "receiveTradeItem", route = "orders/{cartId}/receive-trade",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().markTradeReceived(cartId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("receiveTradeItem error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/refresh-discounts ─────────────────────────

    /**
     * Admin: re-runs discount calculation on a FINALIZING cart and re-sends the
     * order-submitted email. Used to recover a cart whose discount snapshot was
     * computed before a discount-logic change shipped.
     */
    @FunctionName("refreshOrderDiscounts")
    public HttpResponseMessage refreshOrderDiscounts(
        @HttpTrigger(name = "refreshOrderDiscounts", route = "orders/{cartId}/refresh-discounts",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().refreshSubmittedDiscounts(cartId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("refreshOrderDiscounts error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/unsubmit ──────────────────────────────────

    @FunctionName("unsubmitOrder")
    public HttpResponseMessage unsubmitOrder(
        @HttpTrigger(name = "unsubmitOrder", route = "orders/{cartId}/unsubmit",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().unsubmitOrder(cartId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("unsubmitOrder error", e);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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

    // ─── POST /api/orders/{cartId}/notes ─────────────────────────────────────

    @FunctionName("updateOrderAdminNotes")
    public HttpResponseMessage updateOrderAdminNotes(
        @HttpTrigger(name = "updateOrderAdminNotes", route = "orders/{cartId}/notes",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            String notes = payload.has("adminNotes") ? payload.get("adminNotes").asText(null) : null;
            Cart cart = CartService.getServiceInstance().updateAdminNotes(cartId, notes);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updateOrderAdminNotes error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/payment ───────────────────────────────────

    /** Statuses an admin is currently allowed to assign via the public payment endpoint. */
    private static final java.util.Set<PaymentStatus> ASSIGNABLE_PAYMENT_STATUSES =
        java.util.Set.of(PaymentStatus.UNPAID, PaymentStatus.PAID);

    @FunctionName("updatePaymentStatus")
    public HttpResponseMessage updatePaymentStatus(
        @HttpTrigger(name = "updatePaymentStatus", route = "orders/{cartId}/payment",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            String raw = payload.has("status") ? payload.get("status").asText() : null;
            PaymentStatus parsed;
            try {
                parsed = PaymentStatus.fromJson(raw);
            } catch (IllegalArgumentException e) {
                parsed = null;
            }
            if (parsed == null || !ASSIGNABLE_PAYMENT_STATUSES.contains(parsed)) {
                return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("status must be one of: UNPAID, PAID").build();
            }
            Cart cart = CartService.getServiceInstance().updatePaymentStatus(cartId, parsed);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updatePaymentStatus error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/{cartId}/shipping ──────────────────────────────────

    @FunctionName("updateShipping")
    public HttpResponseMessage updateShipping(
        @HttpTrigger(name = "updateShipping", route = "orders/{cartId}/shipping",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("cartId") String cartId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            boolean shipped = payload.has("shipped") && payload.get("shipped").asBoolean();
            String trackingNumber = payload.has("trackingNumber") && !payload.get("trackingNumber").isNull()
                ? payload.get("trackingNumber").asText(null) : null;
            Cart cart = CartService.getServiceInstance().updateShipping(cartId, shipped, trackingNumber);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updateShipping error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/archived/{orderId}/payment ──────────────────────────

    @FunctionName("updateArchivedPaymentStatus")
    public HttpResponseMessage updateArchivedPaymentStatus(
        @HttpTrigger(name = "updateArchivedPaymentStatus", route = "orders/archived/{orderId}/payment",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("orderId") String orderId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            String raw = payload.has("status") ? payload.get("status").asText() : null;
            PaymentStatus parsed;
            try {
                parsed = PaymentStatus.fromJson(raw);
            } catch (IllegalArgumentException e) {
                parsed = null;
            }
            if (parsed == null || !ASSIGNABLE_PAYMENT_STATUSES.contains(parsed)) {
                return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("status must be one of: UNPAID, PAID").build();
            }
            ArchivedOrder order = ArchiveService.getServiceInstance().updatePaymentStatus(orderId, parsed);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(order)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updateArchivedPaymentStatus error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/archived/{orderId}/notes ───────────────────────────

    @FunctionName("updateArchivedAdminNotes")
    public HttpResponseMessage updateArchivedAdminNotes(
        @HttpTrigger(name = "updateArchivedAdminNotes", route = "orders/archived/{orderId}/notes",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("orderId") String orderId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            String notes = payload.has("adminNotes") && !payload.get("adminNotes").isNull()
                ? payload.get("adminNotes").asText(null) : null;
            ArchivedOrder order = ArchiveService.getServiceInstance().updateAdminNotes(orderId, notes);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(order)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updateArchivedAdminNotes error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/orders/archived/{orderId}/shipping ────────────────────────

    @FunctionName("updateArchivedShipping")
    public HttpResponseMessage updateArchivedShipping(
        @HttpTrigger(name = "updateArchivedShipping", route = "orders/archived/{orderId}/shipping",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("orderId") String orderId)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("{}");
            ObjectNode payload = OBJECT_MAPPER.readValue(body, ObjectNode.class);
            boolean shipped = payload.has("shipped") && payload.get("shipped").asBoolean();
            String trackingNumber = payload.has("trackingNumber") && !payload.get("trackingNumber").isNull()
                ? payload.get("trackingNumber").asText(null) : null;
            ArchivedOrder order = ArchiveService.getServiceInstance().updateShipping(orderId, shipped, trackingNumber);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(order)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("updateArchivedShipping error", e);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
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
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            ArchivedOrder order = ArchiveService.getServiceInstance().getArchivedOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Archived order not found: " + orderId));

            List<Integer> groups = order.getItems().stream()
                .map(ArchivedOrderItem::getCollectionGroup)
                .filter(g -> g != null && g > 0)
                .distinct()
                .collect(Collectors.toList());

            List<String> individualIds = order.getItems().stream()
                .filter(i -> (i.getCollectionGroup() == null || i.getCollectionGroup() <= 0) && i.getComicId() != null)
                .map(ArchivedOrderItem::getComicId)
                .collect(Collectors.toList());

            ArchiveService.getServiceInstance().deleteArchivedOrder(orderId);

            for (Integer group : groups) {
                try {
                    ComicService.getServiceInstance().deleteSetFully(group);
                } catch (Exception e) {
                    log.warn("deleteArchivedOrder: could not delete set group {}: {}", group, e.getMessage());
                }
            }

            for (String comicId : individualIds) {
                try {
                    ComicService.getServiceInstance().deleteComic(Integer.parseInt(comicId));
                } catch (Exception e) {
                    log.warn("deleteArchivedOrder: could not delete comic {}: {}", comicId, e.getMessage());
                }
            }

            return cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
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
        User admin = AuthHelper.requireAdmin(request);
        if (admin == null) return unauthorized(request);
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

            log.warn("DATABASE RESET complete.");
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .body("Database reset complete.").build();
        } catch (Exception e) {
            log.error("resetDatabase error", e);
            return serverError(request, e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    //
    // Thin wrappers delegating to HttpHelper so existing call sites don't need to change.

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return HttpHelper.cors(b);
    }

    private HttpResponseMessage unauthorized(HttpRequestMessage<?> request) {
        return HttpHelper.unauthorized(request);
    }

    private HttpResponseMessage serverError(HttpRequestMessage<?> request, Exception e) {
        return HttpHelper.serverError(request, e);
    }
}
