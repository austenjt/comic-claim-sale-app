package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaimNotification {
    /** CLAIM | AWARD | RETURN */
    private String eventType;
    private String comicId;
    private String comicTitle;
    private String comicNumber;
    private String userName;
    private double price;
    private String claimedAt;
}
