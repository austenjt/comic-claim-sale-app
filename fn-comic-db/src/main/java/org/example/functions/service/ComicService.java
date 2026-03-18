package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.ComicBook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ComicService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();

    private final CosmosContainer comicsContainer;

    private static ComicService SERVICE_INSTANCE;

    public static ComicService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new ComicService();
        }
        return SERVICE_INSTANCE;
    }

    public ComicService() {
        this.comicsContainer = CosmosDbClient.getInstance().getComicsContainer();
    }

    public List<ComicBook> getComicsList() {
        log.info("Calling getComicsList...");
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            "SELECT * FROM c", new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            result.add(nodeToComicBook(node));
        }
        log.info("getComicsList() returned {} items.", result.size());
        return result;
    }

    public List<ComicBook> getComicsList(int pageNumber, int pageSize) {
        log.info("Calling getComicsList with pageNumber: {} and pageSize: {}", pageNumber, pageSize);
        int offset = (pageNumber - 1) * pageSize;
        SqlQuerySpec querySpec = new SqlQuerySpec(
            "SELECT * FROM c OFFSET @offset LIMIT @limit",
            List.of(new SqlParameter("@offset", offset), new SqlParameter("@limit", pageSize)));
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            result.add(nodeToComicBook(node));
        }
        log.info("getComicsList() returned {} items for page {}.", result.size(), pageNumber);
        return result;
    }

    public List<String> getOrphanedImagesNames() {
        List<String> allCachedImages = ImageService.getServiceInstance().getImagesList();
        List<ComicBook> comicBooks = this.getComicsList();

        java.util.Set<String> usedImages = Stream.concat(
                comicBooks.stream().map(ComicBook::getSmallCachedImageId),
                comicBooks.stream().map(ComicBook::getLargeCachedImageId))
            .collect(Collectors.toSet());

        return allCachedImages.stream()
            .filter(cachedImage -> !usedImages.contains(cachedImage))
            .collect(Collectors.toList());
    }

    public List<String> deleteImages(List<String> deletionList) {
        List<String> deletedImages = new ArrayList<>();
        deletionList.remove("missing.png");
        deletionList.forEach(imageId -> {
            try {
                ImageService.getServiceInstance().deleteImage(imageId);
                deletedImages.add(imageId);
            } catch (Exception e) {
                deletedImages.add("Failed to delete image: " + imageId + ". Error: " + e.getMessage());
            }
        });
        return deletedImages;
    }

    /**
     * Prune any unused images stored in the comic database.
     *   Deletes any image that is not used within the comic database metadata.
     */
    public HttpResponseMessage pruneImages(HttpRequestMessage<Optional<String>> request) {
        List<String> responseDeleted;
        try {
            responseDeleted = this.deleteImages(this.getOrphanedImagesNames());
        } catch (Exception e) {
            log.error("Error pruning images.");
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain")
                .body("Unknown error occurred while pruning images.")
                .build();
        }
        return request.createResponseBuilder(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body(responseDeleted)
            .build();
    }

    /**
     * Fetches all comics and returns a top-level list where set containers have their
     * member comics embedded in the {@code items} field. Set members are excluded from
     * the top-level list — only containers (isSet=true) and standalone comics appear.
     * One Cosmos query; the grouping is done in memory.
     */
    public List<ComicBook> getComicsListEnriched() {
        List<ComicBook> all = getComicsList();

        // Group non-container members by collectionGroup
        Map<Integer, List<ComicBook>> membersByGroup = all.stream()
            .filter(c -> c.getCollectionGroup() != null
                      && c.getCollectionGroup() > 0
                      && !Boolean.TRUE.equals(c.getIsSet()))
            .collect(Collectors.groupingBy(ComicBook::getCollectionGroup));

        List<ComicBook> topLevel = new ArrayList<>();
        for (ComicBook comic : all) {
            boolean isRealSet = Boolean.TRUE.equals(comic.getIsSet())
                && comic.getCollectionGroup() != null
                && comic.getCollectionGroup() > 0;
            boolean isStandalone = comic.getCollectionGroup() == null
                || comic.getCollectionGroup() <= 0;

            if (isRealSet) {
                comic.setItems(membersByGroup.getOrDefault(comic.getCollectionGroup(), new ArrayList<>()));
                topLevel.add(comic);
            } else if (isStandalone) {
                topLevel.add(comic);
            }
            // Members (collectionGroup > 0 && !isSet) are embedded in their container
        }
        return topLevel;
    }

    public List<ComicBook> getComicsSearch(final String subString) {
        log.info("Calling getComicsSearch...");
        SqlQuerySpec querySpec = new SqlQuerySpec(
            "SELECT * FROM c WHERE CONTAINS(LOWER(c.title), LOWER(@sub))",
            List.of(new SqlParameter("@sub", subString)));
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            result.add(nodeToComicBook(node));
        }
        return result;
    }

    public List<ComicBook> getComicsByCollectionGroup(int collectionGroup) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.collectionGroup = @collectionGroup",
            List.of(new SqlParameter("@collectionGroup", collectionGroup)));
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            result.add(nodeToComicBook(node));
        }
        return result;
    }

    public Optional<ComicBook> findByCertificationId(String certificationId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.comicCondition.certificationId = @certId",
            List.of(new SqlParameter("@certId", certificationId)));
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        for (ObjectNode node : items) {
            return Optional.ofNullable(nodeToComicBook(node));
        }
        return Optional.empty();
    }

    public Optional<ComicBook> getComicById(int id) {
        String idStr = String.valueOf(id);
        try {
            ObjectNode node = comicsContainer.readItem(idStr, new PartitionKey(idStr), ObjectNode.class).getItem();
            return Optional.ofNullable(nodeToComicBook(node));
        } catch (Exception e) {
            log.warn("getComicById({}) not found: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public ComicBook updateComic(ComicBook updatedComicBook) {
        String idStr = String.valueOf(updatedComicBook.getId());
        ObjectNode node = comicBookToNode(updatedComicBook);
        comicsContainer.replaceItem(node, idStr, new PartitionKey(idStr), new CosmosItemRequestOptions());
        return updatedComicBook;
    }

    public ComicBook createComic(ComicBook newComicBookObj) throws IOException {
        if (newComicBookObj == null) {
            throw new IllegalArgumentException("Comic object cannot be null");
        }
        if (newComicBookObj.getId() <= 0) {
            int newComicId = getRandomId();
            log.info("Assigning new ID {} to the comic", newComicId);
            newComicBookObj.setId(newComicId);
        }
        Optional<ComicBook> existingComic = this.getComicsList().stream()
            .filter(comic -> Objects.equals(comic, newComicBookObj))
            .findAny();
        if (existingComic.isPresent()) {
            throw new IOException(String.format("Comic '%s' already exists in the database.", newComicBookObj.getTitle()));
        }
        String idStr = String.valueOf(newComicBookObj.getId());
        ObjectNode node = comicBookToNode(newComicBookObj);
        comicsContainer.createItem(node, new PartitionKey(idStr), new CosmosItemRequestOptions());
        return newComicBookObj;
    }

    public ComicBook uploadComic(ComicBook comic) {
        if (comic == null) {
            throw new IllegalArgumentException("Comic object cannot be null");
        }
        if (comic.getId() <= 0) {
            comic.setId(getRandomId());
        }
        String idStr = String.valueOf(comic.getId());
        ObjectNode node = comicBookToNode(comic);
        comicsContainer.upsertItem(node, new PartitionKey(idStr), new CosmosItemRequestOptions());
        return comic;
    }

    public void deleteComic(int id) {
        log.info("Delete comic by id {}", id);
        String idStr = String.valueOf(id);
        comicsContainer.deleteItem(idStr, new PartitionKey(idStr), new CosmosItemRequestOptions());
    }

    /** Deletes the isSet=true container and clears collectionGroup from all member comics. */
    public void deleteSet(int collectionGroup) {
        List<ComicBook> all = getComicsByCollectionGroup(collectionGroup);
        log.info("Deleting set with collectionGroup={}, affecting {} comics", collectionGroup, all.size());
        for (ComicBook comic : all) {
            if (Boolean.TRUE.equals(comic.getIsSet())) {
                deleteComic(comic.getId());
            } else {
                comic.setCollectionGroup(null);
                updateComic(comic);
            }
        }
    }

    /** Deletes ALL comics in a collectionGroup (container and members). Used when cleaning up fulfilled sets. */
    public void deleteSetFully(int collectionGroup) {
        List<ComicBook> all = getComicsByCollectionGroup(collectionGroup);
        log.info("Fully deleting set with collectionGroup={}, removing {} comics", collectionGroup, all.size());
        for (ComicBook comic : all) {
            deleteComic(comic.getId());
        }
    }

    private ObjectNode comicBookToNode(ComicBook comic) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(comic);
        node.put("id", String.valueOf(comic.getId()));
        return node;
    }

    private ComicBook nodeToComicBook(ObjectNode node) {
        if (node.has("id")) {
            node.put("id", node.get("id").asInt());
        }
        try {
            return OBJECT_MAPPER.treeToValue(node, ComicBook.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing Cosmos document into comic.", e);
            return null;
        }
    }

    /** Deletes all comics and all images. Used during database reset. */
    public void deleteAllComicsAndImages() {
        // Delete all images first
        List<String> imageNames = ImageService.getServiceInstance().getImagesList();
        for (String name : imageNames) {
            try {
                ImageService.getServiceInstance().deleteImage(name);
            } catch (Exception e) {
                log.warn("Failed to delete image {}: {}", name, e.getMessage());
            }
        }
        log.info("Deleted {} images.", imageNames.size());

        // Delete all comics
        SqlQuerySpec query = new SqlQuerySpec("SELECT c.id FROM c");
        for (ObjectNode node : comicsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            String id = node.get("id").asText();
            try {
                comicsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
            } catch (Exception e) {
                log.warn("Failed to delete comic {}: {}", id, e.getMessage());
            }
        }
        log.info("All comics deleted.");
    }

    private int getRandomId() {
        Random rand = new Random();
        return 10000000 + rand.nextInt(90000000);
    }

}
