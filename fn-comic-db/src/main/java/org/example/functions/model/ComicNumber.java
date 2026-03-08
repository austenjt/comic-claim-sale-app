package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.example.functions.model.enums.NumberSentinel;

import java.util.Objects;

@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComicNumber implements Comparable<ComicNumber> {

    @Setter
    private Integer volume;

    private Integer number;

    private NumberSentinel sentinel;

    public void setNumber(Integer number) {
        this.number = number;
        if (number != null && number >= 0) {
            this.sentinel = null;
        }
    }

    public void setSentinel(NumberSentinel sentinel) {
        this.sentinel = sentinel;
        if (sentinel != null) {
            this.number = null;
        }
    }

    public static ComicNumber of(int volume, int number) {
        ComicNumber cn = ComicNumber.builder().volume(volume).build();
        cn.setNumber(number);
        return cn;
    }

    public static ComicNumber of(int volume, NumberSentinel sentinel) {
        ComicNumber cn = ComicNumber.builder().volume(volume).build();
        cn.setSentinel(sentinel);
        return cn;
    }

    public boolean hasStandardNumber() {
        return number != null && number >= 0;
    }

    @Override
    public int compareTo(ComicNumber other) {
        // sort by volume first (nulls last)
        int volCompare = compareNullable(this.volume, other.volume);
        if (volCompare != 0) {
            return volCompare;
        }

        // then by number vs sentinel
        if (this.hasStandardNumber() && other.hasStandardNumber()) {
            return this.number.compareTo(other.number);
        }
        if (this.hasStandardNumber()) {
            return -1; // numbered sorts before sentinel
        }
        if (other.hasStandardNumber()) {
            return 1; // sentinel sorts after numbered
        }
        // both are sentinels — sort by enum ordinal
        if (this.sentinel == null && other.sentinel == null) return 0;
        if (this.sentinel == null) return 1;
        if (other.sentinel == null) return -1;
        return this.sentinel.compareTo(other.sentinel);
    }

    private static int compareNullable(Integer a, Integer b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ComicNumber that = (ComicNumber) obj;
        return Objects.equals(volume, that.volume)
            && Objects.equals(number, that.number)
            && Objects.equals(sentinel, that.sentinel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(volume, number, sentinel);
    }
}