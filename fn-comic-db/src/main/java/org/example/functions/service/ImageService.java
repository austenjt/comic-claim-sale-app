package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.CosmosImage;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Slf4j
public class ImageService {

    private final CosmosContainer imagesContainer;

    private static ImageService SERVICE_INSTANCE;

    public static ImageService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new ImageService();
        }
        return SERVICE_INSTANCE;
    }

    public ImageService() {
        this.imagesContainer = CosmosDbClient.getInstance().getImagesContainer();
    }

    public List<String> getImagesList() {
        log.info("Calling getImagesList...");
        CosmosPagedIterable<ObjectNode> items = imagesContainer.queryItems(
            "SELECT c.id FROM c", new CosmosQueryRequestOptions(), ObjectNode.class);
        List<String> result = new ArrayList<>();
        for (ObjectNode node : items) {
            result.add(node.get("id").asText());
        }
        log.info("getImagesList() returned {} items.", result.size());
        return result;
    }

    public byte[] getImageByName(String imageName) {
        log.info("Calling getImageByName for name: {}", imageName);
        try {
            CosmosImage image = imagesContainer.readItem(imageName, new PartitionKey(imageName), CosmosImage.class).getItem();
            if (image == null || image.getData() == null) {
                log.info("Image with name {} not found.", imageName);
                return null;
            }
            return Base64.getDecoder().decode(image.getData());
        } catch (Exception e) {
            log.error("Error retrieving image with name {}: {}", imageName, e.getMessage(), e);
            return null;
        }
    }

    public String createImage(String imageName, byte[] imageData) {
        log.info("Creating image with name: {} and size of {} bytes.", imageName, imageData.length);
        try {
            String contentType = detectContentType(imageData);
            String encodedData = Base64.getEncoder().encodeToString(imageData);
            CosmosImage image = CosmosImage.builder()
                .id(imageName)
                .data(encodedData)
                .contentType(contentType)
                .build();
            imagesContainer.createItem(image, new PartitionKey(imageName), new CosmosItemRequestOptions());
            return "Image created successfully.";
        } catch (Exception e) {
            String errorMessage = String.format("Error creating image with name %s: %s", imageName, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    public String updateImage(String imageName, byte[] imageData, boolean createIfNotExists) {
        return updateImage(imageName, imageData, createIfNotExists, null, null, null, null);
    }

    public String updateImage(String imageName, byte[] imageData, boolean createIfNotExists,
                             Integer comicId, String comicTitle, String comicSeries, String comicNumber) {
        log.info("Updating image with name: {} and size of {} bytes.", imageName, imageData.length);
        try {
            String contentType = detectContentType(imageData);
            String encodedData = Base64.getEncoder().encodeToString(imageData);
            CosmosImage image = CosmosImage.builder()
                .id(imageName)
                .data(encodedData)
                .contentType(contentType)
                .comicId(comicId)
                .comicTitle(comicTitle)
                .comicSeries(comicSeries)
                .comicNumber(comicNumber)
                .build();
            if (createIfNotExists) {
                imagesContainer.upsertItem(image, new PartitionKey(imageName), new CosmosItemRequestOptions());
            } else {
                imagesContainer.replaceItem(image, imageName, new PartitionKey(imageName), new CosmosItemRequestOptions());
            }
            return "Image updated successfully.";
        } catch (Exception e) {
            String errorMessage = String.format("Error updating image with name %s: %s", imageName, e.getMessage());
            throw new RuntimeException(errorMessage, e);
        }
    }

    public String deleteImage(String imageName) {
        log.info("Deleting image with name: {}", imageName);
        try {
            imagesContainer.deleteItem(imageName, new PartitionKey(imageName), new CosmosItemRequestOptions());
            return String.format("Successfully deleted image with name %s", imageName);
        } catch (Exception e) {
            String errorMessage = String.format("Error deleting image with name %s: %s", imageName, e.getMessage());
            throw new RuntimeException(errorMessage, e);
        }
    }

    private String detectContentType(byte[] imageData) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
            String contentType = URLConnection.guessContentTypeFromStream(inputStream);
            return contentType != null ? contentType : "image/jpeg";
        } catch (Exception e) {
            log.error("Error detecting image type.  Defaulting to .png .");
            return "image/png";
        }
    }

}
