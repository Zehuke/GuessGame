package com.guess.order;

/** Pure game rules shared by local and online transports. */
public final class GameRules {
    private GameRules() { }

    public static boolean isValidNumber(String value) {
        if (value == null || value.length() != 4) return false;
        boolean[] seen = new boolean[10];
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '1' || c > '9' || seen[c - '0']) return false;
            seen[c - '0'] = true;
        }
        return true;
    }

    /** Returns {True count, Position count}. */
    public static int[] score(String guess, String secret) {
        if (!isValidNumber(guess) || !isValidNumber(secret))
            throw new IllegalArgumentException("Guess and secret must be valid four-digit numbers");
        int trueCount = 0, positionCount = 0;
        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) == secret.charAt(i)) positionCount++;
            if (secret.indexOf(guess.charAt(i)) >= 0) trueCount++;
        }
        return new int[]{trueCount, positionCount};
    }

    /** -1 means no winner, 0 draw, 1 player one, 2 player two. */
    public static int resolveRound(int[] playerOneScore, int[] playerTwoScore) {
        boolean oneWon = playerOneScore[1] == 4;
        boolean twoWon = playerTwoScore[1] == 4;
        if (oneWon && twoWon) return 0;
        if (oneWon) return 1;
        if (twoWon) return 2;
        return -1;
    }
}
