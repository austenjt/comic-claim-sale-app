package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItem {
    private String comicId;
    private String comicTitle;
    private String comicNumber;
    private double price;
    private String claimedAt;

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
}
