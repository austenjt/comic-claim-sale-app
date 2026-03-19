package org.example.functions.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

public class HttpHelper {

    public static HttpResponseMessage getOkResponse(HttpRequestMessage<?> request, String body) {
        return request.createResponseBuilder(HttpStatus.OK)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "*")
            .header("Content-Type", "application/json")
            .body(body)
            .build();
    }

    public static HttpResponseMessage getErrorResponse(HttpRequestMessage<?> request, String body) {
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "*")
            .header("Content-Type", "text/plain")
            .body(body)
            .build();
    }

    public static String getString(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        String s = val.asText().trim();
        return s.isEmpty() ? null : s;
    }

}
