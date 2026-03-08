package org.example.functions.model;

import org.example.functions.model.enums.NumberSentinel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComicBookNumberTest {

    @Test
    void sameVolume_sortsByNumber() {
        ComicNumber issue1 = ComicNumber.of(1, 1);
        ComicNumber issue2 = ComicNumber.of(1, 50);

        assertTrue(issue1.compareTo(issue2) < 0);
        assertTrue(issue2.compareTo(issue1) > 0);
    }

    @Test
    void differentVolumes_sortsByVolumeFirst() {
        ComicNumber vol1issue50 = ComicNumber.of(1, 50);
        ComicNumber vol2issue1 = ComicNumber.of(2, 1);

        assertTrue(vol1issue50.compareTo(vol2issue1) < 0);
    }

    @Test
    void sentinelSortsAfterNumberedWithinSameVolume() {
        ComicNumber numbered = ComicNumber.of(1, 100);
        ComicNumber sentinel = ComicNumber.of(1, NumberSentinel.NN);

        assertTrue(numbered.compareTo(sentinel) < 0);
        assertTrue(sentinel.compareTo(numbered) > 0);
    }

    @Test
    void sentinelsSortByEnumOrdinal() {
        ComicNumber negOne = ComicNumber.of(1, NumberSentinel.NEGATIVE_ONE);
        ComicNumber nn = ComicNumber.of(1, NumberSentinel.NN);

        assertTrue(negOne.compareTo(nn) < 0);
        assertTrue(nn.compareTo(negOne) > 0);
    }

    @Test
    void nullVolumeSortsLast() {
        ComicNumber withVolume = ComicNumber.of(1, 1);
        ComicNumber nullVolume = new ComicNumber();
        nullVolume.setVolume(null);
        nullVolume.setNumber(1);

        assertTrue(withVolume.compareTo(nullVolume) < 0);
        assertTrue(nullVolume.compareTo(withVolume) > 0);
    }

    @Test
    void sameValuesAreEqual() {
        ComicNumber a = ComicNumber.of(1, 5);
        ComicNumber b = ComicNumber.of(1, 5);

        assertEquals(0, a.compareTo(b));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void fullSortOrder() {
        ComicNumber vol1num1 = ComicNumber.of(1, 1);
        ComicNumber vol1num2 = ComicNumber.of(1, 2);
        ComicNumber vol1nn = ComicNumber.of(1, NumberSentinel.NN);
        ComicNumber vol2num1 = ComicNumber.of(2, 1);
        ComicNumber vol2num50 = ComicNumber.of(2, 50);
        ComicNumber nullVolNum1 = new ComicNumber();
        nullVolNum1.setVolume(null);
        nullVolNum1.setNumber(1);

        List<ComicNumber> list = Arrays.asList(
            nullVolNum1, vol2num50, vol1nn, vol1num1, vol2num1, vol1num2
        );
        Collections.sort(list);

        assertEquals(List.of(vol1num1, vol1num2, vol1nn, vol2num1, vol2num50, nullVolNum1), list);
    }

    @Test
    void hasNumber_trueWhenNumberSet() {
        assertTrue(ComicNumber.of(1, 5).hasStandardNumber());
    }

    @Test
    void hasNumber_falseWhenSentinel() {
        assertFalse(ComicNumber.of(1, NumberSentinel.NN).hasStandardNumber());
    }

    @Test
    void setNumber_clearsSentinel() {
        ComicNumber cn = ComicNumber.of(1, NumberSentinel.NN);
        cn.setNumber(5);
        assertNull(cn.getSentinel());
        assertEquals(5, cn.getNumber());
    }

    @Test
    void setSentinel_setsNumberToNegativeOne() {
        ComicNumber cn = ComicNumber.of(1, 5);
        cn.setSentinel(NumberSentinel.NN);
        assertEquals(null, cn.getNumber());
        assertEquals(NumberSentinel.NN, cn.getSentinel());
    }
}