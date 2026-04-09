package org.example.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.Cart;
import org.example.functions.model.ComicBook;
import org.example.functions.model.User;
import org.example.functions.service.ActivityLogService;
import org.example.functions.service.BidService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
public class BidTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private static final String CORS_ORIGIN  = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

    // ─── POST /api/bid/cancel ─────────────────────────────────────────────────
    // Admin-only: cancel an opened bid before any user has bid. Resets to pre-opened state.

    @FunctionName("cancelBid")
    public HttpResponseMessage cancelBid(
        @HttpTrigger(name = "cancelBid", route = "bid/cancel",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            ComicBook comic = BidService.getServiceInstance().cancelBid(comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(comic)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("cancelBid error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/bid/open ───────────────────────────────────────────────────
    // Admin-only: open a bid-enabled comic for bidding. Users will see the Bid button.
    // The countdown does not start until the first user bids.

    @FunctionName("openBid")
    public HttpResponseMessage openBid(
        @HttpTrigger(name = "openBid", route = "bid/open",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        if (AuthHelper.requireAdmin(request) == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            ComicBook comic = BidService.getServiceInstance().openBid(comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(comic)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("openBid error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/bid/start ──────────────────────────────────────────────────
    // Start a bidding cycle on an enableBid comic. Called when user clicks "Bid".

    @FunctionName("startBid")
    public HttpResponseMessage startBid(
        @HttpTrigger(name = "startBid", route = "bid/start",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            ComicBook comic = BidService.getServiceInstance().startBidding(user, comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(comic)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("startBid error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/bid ────────────────────────────────────────────────────────
    // Place a bid on a comic currently in a bidding cycle.

    @FunctionName("placeBid")
    public HttpResponseMessage placeBid(
        @HttpTrigger(name = "placeBid", route = "bid",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            JsonNode amountNode = body.get("amount");
            if (amountNode == null || amountNode.isNull()) return badRequest(request, "amount is required");
            BigDecimal amount = new BigDecimal(amountNode.asText());

            ComicBook comic = BidService.getServiceInstance().placeBid(user, comicId, amount);
            String logMsg = String.format("User %s placed bid of $%.2f on \"%s\"",
                user.getName(), amount, comic.getTitle());
            ActivityLogService.getServiceInstance().writeLog(logMsg, false);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(comic)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("placeBid error", e);
            return serverError(request, e);
        }
    }

    // ─── POST /api/bid/finalize ───────────────────────────────────────────────
    // Called by the frontend when the bid timer expires. Adds the winner's comic to their cart.

    @FunctionName("finalizeBid")
    public HttpResponseMessage finalizeBid(
        @HttpTrigger(name = "finalizeBid", route = "bid/finalize",
            methods = {HttpMethod.POST, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }
        User user = AuthHelper.requireApproved(request);
        if (user == null) return unauthorized(request);
        try {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody().orElse("{}"));
            String comicId = HttpHelper.getString(body, "comicId");
            if (comicId == null) return badRequest(request, "comicId is required");

            Cart cart = BidService.getServiceInstance().finalizeBid(comicId);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(cart)).build();
        } catch (IllegalStateException e) {
            return cors(request.createResponseBuilder(HttpStatus.CONFLICT)).body(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return badRequest(request, e.getMessage());
        } catch (Exception e) {
            log.error("finalizeBid error", e);
            return serverError(request, e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", "*");
    }

    private HttpResponseMessage unauthorized(HttpRequestMessage<?> request) {
        return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED)).body("Unauthorized").build();
    }

    private HttpResponseMessage badRequest(HttpRequestMessage<?> request, String msg) {
        return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).body(msg).build();
    }

    private HttpResponseMessage serverError(HttpRequestMessage<?> request, Exception e) {
        return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
            .body(e.getMessage()).build();
    }
}
