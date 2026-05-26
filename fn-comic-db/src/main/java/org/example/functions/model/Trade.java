package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.functions.model.enums.ComicGrade;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {
    /** Minimum grade the admin considers acceptable for this trade-in. */
    private ComicGrade desiredGrade;
    /** Grade of the copy the customer is offering. */
    private ComicGrade offeredGrade;
    /** Set by admin once the trade terms are agreed upon. */
    private Boolean offerAccepted;
    /** Set by admin once the physical comic has been received. */
    private Boolean tradeReceived;
    private String tradeNotes;
    /** Email/name of the user who made the offer. */
    private String offeredBy;
    /** ISO-8601 timestamp of when the offer was submitted. */
    private String offeredAt;
    /** Large front image of the user's trade-in copy. */
    private String tradeFrontImageId;
    /** Thumbnail front image of the user's trade-in copy. */
    private String tradeSmallFrontImageId;
    /** Large back image of the user's trade-in copy. */
    private String tradeBackImageId;
    /** Thumbnail back image of the user's trade-in copy. */
    private String tradeSmallBackImageId;
}