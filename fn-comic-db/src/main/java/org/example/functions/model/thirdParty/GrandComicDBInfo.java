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
public class GrandComicDBInfo {
    private Integer gcdbIssueId;
    private Integer gcdbSeriesId;
    private String issueUrl;
    private String seriesUrl;
}
