package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.PageQuality;
import org.example.functions.util.GradeSerializer;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CGCCondition {

    private String label;
    @JsonSerialize(using = GradeSerializer.class)
    private ComicGrade grade;
    private PageQuality pageQuality;
    private String pedigree;
    private Boolean signature;
    private String degreeOfRestoration;
    private String graderNotes;
}
