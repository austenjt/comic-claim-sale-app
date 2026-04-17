package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.functions.model.enums.DiscountType;
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
public class Discount {
    private String id;
    private String name;
    private DiscountType type;
    @JsonProperty("isActive")
    private boolean isActive;
    private double percentageOff;
    @JsonProperty("xBooks")
    @JsonAlias("XBooks")
    private int xBooks;
    @JsonProperty("excludeSets")
    private Boolean excludeSets;
    private String createdAt;
}
