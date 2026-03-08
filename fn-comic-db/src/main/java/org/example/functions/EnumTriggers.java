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
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.CoverVariant;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.model.enums.PageQuality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class EnumTriggers {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

    @FunctionName("getEnums")
    public HttpResponseMessage getEnums(
        @HttpTrigger(name = "getEnums", route = "enums",
            methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        try {
            Map<String, Object> response = new LinkedHashMap<>();

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
                .body(OBJECT_MAPPER.writeValueAsString(response)).build();

        } catch (Exception e) {
            log.error("getEnums error", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body("Error building enum response").build();
        }
    }

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder builder) {
        return builder
            .header("Access-Control-Allow-Origin", CORS_ORIGIN)
            .header("Access-Control-Allow-Methods", "GET, OPTIONS")
            .header("Access-Control-Allow-Headers", CORS_HEADERS);
    }
}
