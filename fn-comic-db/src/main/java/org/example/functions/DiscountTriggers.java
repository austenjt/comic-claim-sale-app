package org.example.functions;

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
import org.example.functions.model.Discount;
import org.example.functions.service.DiscountService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Optional;

@Slf4j
public class DiscountTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    // ─── GET /api/discounts ───────────────────────────────────────────────────

    @FunctionName("getAllDiscounts")
    public HttpResponseMessage getAllDiscounts(
        @HttpTrigger(name = "getAllDiscounts", route = "discounts",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        try {
            List<Discount> discounts = DiscountService.getServiceInstance().getAllDiscounts();
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(discounts)).build();
        } catch (Exception e) {
            log.error("getAllDiscounts error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/discounts ──────────────────────────────────────────────────

    @FunctionName("createDiscount")
    public HttpResponseMessage createDiscount(
        @HttpTrigger(name = "createDiscount", route = "discounts",
            methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("");
            Discount discount = OBJECT_MAPPER.readValue(body, Discount.class);
            Discount created = DiscountService.getServiceInstance().createDiscount(discount);
            return cors(request.createResponseBuilder(HttpStatus.CREATED))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(created)).build();
        } catch (Exception e) {
            log.error("createDiscount error", e);
            return serverError(request, e);
        }
    }

    // ─── PUT /api/discounts/{id} ──────────────────────────────────────────────

    @FunctionName("updateDiscount")
    public HttpResponseMessage updateDiscount(
        @HttpTrigger(name = "updateDiscount", route = "discounts/{id}",
            methods = {HttpMethod.PUT}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            String body = request.getBody().orElse("");
            Discount discount = OBJECT_MAPPER.readValue(body, Discount.class);
            discount.setId(id);
            Discount updated = DiscountService.getServiceInstance().updateDiscount(discount);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(updated)).build();
        } catch (Exception e) {
            log.error("updateDiscount error", e);
            return serverError(request, e);
        }
    }

    // ─── DELETE /api/discounts/{id} ───────────────────────────────────────────

    @FunctionName("deleteDiscount")
    public HttpResponseMessage deleteDiscount(
        @HttpTrigger(name = "deleteDiscount", route = "discounts/{id}",
            methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            DiscountService.getServiceInstance().deleteDiscount(id);
            return cors(request.createResponseBuilder(HttpStatus.NO_CONTENT)).build();
        } catch (Exception e) {
            log.error("deleteDiscount error", e);
            return serverError(request, e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    //
    // These thin wrappers delegate to HttpHelper so call sites in this trigger keep their
    // current shape. New triggers should call HttpHelper directly.

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
