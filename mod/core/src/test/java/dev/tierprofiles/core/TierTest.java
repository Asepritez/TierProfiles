package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tierprofiles.core.model.Band;
import dev.tierprofiles.core.model.Tier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TierTest {
    private static Tier tier(String label) {
        return Tier.parse(label);
    }

    @Test
    void readsMctiersHighAndLow() {
        assertEquals(Tier.of(Band.HT, 3), tier("HT3"));
        assertEquals(Tier.of(Band.LT, 5), tier("LT5"));
    }

    @Test
    void readsMcpvpMidAndSubDownToLevelSix() {
        assertEquals(Tier.of(Band.MT, 4), tier("MT4"));
        assertEquals(Tier.of(Band.ST, 6), tier("ST6"));
    }

    @Test
    void ignoresCase() {
        assertEquals(tier("HT1"), tier("ht1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "HT", "HT7", "HT0", "XT1", "HT12", "banana", "H T"})
    void rejectsNonsenseWithoutThrowing(String text) {
        assertNull(Tier.parse(text));
    }

    @Test
    void rejectsNull() {
        assertNull(Tier.parse(null));
    }

    @Test
    void levelOutsideTheRangeCantBeBuilt() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Tier.of(Band.HT, 7));
    }

    @ParameterizedTest(name = "{0} beats {1}")
    @CsvSource({
        "HT1, LT1", "LT1, HT2", "HT2, LT2", "LT2, HT3", "HT3, LT3", "LT3, HT4", "HT4, LT4", "LT4, HT5", "HT5, LT5",
        "HT1, MT1", "MT1, LT1", "LT1, ST1", "ST1, HT2",
        "LT5, HT6", "LT3, ST3", "MT3, LT3", "HT6, ST6",
    })
    void strongerTierBeatsWeaker(String stronger, String weaker) {
        assertTrue(tier(stronger).isAtLeast(tier(weaker)));
        assertFalse(tier(weaker).isAtLeast(tier(stronger)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HT1", "LT3", "MT4", "ST6"})
    void aTierIsAtLeastItself(String label) {
        assertTrue(tier(label).isAtLeast(tier(label)));
    }

    @Test
    void sortsBothListStylesOnOneScale() {
        List<Tier> mixed = new ArrayList<>(List.of(tier("LT3"), tier("HT1"), tier("ST6"), tier("MT3"), tier("HT4")));
        Collections.sort(mixed, Tier.STRONGEST_FIRST);
        assertEquals(List.of(tier("HT1"), tier("MT3"), tier("LT3"), tier("HT4"), tier("ST6")), mixed);
    }

    @Test
    void everyTierHasAUniqueScore() {
        var seen = new java.util.HashSet<Integer>();
        for (Band band : Band.values()) {
            for (int level = Tier.MIN_LEVEL; level <= Tier.MAX_LEVEL; level++) {
                assertTrue(seen.add(Tier.of(band, level).score()));
            }
        }
    }

    @Test
    void labelsReadWell() {
        assertEquals("HT3", tier("HT3").label());
        assertEquals("High Tier 3", tier("HT3").longLabel());
        assertEquals("Mid Tier 4", tier("MT4").longLabel());
        assertEquals("Sub Tier 6", tier("ST6").longLabel());
    }
}
