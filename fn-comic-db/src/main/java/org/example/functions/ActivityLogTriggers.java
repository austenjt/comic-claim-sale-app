package org.example.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.ActivityLog;
import org.example.functions.service.ActivityLogService;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ActivityLogTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    @FunctionName("writeActivityLog")
    public HttpResponseMessage writeActivityLog(
        @HttpTrigger(
            name = "writeActivityLog",
            route = "activity-logs",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing writeActivityLog.");
        try {
            String body = request.getBody().orElse(null);
            if (body == null || body.isBlank()) {
                return HttpHelper.cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("Request body required.")                    .build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = OBJECT_MAPPER.readValue(body, Map.class);
            String message = (String) payload.getOrDefault("message", "");
            boolean isError = Boolean.TRUE.equals(payload.get("isError"));
            ActivityLog entry = ActivityLogService.getServiceInstance().writeLog(message, isError);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(entry))
                .build();
        } catch (Exception e) {
            log.error("Error writing activity log", e);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body("Failed to write log.")                .build();
        }
    }

    @FunctionName("getActivityLogs")
    public HttpResponseMessage getActivityLogs(
        @HttpTrigger(
            name = "getActivityLogs",
            route = "activity-logs",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getActivityLogs.");
        try {
            List<ActivityLog> logs = ActivityLogService.getServiceInstance().getRecentLogs(100);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(logs))
                .build();
        } catch (Exception e) {
            log.error("Error fetching activity logs", e);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body("Failed to load logs.")                .build();
        }
    }
}
