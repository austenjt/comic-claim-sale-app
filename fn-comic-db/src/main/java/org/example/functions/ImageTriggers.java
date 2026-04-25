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
import org.apache.commons.fileupload.MultipartStream;
import org.example.functions.model.ComicBook;
import org.example.functions.model.ComicNumber;
import org.example.functions.service.ComicService;
import org.example.functions.service.ImageResizeService;
import org.example.functions.service.ImageService;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
public class ImageTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.WITH_JAVA_TIME;

    @FunctionName("getAllImageNames")
    public HttpResponseMessage getAllImageNames(
        @HttpTrigger(
            name = "getAllImageNames",
            route = "images",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request) {
        log.info("Processing getAllImageNames function...");
        try {
            ImageService imageService = ImageService.getServiceInstance();

            List<String> imageData = imageService.getImagesList();
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(imageData)
                .build();
        } catch (Exception e) {
            log.error("Severe error processing getAllImageNames.", e);
            return HttpHelper.errorResponse(request, e);
        }
    }

    @FunctionName("getImageByName")
    public HttpResponseMessage getImageByName(
        @HttpTrigger(
            name = "getImageByName",
            route = "images/{name}",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("name") String name) {
        log.info("Processing getImageById {} function...", name);
        try {
            ImageService imageService = ImageService.getServiceInstance();
            byte[] imageData = imageService.getImageByName(name);

            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", getMimeType(name))                .body(imageData)
                .build();
        } catch (Exception e) {
            log.error("Severe error processing getImageByName.", e);
            return HttpHelper.errorResponse(request, e);
        }
    }

    @FunctionName("updateImage")
    public HttpResponseMessage updateImage(
        @HttpTrigger(
            name = "updateImage",
            route = "images/{name}",
            methods = {HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<byte[]>> request,
        @BindingName("name") String name)
    {
        try {
            ImageService imageService = ImageService.getServiceInstance();
            boolean force = Boolean.parseBoolean(request.getQueryParameters().get("force"));

            byte[] requestBody = request.getBody().orElseThrow(() -> new IllegalArgumentException("Invalid argument in PUT body."));
            ByteArrayInputStream content = new ByteArrayInputStream(requestBody);
            String boundaryContents = extractBoundary(request.getHeaders().get("Content-Type".toLowerCase()));

            MultipartStream multipartStream = new MultipartStream(content, boundaryContents.getBytes());
            List<byte[]> files = new ArrayList<>();

            boolean nextPart = multipartStream.skipPreamble();
            while (nextPart) {
                String header = multipartStream.readHeaders();
                if (header.contains("Content-Disposition: form-data; name=\"file\"")) {
                    ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                    multipartStream.readBodyData(fileContent);
                    files.add(fileContent.toByteArray());
                }
                nextPart = multipartStream.readBoundary();
            }

            if (files.isEmpty()) {
                throw new RuntimeException("No multi-part form file was found.");
            }

            byte[] imageData = files.get(0); // Assuming first file is the image
            log.info("Before upload, image size is: " + imageData.length);
            String result = imageService.updateImage(name, imageData, force);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "text/plain")
                .body(result)
                .build();
        } catch (Exception e) {
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .header("Content-Type", "text/plain")
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    private String extractBoundary(String contentType) {
        String boundary = null;
        if (contentType != null && contentType.contains("boundary=")) {
            // Split by 'boundary=' and take the second part
            String[] parts = contentType.split("boundary=");
            if (parts.length > 1) {
                boundary = parts[1].trim();
                // Remove any leading or trailing whitespace, CR, LF, or quotes
                boundary = boundary.replaceAll("^[\r\n\"']+", "").replaceAll("[\r\n\"']+$", "");
            }
        }
        return boundary;
    }



    @FunctionName("uploadComicImage")
    public HttpResponseMessage uploadComicImage(
        @HttpTrigger(
            name = "uploadComicImage",
            route = "comics/{id}/image",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<byte[]>> request,
        @BindingName("id") String id)
    {
        log.info("Processing uploadComicImage for comic id: {}", id);
        try {
            int comicId = Integer.parseInt(id);
            ImageService imageService = ImageService.getServiceInstance();
            ImageResizeService imageResizeService = ImageResizeService.getServiceInstance();
            ComicService comicService = ComicService.getServiceInstance();

            // Parse multipart body to extract image bytes
            byte[] requestBody = request.getBody().orElseThrow(() -> new IllegalArgumentException("No body in image upload request."));
            String boundary = extractBoundary(request.getHeaders().get("content-type"));
            MultipartStream multipartStream = new MultipartStream(new ByteArrayInputStream(requestBody), boundary.getBytes());
            List<byte[]> files = new ArrayList<>();
            boolean nextPart = multipartStream.skipPreamble();
            while (nextPart) {
                String header = multipartStream.readHeaders();
                if (header.contains("Content-Disposition: form-data; name=\"file\"")) {
                    ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                    multipartStream.readBodyData(fileContent);
                    files.add(fileContent.toByteArray());
                }
                nextPart = multipartStream.readBoundary();
            }
            if (files.isEmpty()) {
                throw new RuntimeException("No file part found in multipart upload.");
            }
            byte[] imageData = files.get(0);
            imageData = imageResizeService.resizeIfTooTall(imageData, 900);

            ComicBook comic = comicService.getComicById(comicId)
                .orElseThrow(() -> new RuntimeException("Comic not found with id: " + comicId));
            String comicNumberStr = comicNumberToString(comic.getNumber());

            // Store the large image (required — fail if this fails)
            String largeImageName = comicId + "-large.png";
            imageService.updateImage(largeImageName, imageData, true, comicId, comic.getTitle(), comic.getSeries(), comicNumberStr);
            log.info("Stored large image: {}", largeImageName);

            // Resize to ~104px tall and store the small image (best effort)
            String smallImageName = null;
            try {
                byte[] smallImageData = imageResizeService.resizeToHeight(imageData, 104);
                smallImageName = comicId + "-small.png";
                imageService.updateImage(smallImageName, smallImageData, true, comicId, comic.getTitle(), comic.getSeries(), comicNumberStr);
                log.info("Stored small image: {} ({} bytes)", smallImageName, smallImageData.length);
            } catch (Exception e) {
                log.warn("Could not create thumbnail for comic {}, skipping small image.", comicId, e);
            }

            // Always update the comic record — at minimum set the large image ID
            comic.setLargeCachedImageId(largeImageName);
            if (smallImageName != null) {
                comic.setSmallCachedImageId(smallImageName);
            }
            ComicBook updated = comicService.updateComic(comic);

            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(updated))
                .build();
        } catch (Exception e) {
            log.error("Error in uploadComicImage.", e);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .header("Content-Type", "text/plain")
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    @FunctionName("uploadComicBackImage")
    public HttpResponseMessage uploadComicBackImage(
        @HttpTrigger(
            name = "uploadComicBackImage",
            route = "comics/{id}/back-image",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<byte[]>> request,
        @BindingName("id") String id)
    {
        log.info("Processing uploadComicBackImage for comic id: {}", id);
        try {
            int comicId = Integer.parseInt(id);
            ImageService imageService = ImageService.getServiceInstance();
            ImageResizeService imageResizeService = ImageResizeService.getServiceInstance();
            ComicService comicService = ComicService.getServiceInstance();

            byte[] requestBody = request.getBody().orElseThrow(() -> new IllegalArgumentException("No body in image upload request."));
            String boundary = extractBoundary(request.getHeaders().get("content-type"));
            MultipartStream multipartStream = new MultipartStream(new ByteArrayInputStream(requestBody), boundary.getBytes());
            List<byte[]> files = new ArrayList<>();
            boolean nextPart = multipartStream.skipPreamble();
            while (nextPart) {
                String header = multipartStream.readHeaders();
                if (header.contains("Content-Disposition: form-data; name=\"file\"")) {
                    ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                    multipartStream.readBodyData(fileContent);
                    files.add(fileContent.toByteArray());
                }
                nextPart = multipartStream.readBoundary();
            }
            if (files.isEmpty()) {
                throw new RuntimeException("No file part found in multipart upload.");
            }
            byte[] imageData = files.get(0);
            imageData = imageResizeService.resizeIfTooTall(imageData, 900);

            ComicBook comic = comicService.getComicById(comicId)
                .orElseThrow(() -> new RuntimeException("Comic not found with id: " + comicId));
            String comicNumberStr = comicNumberToString(comic.getNumber());

            String largeImageName = comicId + "-back-large.png";
            imageService.updateImage(largeImageName, imageData, true, comicId, comic.getTitle(), comic.getSeries(), comicNumberStr);
            log.info("Stored large back image: {}", largeImageName);

            String smallImageName = null;
            try {
                byte[] smallImageData = imageResizeService.resizeToHeight(imageData, 104);
                smallImageName = comicId + "-back-small.png";
                imageService.updateImage(smallImageName, smallImageData, true, comicId, comic.getTitle(), comic.getSeries(), comicNumberStr);
                log.info("Stored small back image: {} ({} bytes)", smallImageName, smallImageData.length);
            } catch (Exception e) {
                log.warn("Could not create back thumbnail for comic {}, skipping small back image.", comicId, e);
            }
            comic.setLargeBackImageId(largeImageName);
            if (smallImageName != null) {
                comic.setSmallBackImageId(smallImageName);
            }
            ComicBook updated = comicService.updateComic(comic);

            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(updated))
                .build();
        } catch (Exception e) {
            log.error("Error in uploadComicBackImage.", e);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .header("Content-Type", "text/plain")
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    @FunctionName("deleteImage")
    public HttpResponseMessage deleteImage(
        @HttpTrigger(
            name = "deleteImage",
            route = "images/{name}",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("name") String name)
    {
        try {
            ImageService imageService = ImageService.getServiceInstance();
            String result = imageService.deleteImage(name);
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "text/plain")
                .body(result)
                .build();
        } catch (Exception e) {
            return HttpHelper.cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .header("Content-Type", "text/plain")
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    private static String getMimeType(String imageName) {
        String mimeType = URLConnection.guessContentTypeFromName(imageName);
        return mimeType != null ? mimeType : "text/plain";
    }

    private static String comicNumberToString(ComicNumber cn) {
        if (cn == null) return null;
        if (cn.getSentinel() != null) return cn.getSentinel().getValue();
        return cn.getNumber() != null ? String.valueOf(cn.getNumber()) : null;
    }

}
