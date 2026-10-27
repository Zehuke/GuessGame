package com.guess.order;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameRulesTest {
    @Test public void validatesOnlyFourDistinctNonzeroDigits() {
        assertTrue(GameRules.isValidNumber("1234"));
        assertFalse(GameRules.isValidNumber("0123"));
        assertFalse(GameRules.isValidNumber("1123"));
        assertFalse(GameRules.isValidNumber("12a4"));
        assertFalse(GameRules.isValidNumber(null));
    }

    @Test public void scoreCountsMembershipAndExactPosition() {
        assertArrayEquals(new int[]{4, 0}, GameRules.score("1234", "4321"));
        assertArrayEquals(new int[]{2, 2}, GameRules.score("1234", "1256"));
    }

    @Test public void resolvesSimultaneousWinsAsDraw() {
        assertEquals(0, GameRules.resolveRound(new int[]{4, 4}, new int[]{4, 4}));
        assertEquals(1, GameRules.resolveRound(new int[]{4, 4}, new int[]{2, 1}));
        assertEquals(-1, GameRules.resolveRound(new int[]{3, 2}, new int[]{2, 0}));
    }
}
