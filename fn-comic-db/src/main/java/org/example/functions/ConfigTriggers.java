package org.example.functions;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.util.EnvHelper;

import java.util.Optional;

@Slf4j
public class ConfigTriggers {

    private static final String CORS_ORIGIN  = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

    @FunctionName("getConfig")
    public HttpResponseMessage getConfig(
        @HttpTrigger(name = "getConfig", route = "config",
            methods = {HttpMethod.GET, HttpMethod.OPTIONS},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        if (request.getHttpMethod() == HttpMethod.OPTIONS) {
            return cors(request.createResponseBuilder(HttpStatus.OK)).build();
        }

        String json = "{\"gmailEnabled\":" + EnvHelper.isGmailEnabled() + "}";
        return cors(request.createResponseBuilder(HttpStatus.OK))
            .header("Content-Type", "application/json")
            .body(json)
            .build();
    }

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", "*");
    }
}
