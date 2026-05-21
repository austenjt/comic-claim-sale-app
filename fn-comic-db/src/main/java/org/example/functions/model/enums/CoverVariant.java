package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CoverVariant {
    DIRECT_EDITION("Direct Edition"),
    NEWSSTAND("Newsstand"),
    COVER_A("Cover A"),
    COVER_B("Cover B"),
    COVER_C("Cover C"),
    COVER_D("Cover D"),
    COVER_E("Cover E"),
    COVER_F("Cover F"),
    COVER_G("Cover G"),
    COVER_H("Cover H"),
    COVER_I("Cover I"),
    COVER_J("Cover J"),
    COVER_K("Cover K"),
    COVER_L("Cover L"),
    COVER_M("Cover M"),
    REGULAR("Regular"),
    VARIANT("Variant"),
    SECRET_VARIANT("Secret Variant"),
    VIRGIN("Virgin"),
    SKETCH("Sketch"),
    BLANK("Blank"),
    FOIL("Foil"),
    HOLOGRAPHIC("Holographic"),
    CHROMIUM("Chromium"),
    LENTICULAR("Lenticular"),
    GLOW_IN_THE_DARK("Glow in the Dark"),
    DIE_CUT("Die-Cut"),
    GATEFOLD("Gatefold"),
    DOUBLE_SIZED("Double-Sized"),
    WRAPAROUND("Wraparound"),
    CONNECTING("Connecting"),
    LIMITED_EDITION("Limited Edition"),
    HOMAGE("Homage"),
    BLACK_AND_WHITE("Black and White"),
    NEGATIVE_SPACE("Negative Space"),
    PHOTO("Photo"),
    ACTION_FIGURE("Action Figure"),
    INCENTIVE_1_10("Incentive 1:10"),
    INCENTIVE_1_25("Incentive 1:25"),
    INCENTIVE_1_50("Incentive 1:50"),
    INCENTIVE_1_100("Incentive 1:100"),
    INCENTIVE_1_200("Incentive 1:200"),
    INCENTIVE_1_500("Incentive 1:500"),
    INCENTIVE_1_1000("Incentive 1:1000"),
    STORE_EXCLUSIVE("Store Exclusive"),
    ONE_OF_N("One of N"),
    CONVENTION_EXCLUSIVE("Convention Exclusive"),
    CANADIAN_PRICE("Canadian Price"),
    UK_PRICE("UK Price"),
    GOLD_EDITION("Gold Edition"),
    PLATINUM_EDITION("Platinum Edition"),
    SIGNED_EDITION("Signed Edition"),
    DIRECTORS_CUT("Director's Cut"),
    ANNIVERSARY("Anniversary"),
    REPRINT("Reprint"),
    ERROR_MISPRINT("Error/Misprint"),
    RECALLED("Recalled");

    private final String label;

    CoverVariant(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}