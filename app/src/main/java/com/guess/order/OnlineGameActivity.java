package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

import com.google.firebase.database.*;

import java.util.HashSet;
import java.util.Set;

public class OnlineGameActivity extends Activity {

    EditText etGuessP1, etGuessP2;
    Button btnSubmitP1, btnSubmitP2;
    TextView tvWaiting;
    LinearLayout resultContainer;

    String mySecret, opponentSecret;
    String myGuess = null, opponentGuess = null;

    int attempts = 0;

    String roomCode;
    String myRole;
    String opponentRole;

    DatabaseReference roomRef;

    boolean secretsLoaded = false;

    ValueEventListener secretsListener;
    ValueEventListener guessesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        roomCode = getIntent().getStringExtra("ROOM_CODE");
        myRole = getIntent().getStringExtra("MY_ROLE");
        opponentRole = myRole.equals("player1") ? "player2" : "player1";

        etGuessP1 = findViewById(R.id.etGuessP1);
        etGuessP2 = findViewById(R.id.etGuessP2);
        btnSubmitP1 = findViewById(R.id.btnSubmitP1);
        btnSubmitP2 = findViewById(R.id.btnSubmitP2);
        tvWaiting = findViewById(R.id.tvWaiting);
        resultContainer = findViewById(R.id.resultContainer);

        if (myRole.equals("player1")) {
            etGuessP2.setEnabled(false);
            btnSubmitP2.setEnabled(false);
        } else {
            etGuessP1.setEnabled(false);
            btnSubmitP1.setEnabled(false);
        }

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode);

        tvWaiting.setText("Loading secrets...");
        loadSecrets();

        btnSubmitP1.setOnClickListener(v -> submitGuessP1());
        btnSubmitP2.setOnClickListener(v -> submitGuessP2());

        listenForOpponentGuess();
    }

    // ---------------- FIREBASE ----------------
    private void loadSecrets() {
        secretsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                mySecret = snapshot.child(myRole).getValue(String.class);
                opponentSecret = snapshot.child(opponentRole).getValue(String.class);

                if (mySecret != null && opponentSecret != null) {
                    secretsLoaded = true;
                    tvWaiting.setText("");

                    roomRef.child("secrets").removeEventListener(secretsListener);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                tvWaiting.setText("Error loading game");
            }
        };

        roomRef.child("secrets").addValueEventListener(secretsListener);
    }

    private void listenForOpponentGuess() {
        guessesListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                opponentGuess = snapshot.child(opponentRole)
                        .getValue(String.class);

                if (myGuess != null && opponentGuess != null) {
                    processRound();
                    roomRef.child("guesses").setValue(null);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        roomRef.child("guesses").addValueEventListener(guessesListener);
    }

    // ---------------- GAME LOGIC ----------------
    private void submitGuessP1() {
        if (!myRole.equals("player1") || !secretsLoaded) return;
        handleSubmit(etGuessP1);
    }

    private void submitGuessP2() {
        if (!myRole.equals("player2") || !secretsLoaded) return;
        handleSubmit(etGuessP2);
    }

    private void handleSubmit(EditText input) {
        String guess = input.getText().toString().trim();

        if (!isValidGuess(guess)) {
            input.setError("Invalid guess");
            return;
        }

        attempts++;
        myGuess = guess;
        input.setText("");
        tvWaiting.setText("Waiting for opponent...");

        roomRef.child("guesses").child(myRole).setValue(myGuess);
    }

    private void processRound() {

        tvWaiting.setText("");

        int[] myResult = calculateResult(myGuess, opponentSecret);
        int[] opponentResult = calculateResult(opponentGuess, mySecret);

        if (myRole.equals("player1")) {
            addResultRow(myGuess, myResult, opponentGuess, opponentResult);
        } else {
            addResultRow(opponentGuess, opponentResult, myGuess, myResult);
        }

        checkWin(myResult, opponentResult);

        myGuess = null;
        opponentGuess = null;
    }

    // ---------------- UTIL ----------------
    private boolean isValidGuess(String guess) {
        if (guess.length() != 4 || guess.contains("0")) return false;
        Set<Character> set = new HashSet<>();
        for (char c : guess.toCharArray()) set.add(c);
        return set.size() == 4;
    }

    private int[] calculateResult(String guess, String secret) {
        int trueCount = 0, position = 0;
        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) == secret.charAt(i)) position++;
            if (secret.indexOf(guess.charAt(i)) != -1) trueCount++;
        }
        return new int[]{trueCount, position};
    }

    private void addResultRow(String p1Guess, int[] p1Result, String p2Guess, int[] p2Result) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams cell =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);

        TextView[] cells = {
                createCell(p1Guess, cell),
                createCell(String.valueOf(p1Result[0]), cell),
                createCell(String.valueOf(p1Result[1]), cell),
                createCell(p2Guess, cell),
                createCell(String.valueOf(p2Result[0]), cell),
                createCell(String.valueOf(p2Result[1]), cell)
        };

        for (TextView tv : cells) row.addView(tv);
        resultContainer.addView(row);
    }

    private TextView createCell(String text, LinearLayout.LayoutParams params) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.WHITE);
        return tv;
    }

    private void checkWin(int[] myResult, int[] opponentResult) {

        boolean youWin = myResult[1] == 4;
        boolean opponentWin = opponentResult[1] == 4;

        if (youWin || opponentWin) {

            roomRef.child("guesses").removeEventListener(guessesListener);

            Intent intent = new Intent(this, OnlineResultActivity.class);

            if (youWin && opponentWin) intent.putExtra("RESULT", "DRAW");
            else if (youWin) intent.putExtra("RESULT", "YOU WIN");
            else intent.putExtra("RESULT", "YOU LOSE");

            intent.putExtra("ATTEMPTS", attempts);
            intent.putExtra("ROOM_CODE", roomCode);
            startActivity(intent);
            finish();
        }
    }
}