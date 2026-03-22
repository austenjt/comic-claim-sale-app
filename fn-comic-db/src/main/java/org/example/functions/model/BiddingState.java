package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.functions.util.MoneySerializer;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BiddingState {

    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal highBid;

    /** Set by admin to open the item for bidding. Users see the Bid button only after this is set. */
    private String bidOpenedAt;
    /** Set when the first user places a bid, starting the inactivity countdown. */
    private String bidStartedAt;
    private String currentBidderId;
    private String currentBidderName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<BidEntry> bidHistory;
}
