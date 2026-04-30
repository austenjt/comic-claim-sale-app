package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.example.functions.util.Views;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.example.functions.util.MoneySerializer;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.functions.model.enums.CoverVariant;
import org.example.functions.model.enums.Era;
import org.example.functions.model.thirdParty.GrandComicDBInfo;
import org.example.functions.model.thirdParty.GoCollectInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComicBook {

    private int id;

    // Issue details
    private String title;
    private String series;
    private ComicNumber number;
    private String publisher;
    private String publishedDate;
    private Era era;
    private CoverVariant variant;
    private Integer printRun;
    private Integer numCopies;
    private String barCode;
    private String keyIssue;

    // Credits
    private List<String> writer;
    private List<String> artist;

    // Grading
    private ComicCondition comicCondition;
    private String defects;

    // Purchase info
    @JsonView(Views.Admin.class)
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal pricePaid;
    private String dateAcquired;
    private String purchasedFrom;
    private String purchaseReferenceURL;

    // Sale info
    private String ebayListingUrl;
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal salePrice;
    private String dateSold;
    private String soldTo;
    @JsonProperty("isForSale")
    private Boolean isForSale;

    // Valuation
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal personalEstimate;
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal targetPrice;

    // Collection & storage
    private Integer collectionGroup;
    // Set members — populated at response-time when docType="SET"; not persisted in Cosmos
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ComicBook> items;
    private String docType;
    private String storageLocation;

    // Metadata from externally supported sites
    private GoCollectInfo goCollectInfo;
    private GrandComicDBInfo grandComicDBInfo;

    // Front & Back Images
    private String smallCachedImageId;
    private String largeCachedImageId;
    private String smallBackImageId;
    private String largeBackImageId;

    // Notes
    private String personalNotes;
    private String publicNotes;

    // Bidding
    @JsonProperty("enableBid")
    private Boolean enableBid;

    @JsonUnwrapped
    @Getter(AccessLevel.NONE)
    private BiddingState biddingState;

    public BiddingState getBiddingState() {
        if (this.biddingState == null) this.biddingState = new BiddingState();
        return this.biddingState;
    }

    /**
     * Used to prevent duplicate data being added.
     * Does a comparison but ignores 'id' field.
     * @param obj to be compared with this object
     * @return boolean
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ComicBook comicBook = (ComicBook) obj;
        return Objects.equals(title, comicBook.title) &&
            Objects.equals(series, comicBook.series) &&
            Objects.equals(number, comicBook.number) &&
            Objects.equals(goCollectInfo != null ? goCollectInfo.getGcIndex() : null,
                comicBook.goCollectInfo != null ? comicBook.goCollectInfo.getGcIndex() : null) &&
            Objects.equals(dateAcquired, comicBook.dateAcquired) &&
            Objects.equals(pricePaid, comicBook.pricePaid);
    }

    /**
     * Add additional fields separated by comma to the hash method if you want to compare more.
     * Don't add the id field to the hash computation.
     * @return int
     */
    @Override
    public int hashCode() {
        return Objects.hash(title, series, number,
            goCollectInfo != null ? goCollectInfo.getGcIndex() : null, dateAcquired, pricePaid);
    }

}
