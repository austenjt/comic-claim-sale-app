package org.example.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.ComicBook;
import org.example.functions.service.ComicService;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Optional;

@Slf4j
public class SearchTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.WITH_JAVA_TIME;

    @FunctionName("getComicsSearch")
    public HttpResponseMessage getComicsSearch(
        @HttpTrigger(
            name = "getComicsSearch",
            route = "search",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request
    ) {
        String titleSearch = request.getQueryParameters().get("title");
        log.info("Processing getComicsSearch(...) with '{}'.", titleSearch);
        ComicService comicService = ComicService.getServiceInstance();
        List<ComicBook> comicBookData = comicService.getComicsSearch(titleSearch);
        try {
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(comicBookData))
                .build();
        } catch (JsonProcessingException e) {
            log.error("Severe error processing getComics().", e);
            return HttpHelper.getErrorResponse(request, "Failed to serialize search results.");
        }
    }

}
