package org.example.functions.model;

import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.model.enums.PageQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComicConditionTest {

    // --- certificationCompany ---

    @Test
    void setCertificationCompany_throwsWhenNotGraded() {
        ComicCondition condition = new ComicCondition();
        assertThrows(IllegalStateException.class,
            () -> condition.setCertificationCompany(GradingCompany.CGC));
    }

    @Test
    void setCertificationCompany_succeedsWhenGraded() {
        ComicCondition condition = ComicCondition.builder().isGraded(true).build();
        assertDoesNotThrow(() -> condition.setCertificationCompany(GradingCompany.CGC));
        assertEquals(GradingCompany.CGC, condition.getCertificationCompany());
    }

    // --- certificationId ---

    @Test
    void setCertificationId_throwsWhenNotGraded() {
        ComicCondition condition = new ComicCondition();
        assertThrows(IllegalStateException.class,
            () -> condition.setCertificationId("4027234006"));
    }

    @Test
    void setCertificationId_succeedsWhenGraded() {
        ComicCondition condition = ComicCondition.builder().isGraded(true).build();
        assertDoesNotThrow(() -> condition.setCertificationId("4027234006"));
        assertEquals("4027234006", condition.getCertificationId());
    }

    // --- cgcCondition ---

    @Test
    void setCgcCondition_throwsWhenNotGraded() {
        ComicCondition condition = new ComicCondition();
        CGCCondition cgc = CGCCondition.builder().grade(ComicGrade.MINT).build();
        assertThrows(IllegalStateException.class,
            () -> condition.setCgcCondition(cgc));
    }

    @Test
    void setCgcCondition_succeedsWhenGraded() {
        ComicCondition condition = ComicCondition.builder().isGraded(true).build();
        CGCCondition cgc = CGCCondition.builder().grade(ComicGrade.MINT).build();
        assertDoesNotThrow(() -> condition.setCgcCondition(cgc));
        assertEquals(cgc, condition.getCgcCondition());
    }

    // --- cbcsCondition ---

    @Test
    void setCbcsCondition_throwsWhenNotGraded() {
        ComicCondition condition = new ComicCondition();
        CBCSCondition cbcs = CBCSCondition.builder().grade(ComicGrade.NEAR_MINT_PLUS).build();
        assertThrows(IllegalStateException.class,
            () -> condition.setCbcsCondition(cbcs));
    }

    @Test
    void setCbcsCondition_succeedsWhenGraded() {
        ComicCondition condition = ComicCondition.builder().isGraded(true).build();
        CBCSCondition cbcs = CBCSCondition.builder().grade(ComicGrade.NEAR_MINT_PLUS).build();
        assertDoesNotThrow(() -> condition.setCbcsCondition(cbcs));
        assertEquals(cbcs, condition.getCbcsCondition());
    }

    // --- company mismatch guards ---

    @Test
    void setCgcCondition_throwsWhenCompanyIsCBCS() {
        ComicCondition condition = ComicCondition.builder()
            .isGraded(true)
            .certificationCompany(GradingCompany.CBCS)
            .build();
        CGCCondition cgc = CGCCondition.builder().grade(ComicGrade.MINT).build();
        assertThrows(IllegalStateException.class,
            () -> condition.setCgcCondition(cgc));
    }

    @Test
    void setCgcCondition_succeedsWhenCompanyIsCGC() {
        ComicCondition condition = ComicCondition.builder()
            .isGraded(true)
            .certificationCompany(GradingCompany.CGC)
            .build();
        CGCCondition cgc = CGCCondition.builder().grade(ComicGrade.MINT).build();
        assertDoesNotThrow(() -> condition.setCgcCondition(cgc));
        assertEquals(cgc, condition.getCgcCondition());
    }

    @Test
    void setCbcsCondition_throwsWhenCompanyIsCGC() {
        ComicCondition condition = ComicCondition.builder()
            .isGraded(true)
            .certificationCompany(GradingCompany.CGC)
            .build();
        CBCSCondition cbcs = CBCSCondition.builder().grade(ComicGrade.NEAR_MINT_PLUS).build();
        assertThrows(IllegalStateException.class,
            () -> condition.setCbcsCondition(cbcs));
    }

    @Test
    void setCbcsCondition_succeedsWhenCompanyIsCBCS() {
        ComicCondition condition = ComicCondition.builder()
            .isGraded(true)
            .certificationCompany(GradingCompany.CBCS)
            .build();
        CBCSCondition cbcs = CBCSCondition.builder().grade(ComicGrade.NEAR_MINT_PLUS).build();
        assertDoesNotThrow(() -> condition.setCbcsCondition(cbcs));
        assertEquals(cbcs, condition.getCbcsCondition());
    }

    // --- not-certified fields are always settable ---

    @Test
    void setNotCertifiedFields_alwaysSucceeds() {
        ComicCondition ungraded = new ComicCondition();
        assertDoesNotThrow(() -> {
            ungraded.setNotCertifiedGrade(ComicGrade.VERY_FINE);
            ungraded.setNotCertifiedPageQuality(PageQuality.OFF_WHITE);
            ungraded.setNotCertifiedSignature(false);
        });

        ComicCondition graded = ComicCondition.builder().isGraded(true).build();
        assertDoesNotThrow(() -> {
            graded.setNotCertifiedGrade(ComicGrade.VERY_FINE);
            graded.setNotCertifiedPageQuality(PageQuality.OFF_WHITE);
            graded.setNotCertifiedSignature(false);
        });
    }

    // --- builder is unrestricted ---

    @Test
    void builder_allowsSettingProtectedFieldsRegardlessOfIsGraded() {
        CGCCondition cgc = CGCCondition.builder().grade(ComicGrade.MINT).build();

        assertDoesNotThrow(() -> ComicCondition.builder()
            .isGraded(false)
            .cgcCondition(cgc)
            .certificationCompany(GradingCompany.CGC)
            .certificationId("4027234006")
            .build());

        assertDoesNotThrow(() -> ComicCondition.builder()
            .isGraded(true)
            .cgcCondition(cgc)
            .certificationCompany(GradingCompany.CGC)
            .certificationId("4027234006")
            .build());
    }
}
