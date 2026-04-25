package org.example.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.functions.util.Mappers;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.CoverVariant;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.model.enums.PageQuality;
import org.example.functions.util.EnvHelper;
import org.example.functions.util.HttpHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ConfigTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

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

        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("emailEnabled", EnvHelper.isEmailEnabled());
            response.put("awardModeEnabled", EnvHelper.isAwardModeEnabled());
            response.put("biddingMode", EnvHelper.isBiddingModeEnabled());
            response.put("biddingCycleMins", EnvHelper.getBiddingCycleMins());

            List<String> coverVariants = new ArrayList<>();
            for (CoverVariant cv : CoverVariant.values()) {
                coverVariants.add(cv.getLabel());
            }
            response.put("coverVariants", coverVariants);

            List<String> gradingCompanies = new ArrayList<>();
            for (GradingCompany gc : GradingCompany.values()) {
                gradingCompanies.add(gc.getValue());
            }
            response.put("gradingCompanies", gradingCompanies);

            List<Map<String, Object>> grades = new ArrayList<>();
            for (ComicGrade g : ComicGrade.values()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("value", g.getNumericGrade());
                entry.put("label", g.getLabel());
                grades.add(entry);
            }
            response.put("grades", grades);

            List<String> pageQualities = new ArrayList<>();
            for (PageQuality pq : PageQuality.values()) {
                pageQualities.add(pq.getValue());
            }
            response.put("pageQualities", pageQualities);

            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(response))
                .build();

        } catch (Exception e) {
            log.error("getConfig error", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body("Error building config response").build();
        }
    }

    /** Thin wrapper delegating to {@link HttpHelper#cors}. */
    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return HttpHelper.cors(b);
    }
}
