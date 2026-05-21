package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.ComicAuditLog;
import org.example.functions.model.PagedResponse;
import org.example.functions.util.Mappers;
import org.example.functions.model.ComicBook;
import org.example.functions.model.FieldChange;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ComicService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.WITH_JAVA_TIME;

    private final CosmosContainer comicsContainer;

    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final ComicService INSTANCE = new ComicService();
    }

    public static ComicService getServiceInstance() {
        return Holder.INSTANCE;
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

    /**
     * Returns a paginated, filtered, sorted page of top-level comics (standalone + SET containers).
     * Set members are fetched separately and embedded into their container's items[].
     *
     * NOTE: Cosmos DB requires composite indexes for WHERE + ORDER BY on different fields.
     * Add composite indexes in the Azure Portal for (isForSale, _ts), (isForSale, title),
     * and (isForSale, salePrice) before deploying.
     */
    public PagedResponse<ComicBook> getTopLevelComicsPaged(
            int pageNumber, int pageSize, String sort, boolean admin) {
        int offset = (pageNumber - 1) * pageSize;

        // WHERE: top-level only (standalone comics + SET containers, no members)
        StringBuilder where = new StringBuilder();
        where.append("(c.docType = 'SET'")
             .append(" OR NOT IS_DEFINED(c.collectionGroup)")
             .append(" OR c.collectionGroup = null")
             .append(" OR c.collectionGroup <= 0)");
        where.append(" AND (NOT IS_DEFINED(c.dateSold) OR c.dateSold = null OR c.dateSold = '')");
        // Exclude WANTED comics — they appear on the Trade Board, not the dashboard.
        // Legacy documents without listingType are never WANTED, so they always pass this filter.
        where.append(" AND (NOT IS_DEFINED(c.listingType) OR c.listingType != 'WANTED')");
        String whereClause = where.toString();

        // Price sorts: a SET's effective price is the sum of its members' prices, which isn't a stored field.
        // Cosmos can only sort by c.salePrice on the container document (null for SETs), so we must fetch
        // everything, enrich all SETs, sort by computed price in Java, then paginate.
        boolean isPriceSort = "highest-price".equals(sort) || "lowest-price".equals(sort);
        if (isPriceSort) {
            List<ComicBook> all = new ArrayList<>();
            String sql = "SELECT * FROM c WHERE " + whereClause;
            for (ObjectNode node : comicsContainer.queryItems(sql, new CosmosQueryRequestOptions(), ObjectNode.class)) {
                ComicBook cb = nodeToComicBook(node);
                if (cb != null) all.add(cb);
            }
            enrichSets(all);

            Comparator<ComicBook> cmp = Comparator.comparing(ComicService::effectivePrice);
            if ("highest-price".equals(sort)) cmp = cmp.reversed();
            all.sort(cmp);

            int totalCount = all.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            List<ComicBook> pageItems = new ArrayList<>(
                    all.subList(Math.min(offset, totalCount), Math.min(offset + pageSize, totalCount)));
            log.info("getTopLevelComicsPaged (price sort): page={}, size={}, total={}, sort={}", pageNumber, pageSize, totalCount, sort);
            return PagedResponse.<ComicBook>builder()
                    .items(pageItems)
                    .totalCount(totalCount)
                    .pageNumber(pageNumber)
                    .pageSize(pageSize)
                    .totalPages(totalPages)
                    .build();
        }

        // Count query
        String countSql = "SELECT VALUE COUNT(1) FROM c WHERE " + whereClause;
        int totalCount = 0;
        for (Integer n : comicsContainer.queryItems(countSql, new CosmosQueryRequestOptions(), Integer.class)) {
            totalCount = n;
            break;
        }

        // Page query
        String orderBy = sortToOrderBy(sort);
        String pageSql = "SELECT * FROM c WHERE " + whereClause
                + " ORDER BY " + orderBy
                + " OFFSET @offset LIMIT @limit";
        SqlQuerySpec pageSpec = new SqlQuerySpec(pageSql,
                List.of(new SqlParameter("@offset", offset), new SqlParameter("@limit", pageSize)));

        List<ComicBook> pageItems = new ArrayList<>();
        for (ObjectNode node : comicsContainer.queryItems(pageSpec, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            ComicBook cb = nodeToComicBook(node);
            if (cb != null) pageItems.add(cb);
        }

        enrichSets(pageItems);

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        log.info("getTopLevelComicsPaged: page={}, size={}, total={}, sort={}", pageNumber, pageSize, totalCount, sort);
        return PagedResponse.<ComicBook>builder()
                .items(pageItems)
                .totalCount(totalCount)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Fetches member comics for all SET containers in the list and attaches them to items[].
     */
    private void enrichSets(List<ComicBook> topLevel) {
        List<Integer> setGroups = topLevel.stream()
                .filter(c -> "SET".equals(c.getDocType())
                        && c.getCollectionGroup() != null
                        && c.getCollectionGroup() > 0)
                .map(ComicBook::getCollectionGroup)
                .collect(Collectors.toList());
        if (setGroups.isEmpty()) return;

        String inClause = setGroups.stream().map(String::valueOf).collect(Collectors.joining(", "));
        // NOT (c.docType = 'SET') correctly handles undefined docType (unlike != which returns false for undefined)
        String membersSql = "SELECT * FROM c WHERE c.collectionGroup IN (" + inClause + ") AND NOT (c.docType = 'SET')";
        List<ComicBook> allMembers = new ArrayList<>();
        for (ObjectNode node : comicsContainer.queryItems(membersSql, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            ComicBook m = nodeToComicBook(node);
            if (m != null) allMembers.add(m);
        }
        Map<Integer, List<ComicBook>> membersByGroup = allMembers.stream()
                .collect(Collectors.groupingBy(ComicBook::getCollectionGroup));
        for (ComicBook comic : topLevel) {
            if ("SET".equals(comic.getDocType()) && comic.getCollectionGroup() != null) {
                comic.setItems(membersByGroup.getOrDefault(comic.getCollectionGroup(), new ArrayList<>()));
            }
        }
    }

    /**
     * Effective price for sorting: sum of member prices for SETs, own salePrice for individual comics.
     * Null prices are treated as zero.
     */
    private static BigDecimal effectivePrice(ComicBook c) {
        if ("SET".equals(c.getDocType()) && c.getItems() != null) {
            return c.getItems().stream()
                    .map(ComicBook::getSalePrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return c.getSalePrice() != null ? c.getSalePrice() : BigDecimal.ZERO;
    }

    private String sortToOrderBy(String sort) {
        if (sort == null) return "c._ts ASC";
        return switch (sort) {
            case "newest-first"  -> "c._ts DESC";
            case "a-z"           -> "c.title ASC";
            case "z-a"           -> "c.title DESC";
            default              -> "c._ts ASC"; // oldest-first, claimed-first
        };
    }

    /**
     * Returns all top-level comics whose {@code listingType} is {@code WANTED}, for the Trade Board.
     * Only documents explicitly set to WANTED are returned — legacy documents without a listingType
     * are never treated as WANTED.
     */
    public List<ComicBook> getWantedComics() {
        String sql = "SELECT * FROM c WHERE c.listingType = 'WANTED'" +
                     " AND (NOT IS_DEFINED(c.dateSold) OR c.dateSold = null OR c.dateSold = '')" +
                     " ORDER BY c._ts DESC";
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : comicsContainer.queryItems(sql, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            ComicBook cb = nodeToComicBook(node);
            if (cb != null) result.add(cb);
        }
        log.info("getWantedComics() returned {} items.", result.size());
        return result;
    }

    public List<String> getUniqueSeries() {
        log.info("Calling getUniqueSeries...");
        CosmosPagedIterable<String> items = comicsContainer.queryItems(
            "SELECT DISTINCT VALUE c.series FROM c WHERE IS_DEFINED(c.series) AND c.series != null AND c.series != ''",
            new CosmosQueryRequestOptions(), String.class);
        List<String> result = new ArrayList<>();
        for (String s : items) {
            if (s != null && !s.isBlank()) result.add(s);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        log.info("getUniqueSeries() returned {} unique series.", result.size());
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
     * the top-level list — only containers (docType="SET") and standalone comics appear.
     * One Cosmos query; the grouping is done in memory.
     */
    public List<ComicBook> getComicsListEnriched() {
        List<ComicBook> all = getComicsList();

        // Group non-container members by collectionGroup
        Map<Integer, List<ComicBook>> membersByGroup = all.stream()
            .filter(c -> c.getCollectionGroup() != null
                      && c.getCollectionGroup() > 0
                      && !"SET".equals(c.getDocType()))
            .collect(Collectors.groupingBy(ComicBook::getCollectionGroup));

        List<ComicBook> topLevel = new ArrayList<>();
        for (ComicBook comic : all) {
            boolean isRealSet = "SET".equals(comic.getDocType())
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
            // Members (collectionGroup > 0 && docType != "SET") are embedded in their container
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

    public List<ComicBook> getSetsList() {
        log.info("Calling getSetsList...");
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            "SELECT * FROM c WHERE c.docType = 'SET'", new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            ComicBook cb = nodeToComicBook(node);
            if (cb != null) result.add(cb);
        }
        log.info("getSetsList() returned {} items.", result.size());
        return result;
    }

    public List<ComicBook> getSingleComics() {
        log.info("Calling getSingleComics...");
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            "SELECT * FROM c WHERE NOT (c.docType = 'SET')" +
            " AND (NOT IS_DEFINED(c.collectionGroup) OR c.collectionGroup = null OR c.collectionGroup <= 0)",
            new CosmosQueryRequestOptions(), ObjectNode.class);
        List<ComicBook> result = new ArrayList<>();
        for (ObjectNode node : items) {
            ComicBook cb = nodeToComicBook(node);
            if (cb != null) result.add(cb);
        }
        log.info("getSingleComics() returned {} items.", result.size());
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

    /**
     * Bundles a comic with its Cosmos {@code _etag} so callers can pass the eTag back to
     * {@link #updateComic(ComicBook, String, String)} for optimistic concurrency control.
     */
    public record ComicWithETag(ComicBook comic, String eTag) {}

    /**
     * Reads a comic and returns it together with the Cosmos {@code _etag} captured from the
     * read response. Use this when you need to perform an optimistic-locked update so that a
     * concurrent writer can be detected.
     */
    public Optional<ComicWithETag> getComicByIdWithETag(int id) {
        String idStr = String.valueOf(id);
        try {
            CosmosItemResponse<ObjectNode> response =
                comicsContainer.readItem(idStr, new PartitionKey(idStr), ObjectNode.class);
            ComicBook comic = nodeToComicBook(response.getItem());
            if (comic == null) return Optional.empty();
            return Optional.of(new ComicWithETag(comic, response.getETag()));
        } catch (Exception e) {
            log.warn("getComicByIdWithETag({}) not found: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public ComicBook updateComic(ComicBook updatedComicBook) {
        return replaceComic(updatedComicBook, null);
    }

    /**
     * Updates a comic and writes an audit log entry recording which fields changed and who changed them.
     * @param updatedComicBook the new state of the comic
     * @param editedBy         email of the admin, or a system label like "system:fulfill"
     */
    public ComicBook updateComic(ComicBook updatedComicBook, String editedBy) {
        return updateComic(updatedComicBook, editedBy, null);
    }

    /**
     * Updates a comic with optional optimistic-concurrency protection. When {@code ifMatchETag}
     * is non-null and the stored document has been modified since it was read, Cosmos returns
     * 412 Precondition Failed; that is translated into an {@link IllegalStateException} so the
     * trigger layer maps it to a 409 Conflict response.
     *
     * @param updatedComicBook the new state of the comic
     * @param editedBy         email of the admin, or a system label like "system:fulfill"
     * @param ifMatchETag      the {@code _etag} captured at read time, or {@code null} to skip the check
     */
    public ComicBook updateComic(ComicBook updatedComicBook, String editedBy, String ifMatchETag) {
        ComicBook oldComic = getComicById(updatedComicBook.getId()).orElse(null);
        // Preserve viewCount — it is system-maintained and must not be overwritten by admin saves
        if (oldComic != null && oldComic.getViewCount() != null) {
            updatedComicBook.setViewCount(oldComic.getViewCount());
        }
        replaceComic(updatedComicBook, ifMatchETag);
        if (oldComic != null && editedBy != null) {
            List<FieldChange> changes = diffComics(oldComic, updatedComicBook);
            if (!changes.isEmpty()) {
                ComicAuditLog entry = ComicAuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .comicId(String.valueOf(updatedComicBook.getId()))
                    .comicTitle(updatedComicBook.getTitle())
                    .editedBy(editedBy)
                    .editedAt(Instant.now().toString())
                    .changes(changes)
                    .build();
                AuditService.getServiceInstance().writeAuditLog(entry);
            }
        }
        return updatedComicBook;
    }

    private List<FieldChange> diffComics(ComicBook oldComic, ComicBook newComic) {
        ObjectNode oldNode = OBJECT_MAPPER.valueToTree(oldComic);
        ObjectNode newNode = OBJECT_MAPPER.valueToTree(newComic);
        List<FieldChange> changes = new ArrayList<>();
        Iterator<String> fields = newNode.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (field.startsWith("_") || field.equals("id") || field.equals("items") || field.equals("viewCount")) continue;
            JsonNode oldVal = oldNode.get(field);
            JsonNode newVal = newNode.get(field);
            String oldStr = (oldVal != null && !oldVal.isNull()) ? oldVal.toString() : null;
            String newStr = (newVal != null && !newVal.isNull()) ? newVal.toString() : null;
            if (!Objects.equals(oldStr, newStr)) {
                changes.add(FieldChange.builder().field(field).oldValue(oldStr).newValue(newStr).build());
            }
        }
        return changes;
    }

    /**
     * Per-IP deduplication cache. Key = "viewerKey:comicId", value = timestamp of last counted view.
     * Lives in memory only — resets on cold start, which is acceptable.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> VIEW_SEEN =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VIEW_WINDOW_MS = 24L * 60 * 60 * 1000; // 24 hours

    /**
     * Increments viewCount for a comic, skipping the write if {@code viewerKey} (typically a
     * hashed client IP) already viewed this comic within the last 24 hours.
     * Does not write an audit log entry — view tracking is a system operation.
     * @return the current viewCount (incremented or unchanged), or 0 if the comic was not found
     */
    public int incrementViewCount(int id, String viewerKey) {
        Optional<ComicBook> existing = getComicById(id);
        if (existing.isEmpty()) return 0;
        ComicBook comic = existing.get();
        int currentCount = comic.getViewCount() != null ? comic.getViewCount() : 0;

        String cacheKey = viewerKey + ":" + id;
        long now = System.currentTimeMillis();
        Long lastSeen = VIEW_SEEN.get(cacheKey);

        if (lastSeen != null && (now - lastSeen) < VIEW_WINDOW_MS) {
            return currentCount; // same viewer within 24 h — no increment
        }

        // Lazy eviction: remove entries older than the window to keep the map bounded
        VIEW_SEEN.entrySet().removeIf(e -> (now - e.getValue()) >= VIEW_WINDOW_MS);
        VIEW_SEEN.put(cacheKey, now);

        comic.setViewCount(currentCount + 1);
        updateComic(comic);
        return currentCount + 1;
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

    /** Deletes the docType="SET" container and clears collectionGroup from all member comics. */
    public void deleteSet(int collectionGroup) {
        List<ComicBook> all = getComicsByCollectionGroup(collectionGroup);
        log.info("Deleting set with collectionGroup={}, affecting {} comics", collectionGroup, all.size());
        for (ComicBook comic : all) {
            if ("SET".equals(comic.getDocType())) {
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

    /**
     * Performs the actual {@code replaceItem} call against Cosmos, optionally with an
     * If-Match eTag. A 412 Precondition Failed is mapped to {@link IllegalStateException}
     * so callers and the trigger layer can treat concurrency conflicts uniformly.
     */
    private ComicBook replaceComic(ComicBook comic, String ifMatchETag) {
        String idStr = String.valueOf(comic.getId());
        ObjectNode node = comicBookToNode(comic);
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        if (ifMatchETag != null) {
            options.setIfMatchETag(ifMatchETag);
        }
        try {
            comicsContainer.replaceItem(node, idStr, new PartitionKey(idStr), options);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 412) {
                throw new IllegalStateException(
                    "Comic " + idStr + " was modified by another request. Please refresh and try again.", e);
            }
            throw e;
        }
        return comic;
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

    /** Zeroes viewCount on every comic that has been viewed, and clears the in-memory dedup cache. */
    public int resetAllViewCounts() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE IS_DEFINED(c.viewCount) AND c.viewCount > 0");
        CosmosPagedIterable<ObjectNode> items = comicsContainer.queryItems(
            query, new CosmosQueryRequestOptions(), ObjectNode.class);
        int count = 0;
        for (ObjectNode node : items) {
            node.put("viewCount", 0);
            String id = node.get("id").asText();
            try {
                comicsContainer.replaceItem(node, id, new PartitionKey(id), new CosmosItemRequestOptions());
                count++;
            } catch (Exception e) {
                log.warn("Failed to reset viewCount for comic {}: {}", id, e.getMessage());
            }
        }
        VIEW_SEEN.clear();
        log.info("Reset viewCount for {} comics.", count);
        return count;
    }

    private int getRandomId() {
        Random rand = new Random();
        return 10000000 + rand.nextInt(90000000);
    }

}
