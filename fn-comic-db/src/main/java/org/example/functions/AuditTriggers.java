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
import org.example.functions.model.ComicAuditLog;
import org.example.functions.model.User;
import org.example.functions.service.AuditService;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Optional;

@Slf4j
public class AuditTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    @FunctionName("getComicAuditLog")
    public HttpResponseMessage getComicAuditLog(
        @HttpTrigger(
            name = "getComicAuditLog",
            route = "audit/comics/{comicId}",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("comicId") String comicId)
    {
        User admin = AuthHelper.requireAdmin(request);
        if (admin == null) {
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required.")                .build();
        }
        try {
            List<ComicAuditLog> logs = AuditService.getServiceInstance().getAuditLogsForComic(comicId);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(logs))
                .build();
        } catch (Exception e) {
            log.error("Error fetching audit log for comic {}", comicId, e);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body("Failed to load audit log.")                .build();
        }
    }
}
