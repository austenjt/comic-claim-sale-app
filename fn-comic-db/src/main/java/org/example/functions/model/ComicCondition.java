package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.model.enums.PageQuality;
import org.example.functions.util.GradeSerializer;

@Getter
@Setter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ComicCondition.ComicConditionBuilder.class)
public class ComicCondition {

    // Certification (general)
    @NonNull
    private Boolean isGraded;

    // Blocked when isGraded=false
    @Setter(AccessLevel.NONE)
    private GradingCompany certificationCompany;

    @Setter(AccessLevel.NONE)
    private String certificationId;

    // CGC-specific — setter blocked when isGraded=false
    @Setter(AccessLevel.NONE)
    private CGCCondition cgcCondition;

    // CBCS-specific — setter blocked when isGraded=false
    @Setter(AccessLevel.NONE)
    private CBCSCondition cbcsCondition;

    // Not certified, opinionated values
    private String notCertifiedLabel;
    @JsonSerialize(using = GradeSerializer.class)
    private ComicGrade notCertifiedGrade;
    private PageQuality notCertifiedPageQuality;
    private String notCertifiedPedigree;
    private String notCertifiedDegreeOfRestoration;
    private Boolean notCertifiedSignature;

    public void setCertificationCompany(GradingCompany certificationCompany) {
        if (!Boolean.TRUE.equals(isGraded)) {
            throw new IllegalStateException("Cannot set certificationCompany on a comic that is not graded.");
        }
        this.certificationCompany = certificationCompany;
    }

    public void setCertificationId(String certificationId) {
        if (!Boolean.TRUE.equals(isGraded)) {
            throw new IllegalStateException("Cannot set certificationId on a comic that is not graded.");
        }
        this.certificationId = certificationId;
    }

    public void setCgcCondition(CGCCondition cgcCondition) {
        if (!Boolean.TRUE.equals(isGraded)) {
            throw new IllegalStateException("Cannot set or modify CGC condition data on a comic that is not graded.");
        }
        if (certificationCompany == GradingCompany.CBCS) {
            throw new IllegalStateException("Cannot set CGC condition data when grading company is CBCS.");
        }
        this.cgcCondition = cgcCondition;
    }

    public void setCbcsCondition(CBCSCondition cbcsCondition) {
        if (!Boolean.TRUE.equals(isGraded)) {
            throw new IllegalStateException("Cannot set or modify CBCS condition data on a comic that is not graded.");
        }
        if (certificationCompany == GradingCompany.CGC) {
            throw new IllegalStateException("Cannot set CBCS condition data when grading company is CGC.");
        }
        this.cbcsCondition = cbcsCondition;
    }

    /**
     * Lombok merges this with the generated builder. The @JsonPOJOBuilder tells Jackson that
     * builder methods have no prefix (matching Lombok's default), enabling @JsonDeserialize
     * to use this builder for deserialization without the setter restrictions above.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class ComicConditionBuilder {
        // Lombok generates all field builder methods here.
        // No validation — the builder is the unrestricted construction path.
    }

    /**
     * Syncs some condition fields up from CGCCondition class & CBCSCondition class.
     */
    public void syncCondition() {
        if (Boolean.TRUE.equals(isGraded)) {
            if (cgcCondition != null) {
                if (cgcCondition.getGrade() != null) notCertifiedGrade = cgcCondition.getGrade();
                if (cgcCondition.getPageQuality() != null) notCertifiedPageQuality = cgcCondition.getPageQuality();
                if (cgcCondition.getSignature() != null) notCertifiedSignature = cgcCondition.getSignature();
            } else if (cbcsCondition != null) {
                if (cbcsCondition.getGrade() != null) notCertifiedGrade = cbcsCondition.getGrade();
                if (cbcsCondition.getPageQuality() != null) notCertifiedPageQuality = cbcsCondition.getPageQuality();
                if (cbcsCondition.getSignature() != null) notCertifiedSignature = cbcsCondition.getSignature();
            }
        } else {
            this.cbcsCondition = null;
            this.cgcCondition = null;
        }
    }

}
