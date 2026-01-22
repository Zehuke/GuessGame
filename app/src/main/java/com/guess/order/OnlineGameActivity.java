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

    EditText etGuess;
    Button btnSubmit;
    TextView tvWaiting;
    LinearLayout resultContainer;

    String mySecret, opponentSecret;
    String myGuess = null, opponentGuess = null;

    int attempts = 0;

    String roomCode;
    String myRole;
    String opponentRole;

    DatabaseReference roomRef;
    DatabaseReference myPlayerRef;
    DatabaseReference opponentPlayerRef;

    ValueEventListener secretsListener;
    ValueEventListener guessesListener;
    ValueEventListener opponentListener;

    boolean secretsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        roomCode = getIntent().getStringExtra("ROOM_CODE");
        myRole = getIntent().getStringExtra("MY_ROLE");
        opponentRole = myRole.equals("player1") ? "player2" : "player1";

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode);

        myPlayerRef = roomRef.child("players").child(myRole);
        opponentPlayerRef = roomRef.child("players").child(opponentRole);

        // Ensure Firebase removes player if connection is lost
        myPlayerRef.onDisconnect().setValue(false);

        // Listen for opponent disconnects
        listenForOpponentDisconnect();

        etGuess = findViewById(R.id.etGuess);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvWaiting = findViewById(R.id.tvWaiting);
        resultContainer = findViewById(R.id.resultContainer);

        tvWaiting.setText("Loading secrets...");
        loadSecrets();

        btnSubmit.setOnClickListener(v -> submitGuess());

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

            // Opponent has submitted but you haven't yet
            if (opponentGuess != null && myGuess == null) {
                tvWaiting.setText("Opponent has submitted. Your turn.");
            }

            // Both guesses exist → process round
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

    private void listenForOpponentDisconnect() {
        opponentListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean online = snapshot.getValue(Boolean.class);

                if (online != null && !online) {
                    opponentDisconnected();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        opponentPlayerRef.addValueEventListener(opponentListener);
    }

    private void opponentDisconnected() {
        Toast.makeText(this, "Opponent disconnected", Toast.LENGTH_LONG).show();

        // If host, delete room; if guest, just leave
        if (myRole.equals("player1")) {
            roomRef.removeValue();
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
	
	
	@Override
    public void onBackPressed() {

    // Mark player offline
    if (myPlayerRef != null) {
        myPlayerRef.setValue(false);
    }

    // If host, delete room immediately
    if ("player1".equals(myRole) && roomRef != null) {
        roomRef.removeValue();
    }

    // Navigate back safely
    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
    finish();
    }
	
	

    // ---------------- GAME LOGIC ----------------
    private void submitGuess() {
        if (!secretsLoaded) return;

        String guess = etGuess.getText().toString().trim();

        if (!isValidGuess(guess)) {
            etGuess.setError("Invalid guess");
            return;
        }

        attempts++;
        myGuess = guess;
        etGuess.setText("");
        tvWaiting.setText("Waiting for opponent...");

        roomRef.child("guesses").child(myRole).setValue(myGuess);
    }

    private void processRound() {
        tvWaiting.setText("");

        int[] myResult = calculateResult(myGuess, opponentSecret);
        int[] opponentResult = calculateResult(opponentGuess, mySecret);

        addResultRow(myGuess, myResult, opponentGuess, opponentResult);

        checkWin(myResult, opponentResult);

        myGuess = null;
        opponentGuess = null;
    }

    // ---------------- UTILS ----------------
    private boolean isValidGuess(String guess) {
        if (guess.length() != 4 || guess.contains("0")) return false;
        Set<Character> set = new HashSet<>();
        for (char c : guess.toCharArray()) set.add(c);
        return set.size() == 4;
    }

    private int[] calculateResult(String guess, String secret) {
        int correct = 0, position = 0;
        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) == secret.charAt(i)) position++;
            if (secret.indexOf(guess.charAt(i)) != -1) correct++;
        }
        return new int[]{correct, position};
    }

    private void addResultRow(String myGuess, int[] myResult, String oppGuess, int[] oppResult) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams cell =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);

        TextView[] cells = {
                createCell(myGuess, cell),
                createCell(String.valueOf(myResult[0]), cell),
                createCell(String.valueOf(myResult[1]), cell),
                createCell(oppGuess, cell),
                createCell(String.valueOf(oppResult[0]), cell),
                createCell(String.valueOf(oppResult[1]), cell)
        };

        for (TextView tv : cells) row.addView(tv);
        resultContainer.addView(row);
    }

    private TextView createCell(String text, LinearLayout.LayoutParams params) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.BLACK);
        return tv;
    }

    private void checkWin(int[] myResult, int[] opponentResult) {

    boolean youWin = myResult[1] == 4;
    boolean opponentWin = opponentResult[1] == 4;

    if (youWin || opponentWin) {

        if (guessesListener != null) {
            roomRef.child("guesses").removeEventListener(guessesListener);
        }

        // Delete room when game ends
        roomRef.removeValue();

        Intent intent = new Intent(this, OnlineResultActivity.class);

        if (youWin && opponentWin) intent.putExtra("RESULT", "DRAW");
        else if (youWin) intent.putExtra("RESULT", "YOU WIN");
        else intent.putExtra("RESULT", "YOU LOSE");

        intent.putExtra("ATTEMPTS", attempts);
        intent.putExtra("MY_SECRET", mySecret);
        intent.putExtra("OPPONENT_SECRET", opponentSecret);

        startActivity(intent);
        finish();
    }
    }  

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (secretsListener != null) roomRef.child("secrets").removeEventListener(secretsListener);
        if (guessesListener != null) roomRef.child("guesses").removeEventListener(guessesListener);
        if (opponentListener != null) opponentPlayerRef.removeEventListener(opponentListener);
    }
}