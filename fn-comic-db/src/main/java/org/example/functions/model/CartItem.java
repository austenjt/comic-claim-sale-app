package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItem {
    private String comicId;
    private String comicTitle;
    private String comicNumber;
    private double price;
    private String claimedAt;
    private Integer collectionGroup;
    @JsonProperty("isSetContainer")
    private boolean isSetContainer;

    /** True when this item was awarded by an admin at $0 — excluded from discount calculations. */
    @JsonProperty("isAwarded")
    private boolean isAwarded;

    /**
     * True when the source comic is graded (CGC/CBCS). Snapshotted at cart-add time so
     * discount rules with {@code excludeGraded=true} don't have to round-trip Cosmos per item.
     * Defaults to false for legacy cart items, which means they'll be treated as ungraded —
     * acceptable since legacy carts predate the excludeGraded feature.
     */
    @JsonProperty("isGraded")
    private boolean isGraded;

    public CartItem() {}

    public String getComicId() { return comicId; }
    public void setComicId(String comicId) { this.comicId = comicId; }

    public String getComicTitle() { return comicTitle; }
    public void setComicTitle(String comicTitle) { this.comicTitle = comicTitle; }

    public String getComicNumber() { return comicNumber; }
    public void setComicNumber(String comicNumber) { this.comicNumber = comicNumber; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getClaimedAt() { return claimedAt; }
    public void setClaimedAt(String claimedAt) { this.claimedAt = claimedAt; }

    public Integer getCollectionGroup() { return collectionGroup; }
    public void setCollectionGroup(Integer collectionGroup) { this.collectionGroup = collectionGroup; }

    public boolean isSetContainer() { return isSetContainer; }
    public void setSetContainer(boolean setContainer) { isSetContainer = setContainer; }

    public boolean isAwarded() { return isAwarded; }
    public void setAwarded(boolean awarded) { isAwarded = awarded; }

    public boolean isGraded() { return isGraded; }
    public void setGraded(boolean graded) { isGraded = graded; }
}
