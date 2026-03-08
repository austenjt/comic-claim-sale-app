package org.example.functions.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CosmosImage {
    private String id;          // image name (e.g. "abc123.jpg")
    private String data;        // Base64-encoded image bytes
    private String contentType; // MIME type (e.g. "image/jpeg")
    private Integer comicId;
    private String comicTitle;
    private String comicSeries;
    private String comicNumber;
}
