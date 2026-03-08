package org.example.functions;

import com.fasterxml.jackson.core.JsonGenerator;
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
import org.example.functions.model.enums.NumberSentinel;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ValidationTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();

    /**
     * Validates a comic issue number value against the rules enforced by
     * {@link org.example.functions.model.ComicNumber} and {@link NumberSentinel}.
     *
     * <p>Valid inputs:
     * <ul>
     *   <li>A non-negative integer string matching {@code \d+} that fits in an {@code int} (e.g. "0", "1", "250")</li>
     *   <li>Any {@link NumberSentinel} serialized value (currently {@code "-1"} and {@code "NN"})</li>
     * </ul>
     *
     * <p>Query param: {@code value} (required)
     * <p>Response JSON:
     * <pre>
     * {
     *   "value":     "42",
     *   "valid":     true,
     *   "asNumber":  42,
     *   "asSentinel": null,
     *   "validSentinels": ["-1", "NN"],
     *   "message":   null
     * }
     * </pre>
     */
    @FunctionName("validateComicNumber")
    public HttpResponseMessage validateComicNumber(
        @HttpTrigger(
            name = "validateComicNumber",
            route = "validateNumber",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        String value = request.getQueryParameters().get("value");
        log.info("validateComicNumber called with value: {}", value);

        String sentinelList = Arrays.stream(NumberSentinel.values())
            .map(NumberSentinel::getValue)
            .collect(Collectors.joining(", "));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("validSentinels", Arrays.stream(NumberSentinel.values())
            .map(NumberSentinel::getValue)
            .collect(Collectors.toList()));

        if (value == null || value.isBlank()) {
            result.put("valid", false);
            result.put("asNumber", null);
            result.put("asSentinel", null);
            result.put("message", "Value is required.");
            return buildResponse(request, result);
        }

        String trimmed = value.trim();

        // Check sentinel values first (e.g. "-1", "NN")
        for (NumberSentinel sentinel : NumberSentinel.values()) {
            if (sentinel.getValue().equals(trimmed)) {
                result.put("valid", true);
                result.put("asNumber", null);
                result.put("asSentinel", sentinel.getValue());
                result.put("message", null);
                return buildResponse(request, result);
            }
        }

        // Check non-negative integer: digits only, fits in an int
        if (trimmed.matches("\\d+")) {
            try {
                int number = Integer.parseInt(trimmed);
                result.put("valid", true);
                result.put("asNumber", number);
                result.put("asSentinel", null);
                result.put("message", null);
                return buildResponse(request, result);
            } catch (NumberFormatException ignored) {
                // Value too large for int — fall through to invalid
            }
        }

        result.put("valid", false);
        result.put("asNumber", null);
        result.put("asSentinel", null);
        result.put("message",
            "Must be a non-negative integer (0, 1, 2, \u2026) or a sentinel value (" + sentinelList + ").");
        return buildResponse(request, result);
    }

    private HttpResponseMessage buildResponse(HttpRequestMessage<?> request, Map<String, Object> body) {
        try {
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(body))
                .build();
        } catch (Exception e) {
            log.error("Error serializing validateComicNumber response", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .body("Serialization error")
                .build();
        }
    }
}
