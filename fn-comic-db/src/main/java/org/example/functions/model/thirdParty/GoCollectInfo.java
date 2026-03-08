package org.example.functions.model.thirdParty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoCollectInfo {
    private Integer gcIndex;
    private String gcSlug;
    private String gcUrl;
    private String gcSeries;
    private String importDate;
}
