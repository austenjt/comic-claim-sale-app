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
public class ArchivedOrderItem {
    private String comicId;
    private String comicTitle;
    private String comicNumber;
    private double price;
    private String claimedAt;
    private Integer collectionGroup;
    /** Mirrors {@code CartItem.isGraded} so the frontend can apply graded-exclusion math after archive. */
    private boolean isGraded;
}
