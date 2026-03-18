package org.example.functions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.example.functions.model.ComicBook;
import org.example.functions.model.User;
import org.example.functions.service.CartService;
import org.example.functions.service.ComicService;
import org.example.functions.service.ImageService;
import org.example.functions.service.SessionService;
import org.example.functions.service.UserService;
import org.example.functions.util.CsvToJsonConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ComicTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();

    @FunctionName("getAllComics")
    public HttpResponseMessage getAllComics(
        @HttpTrigger(
            name = "getAllComics",
            route = "comics",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getAllComics function.");
        String pageNumberStr = request.getQueryParameters().get("pageNumber");
        String pageSizeStr = request.getQueryParameters().get("pageSize");
        boolean admin = isAdminRequest(request);
        ComicService comicService = ComicService.getServiceInstance();
        List<ComicBook> comicBookData;
        try {
            if (pageNumberStr != null && pageSizeStr != null) {
                int pageNumber = Integer.parseInt(pageNumberStr);
                int pageSize = Integer.parseInt(pageSizeStr);
                comicBookData = comicService.getComicsList(pageNumber, pageSize);
            } else {
                comicBookData = comicService.getComicsListEnriched();
            }
            String body = admin
                ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(comicBookData)
                : serializeComicsStripped(comicBookData);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        } catch (NumberFormatException e) {
            log.error("Invalid pagination parameters.", e);
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Invalid pagination parameters")
                .build();
        } catch (JsonProcessingException e) {
            log.error("Severe error processing listAllComics.", e);
            return request.createResponseBuilder(HttpStatus.I_AM_A_TEAPOT)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
    }

    @FunctionName("getComicById")
    public HttpResponseMessage getComicById(
        @HttpTrigger(
            name = "getComicById",
            route = "comics/{id}",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request, @BindingName("id") String id)
    {
        log.info("Processing getComicById {} function.", id);
        boolean admin = isAdminRequest(request);
        ComicService comicService = ComicService.getServiceInstance();
        List<ComicBook> comicBookData = comicService.getComicsList();
        Optional<ComicBook> matchingComic = comicBookData.stream()
            .filter(comic -> comic.getId() == Integer.parseInt(id))
            .findFirst();
        if (matchingComic.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body(String.format("Comic with id %s not found.", id))
                .build();
        }
        try {
            String body = admin
                ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(matchingComic.get())
                : serializeComicStripped(matchingComic.get());
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        } catch (JsonProcessingException e) {
            log.error("Severe error processing getComicById.", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
    }

    @FunctionName("updateComic")
    public HttpResponseMessage updateComic(
        @HttpTrigger(
            name = "updateComic",
            route = "comics",
            methods = {HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing updateComic function.");
        ComicService comicService = ComicService.getServiceInstance();
        String requestBody = request.getBody().orElse(null);
        ComicBook updatedComicBook = null;
        try {
            updatedComicBook = OBJECT_MAPPER.readValue(requestBody, ComicBook.class);
        } catch (JsonProcessingException e) {
            return request.createResponseBuilder(HttpStatus.I_AM_A_TEAPOT)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body("Failed update: " + e.getMessage())
                .build();
        }
        ComicBook successfulUpdate = comicService.updateComic(updatedComicBook);
        return request.createResponseBuilder(HttpStatus.OK)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "*")
            .header("Content-Type", "application/json")
            .body(successfulUpdate)
            .build();
    }

    @FunctionName("createComic")
    public HttpResponseMessage createComic(
        @HttpTrigger(
            name = "createComic",
            route = "comics",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing createComic function.");
        try {
            ComicService comicService = ComicService.getServiceInstance();
            String requestBody = request.getBody().orElse(null);
            ComicBook newComicBook = OBJECT_MAPPER.readValue(requestBody, ComicBook.class);
            Optional<String> optionalId = Optional.ofNullable(request.getQueryParameters().get("id"));
            optionalId.ifPresent(s -> newComicBook.setId(Integer.parseInt(s)));
            ComicBook addedComicBook = comicService.createComic(newComicBook);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(addedComicBook))
                .build();
        } catch (Exception e) {
            log.error("There was a create comic error.", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
    }

    @FunctionName("deleteComic")
    public HttpResponseMessage deleteComic(
        @HttpTrigger(
            name = "deleteComic",
            route = "comics/{id}",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request, @BindingName("id") String id)
    {
        log.info("Processing deleteComic function.");

        final int targetId = Integer.parseInt(id);
        ComicService comicService = ComicService.getServiceInstance();
        List<ComicBook> comicBookList = comicService.getComicsList();
        Optional<ComicBook> existingComic = comicBookList.stream()
            .filter(comic -> comic.getId() == targetId)
            .findFirst();
        try {
            if (existingComic.isPresent()) {
                comicService.deleteComic(existingComic.get().getId());

                // Remove comic from any active carts (best-effort)
                try {
                    CartService.getServiceInstance().removeItemAdmin(String.valueOf(targetId));
                } catch (IllegalArgumentException e) {
                    log.info("Comic {} was not in any active cart.", targetId);
                }

                // Delete associated images (best-effort — may not all exist)
                ImageService imageService = ImageService.getServiceInstance();
                for (String imageName : List.of(
                        targetId + "-large.png",
                        targetId + "-small.png",
                        targetId + "-back-large.png",
                        targetId + "-back-small.png")) {
                    try {
                        imageService.deleteImage(imageName);
                        log.info("Deleted image: {}", imageName);
                    } catch (Exception e) {
                        log.info("Image not found, skipping: {}", imageName);
                    }
                }
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "*")
                    .header("Access-Control-Allow-Headers", "Content-Type")
                    .header("Content-Type", "application/json")
                    .body(String.format("{ \"deleted\": \"%s\", \"id\": %s }", false, targetId))
                    .build();
            }
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.I_AM_A_TEAPOT)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Access-Control-Allow-Headers", "Content-Type")
                .header("Content-Type", "text/plain")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
        return request.createResponseBuilder(HttpStatus.OK)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "*")
            .header("Access-Control-Allow-Headers", "Content-Type")
            .header("Content-Type", "application/json")
            .body(String.format("{ \"deleted\": \"%s\", \"id\": %d }", true, targetId))
            .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns true if the request carries a valid admin session token. */
    private boolean isAdminRequest(HttpRequestMessage<?> request) {
        String token = request.getHeaders().get("x-session-token");
        if (token == null || token.isBlank()) return false;
        String userId = SessionService.getServiceInstance().validateSession(token);
        if (userId == null) return false;
        User user = UserService.getServiceInstance().findById(userId).orElse(null);
        return user != null && user.isAdmin();
    }

    /** Serializes a list of comics, removing pricePaid from each item and its nested set members. */
    private String serializeComicsStripped(List<ComicBook> comics) throws JsonProcessingException {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (ComicBook comic : comics) {
            ObjectNode node = OBJECT_MAPPER.valueToTree(comic);
            stripPricePaid(node);
            array.add(node);
        }
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(array);
    }

    /** Serializes a single comic, removing pricePaid. */
    private String serializeComicStripped(ComicBook comic) throws JsonProcessingException {
        ObjectNode node = OBJECT_MAPPER.valueToTree(comic);
        stripPricePaid(node);
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    /** Removes pricePaid from a comic node and recursively from any nested items. */
    private void stripPricePaid(ObjectNode node) {
        node.remove("pricePaid");
        if (node.has("items")) {
            for (JsonNode item : node.get("items")) {
                ((ObjectNode) item).remove("pricePaid");
            }
        }
    }

    /* URI: /comics/data */

    @FunctionName("pruneImages")
    public HttpResponseMessage pruneImages(
        @HttpTrigger(
            name = "pruneImages",
            route = "comics/data/prune",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing pruneImages function.");
        return ComicService.getServiceInstance().pruneImages(request);
    }

    @FunctionName("loadData")
    public HttpResponseMessage loadData(
        @HttpTrigger(
            name = "loadData",
            route = "comics/data",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing loadData function.");
        ComicService comicService = ComicService.getServiceInstance();
        CsvToJsonConverter csvToJsonConverter = new CsvToJsonConverter(request.getBody().orElseThrow(), comicService);
        try {
            return csvToJsonConverter.loadGoCollectCsvData(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Data load failed to return results.", e);
        }
    }

}
