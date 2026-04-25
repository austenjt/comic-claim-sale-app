package org.example.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.example.functions.model.ClaimNotification;
import org.example.functions.model.ShippingEstimate;
import org.example.functions.model.User;
import org.example.functions.service.ActivityLogService;
import org.example.functions.service.ArchiveService;
import org.example.functions.service.CartService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;
import org.example.functions.util.ShippingCalculator;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

@Slf4j
public class CartTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    // ─── GET /api/cart ────────────────────────────────────────────────────────

    @FunctionName("getMyCart")
    public HttpResponseMessage getMyCart(
        @HttpTrigger(name = "getMyCart", route = "cart",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            Optional<Cart> cart = CartService.getServiceInstance().getActiveCart(user.getId());
            String body;
            if (cart.isPresent()) {
                Cart c = cart.get();
                long bookCount = c.getItems().stream().filter(i -> !i.isSetContainer()).count();
                ShippingEstimate shipping = ShippingCalculator.estimate((int) bookCount);
                ObjectNode cartNode = OBJECT_MAPPER.valueToTree(c);
                cartNode.set("shippingEstimate", OBJECT_MAPPER.valueToTree(shipping));
                body = OBJECT_MAPPER.writeValueAsString(cartNode);
            } else {
                body = "null";
            }
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json").body(body).build();
        } catch (Exception e) {
            log.error("getMyCart error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/cart/items ─────────────────────────────────────────────────

    @FunctionName("addCartItem")
    public HttpResponseMessage addCartItem(
        @HttpTrigger(name = "addCartItem", route = "cart/items",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            Cart cart = CartService.getServiceInstance().addItem(user, comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("addCartItem error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/cart/set ───────────────────────────────────────────────────

    @FunctionName("addCartSet")
    public HttpResponseMessage addCartSet(
        @HttpTrigger(name = "addCartSet", route = "cart/set",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String containerId = HttpHelper.getString(body, "containerId");
            if (containerId == null) return badRequest(request, "containerId is required");

            Cart cart = CartService.getServiceInstance().addSet(user, containerId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("addCartSet error", e);
            return serverError(request, e);
        }
    }

    // ─── DELETE /api/cart/items/{comicId} ─────────────────────────────────────

    @FunctionName("removeCartItem")
    public HttpResponseMessage removeCartItem(
        @HttpTrigger(name = "removeCartItem", route = "cart/items/{comicId}",
            methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("comicId") String comicId)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().removeItem(user.getId(), comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("removeCartItem error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/cart/submit ────────────────────────────────────────────────

    @FunctionName("submitOrder")
    public HttpResponseMessage submitOrder(
        @HttpTrigger(name = "submitOrder", route = "cart/submit",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String customerNotes = HttpHelper.getString(body, "customerNotes");
            Cart cart = CartService.getServiceInstance().submitOrder(user.getId(), customerNotes);
            double itemsTotal = cart.getItems().stream()
                .filter(i -> !i.isSetContainer())
                .mapToDouble(org.example.functions.model.CartItem::getPrice)
                .sum();
            double total = itemsTotal - cart.getDiscountAmount() + cart.getShippingCost();
            String logMessage = String.format("User %s submitted shopping cart: $%.2f", user.getName(), total);
            ActivityLogService.getServiceInstance().writeLog(logMessage, false);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("submitOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/cart/unsubmit ──────────────────────────────────────────────

    @FunctionName("unsubmitMyOrder")
    public HttpResponseMessage unsubmitMyOrder(
        @HttpTrigger(name = "unsubmitMyOrder", route = "cart/unsubmit",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            Cart cart = CartService.getServiceInstance().getActiveCart(user.getId())
                .orElseThrow(() -> new IllegalStateException("No active cart found."));
            Cart updated = CartService.getServiceInstance().unsubmitOrder(cart.getId());
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(updated)).build();
        } catch (IllegalStateException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("unsubmitMyOrder error", e);
            return serverError(request, e);
        }
    }

    // ─── GET /api/cart/history ────────────────────────────────────────────────

    @FunctionName("getCartHistory")
    public HttpResponseMessage getCartHistory(
        @HttpTrigger(name = "getCartHistory", route = "cart/history",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            java.util.List<ArchivedOrder> history = ArchiveService.getServiceInstance().getArchivedOrdersForUser(user.getId());
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(history)).build();
        } catch (Exception e) {
            log.error("getCartHistory error", e);
            return serverError(request, e);
        }
    }

    // ─── GET /api/notifications ───────────────────────────────────────────────

    @FunctionName("getNotifications")
    public HttpResponseMessage getNotifications(
        @HttpTrigger(name = "getNotifications", route = "notifications",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        try {
            java.util.List<ClaimNotification> notifications =
                CartService.getServiceInstance().getRecentClaimEvents(30);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(notifications)).build();
        } catch (Exception e) {
            log.error("getNotifications error", e);
            return serverError(request, e);
        }
    }

    // ─── GET /api/cart/claimed-ids ────────────────────────────────────────────

    @FunctionName("getClaimedIds")
    public HttpResponseMessage getClaimedIds(
        @HttpTrigger(name = "getClaimedIds", route = "cart/claimed-ids",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        try {
            java.util.Map<String, String> claimed = CartService.getServiceInstance().getClaimedComicMap();
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(claimed)).build();
        } catch (Exception e) {
            log.error("getClaimedIds error", e);
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

    private HttpResponseMessage badRequest(HttpRequestMessage<?> request, String msg) {
        return HttpHelper.badRequest(request, msg);
    }

    private HttpResponseMessage serverError(HttpRequestMessage<?> request, Exception e) {
        return HttpHelper.serverError(request, e);
    }

}
