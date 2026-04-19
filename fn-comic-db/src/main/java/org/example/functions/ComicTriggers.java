package org.example.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.example.functions.model.ComicBook;
import org.example.functions.model.PagedResponse;
import org.example.functions.model.User;
import org.example.functions.service.ArchiveService;
import org.example.functions.service.CartService;
import org.example.functions.service.ComicService;
import org.example.functions.service.ImageService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.EnvHelper;
import org.example.functions.util.CsvToJsonConverter;
import org.example.functions.util.Mappers;
import org.example.functions.util.Views;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ComicTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.WITH_JAVA_TIME;

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
        boolean admin = AuthHelper.isAdminRequest(request);
        ComicService comicService = ComicService.getServiceInstance();
        try {
            if (pageNumberStr != null) {
                int pageNumber = Integer.parseInt(pageNumberStr);
                int pageSize = EnvHelper.getDashboardPageSize();
                String sort = request.getQueryParameters().getOrDefault("sort", "oldest-first");
                boolean onlyPriced = "true".equalsIgnoreCase(request.getQueryParameters().get("onlyPriced"));
                boolean onlyBiddable = "true".equalsIgnoreCase(request.getQueryParameters().get("onlyBiddable"));
                PagedResponse<ComicBook> paged = comicService.getTopLevelComicsPaged(pageNumber, pageSize, sort, onlyPriced, onlyBiddable, admin);
                String body = admin
                    ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(paged)
                    : OBJECT_MAPPER.writerWithView(Views.Public.class).writeValueAsString(paged);
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "*")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build();
            } else {
                // Non-paginated path: used by admin tools and the in-memory cache warm-up
                List<ComicBook> comicBookData = comicService.getComicsListEnriched();
                String body = admin
                    ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(comicBookData)
                    : OBJECT_MAPPER.writerWithView(Views.Public.class).writeValueAsString(comicBookData);
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "*")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build();
            }
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

    @FunctionName("getSeriesList")
    public HttpResponseMessage getSeriesList(
        @HttpTrigger(
            name = "getSeriesList",
            route = "comics/series",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getSeriesList function.");
        try {
            List<String> series = ComicService.getServiceInstance().getUniqueSeries();
            String body = OBJECT_MAPPER.writeValueAsString(series);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("getSeriesList failed.", e);
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
        boolean admin = AuthHelper.isAdminRequest(request);
        ComicService comicService = ComicService.getServiceInstance();
        Optional<ComicBook> matchingComic = comicService.getComicById(Integer.parseInt(id));
        if (matchingComic.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "*")
                .header("Content-Type", "text/plain")
                .body(String.format("Comic with id %s not found.", id))
                .build();
        }
        ComicBook comic = matchingComic.get();
        if ("SET".equals(comic.getDocType()) && comic.getCollectionGroup() != null && comic.getCollectionGroup() > 0) {
            List<ComicBook> members = comicService.getComicsByCollectionGroup(comic.getCollectionGroup())
                .stream()
                .filter(m -> !"SET".equals(m.getDocType()))
                .collect(Collectors.toList());
            comic.setItems(members);
        }
        try {
            String body = admin
                ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(comic)
                : OBJECT_MAPPER.writerWithView(Views.Public.class).writeValueAsString(comic);
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
        User admin = AuthHelper.requireAdmin(request);
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
        String editedBy = admin != null ? admin.getEmail() : null;
        ComicBook successfulUpdate = comicService.updateComic(updatedComicBook, editedBy);
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
                // Block deletion of bid-sold items
                if (existingComic.get().getBiddingState().isSold()) {
                    return request.createResponseBuilder(HttpStatus.CONFLICT)
                        .header("Content-Type", "text/plain")
                        .body("Cannot delete a comic that was sold via bidding.")
                        .build();
                }
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

    @FunctionName("deleteSet")
    public HttpResponseMessage deleteSet(
        @HttpTrigger(
            name = "deleteSet",
            route = "sets/{collectionGroup}",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request, @BindingName("collectionGroup") String collectionGroupStr)
    {
        log.info("Processing deleteSet function for collectionGroup={}", collectionGroupStr);
        if (!AuthHelper.isAdminRequest(request)) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .header("Access-Control-Allow-Origin", "*")
                .body("Admin access required.")
                .build();
        }
        try {
            int collectionGroup = Integer.parseInt(collectionGroupStr);
            if (CartService.getServiceInstance().isSetClaimed(collectionGroup)) {
                return request.createResponseBuilder(HttpStatus.CONFLICT)
                    .header("Access-Control-Allow-Origin", "*")
                    .body("This set is currently claimed by a user. Release all items before deleting.")
                    .build();
            }
            if (ArchiveService.getServiceInstance().hasArchivedOrderForGroup(collectionGroup)) {
                return request.createResponseBuilder(HttpStatus.CONFLICT)
                    .header("Access-Control-Allow-Origin", "*")
                    .body("This set has a fulfilled archived order. Use 'Full Delete' on the archived order to remove the set.")
                    .build();
            }
            ComicService.getServiceInstance().deleteSet(collectionGroup);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(String.format("{ \"deleted\": true, \"collectionGroup\": %d }", collectionGroup))
                .build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.I_AM_A_TEAPOT)
                .header("Access-Control-Allow-Origin", "*")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
    }

    @FunctionName("deleteSetFully")
    public HttpResponseMessage deleteSetFully(
        @HttpTrigger(
            name = "deleteSetFully",
            route = "sets/{collectionGroup}/full",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request, @BindingName("collectionGroup") String collectionGroupStr)
    {
        log.info("Processing deleteSetFully function for collectionGroup={}", collectionGroupStr);
        if (!AuthHelper.isAdminRequest(request)) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .header("Access-Control-Allow-Origin", "*")
                .body("Admin access required.")
                .build();
        }
        try {
            int collectionGroup = Integer.parseInt(collectionGroupStr);
            if (CartService.getServiceInstance().isSetClaimed(collectionGroup)) {
                return request.createResponseBuilder(HttpStatus.CONFLICT)
                    .header("Access-Control-Allow-Origin", "*")
                    .body("This set is currently claimed by a user. Release all items before deleting.")
                    .build();
            }
            ComicService.getServiceInstance().deleteSetFully(collectionGroup);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(String.format("{ \"deleted\": true, \"collectionGroup\": %d }", collectionGroup))
                .build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.I_AM_A_TEAPOT)
                .header("Access-Control-Allow-Origin", "*")
                .body(ExceptionUtils.getMessage(e))
                .build();
        }
    }

    @FunctionName("getSets")
    public HttpResponseMessage getSets(
        @HttpTrigger(
            name = "getSets",
            route = "sets",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getSets function.");
        if (!AuthHelper.isAdminRequest(request)) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .header("Access-Control-Allow-Origin", "*")
                .body("Admin access required.")
                .build();
        }
        try {
            List<ComicBook> sets = ComicService.getServiceInstance().getSetsList();
            String body = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sets);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        } catch (JsonProcessingException e) {
            log.error("Error in getSets.", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .body(e.getMessage())
                .build();
        }
    }

    @FunctionName("getNextSetGroupId")
    public HttpResponseMessage getNextSetGroupId(
        @HttpTrigger(
            name = "getNextSetGroupId",
            route = "sets/next-group-id",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getNextSetGroupId function.");
        if (!AuthHelper.isAdminRequest(request)) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .header("Access-Control-Allow-Origin", "*")
                .body("Admin access required.")
                .build();
        }
        try {
            // Max across currently active sets
            int activeMax = ComicService.getServiceInstance().getSetsList().stream()
                .filter(s -> s.getCollectionGroup() != null)
                .mapToInt(ComicBook::getCollectionGroup)
                .max().orElse(0);
            // Max across all archived (fulfilled) orders
            int archivedMax = ArchiveService.getServiceInstance().getMaxCollectionGroup();
            int next = Math.max(activeMax, archivedMax) + 1;
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body("{\"nextGroupId\":" + next + "}")
                .build();
        } catch (Exception e) {
            log.error("Error in getNextSetGroupId.", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .body(e.getMessage())
                .build();
        }
    }

    @FunctionName("getSingleComics")
    public HttpResponseMessage getSingleComics(
        @HttpTrigger(
            name = "getSingleComics",
            route = "comics/single",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getSingleComics function.");
        if (!AuthHelper.isAdminRequest(request)) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .header("Access-Control-Allow-Origin", "*")
                .body("Admin access required.")
                .build();
        }
        try {
            List<ComicBook> singles = ComicService.getServiceInstance().getSingleComics();
            String body = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(singles);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        } catch (JsonProcessingException e) {
            log.error("Error in getSingleComics.", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Access-Control-Allow-Origin", "*")
                .body(e.getMessage())
                .build();
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
        String collectionGroupStr = request.getQueryParameters().get("collectionGroup");
        Integer collectionGroup = null;
        if (collectionGroupStr != null) {
            try { collectionGroup = Integer.parseInt(collectionGroupStr); } catch (NumberFormatException ignored) {}
        }
        boolean setPriceToPricePaid = "true".equalsIgnoreCase(request.getQueryParameters().get("setPriceToPricePaid"));
        boolean markForSale = !"false".equalsIgnoreCase(request.getQueryParameters().get("markForSale"));
        try {
            return csvToJsonConverter.loadGoCollectCsvData(request, collectionGroup, setPriceToPricePaid, markForSale);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Data load failed to return results.", e);
        }
    }

}
