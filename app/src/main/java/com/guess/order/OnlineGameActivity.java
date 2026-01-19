package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class OnlineGameActivity extends Activity {

    EditText etGuess;
    Button btnSubmit;
    TextView tvWaiting;

    LinearLayout containerYou, containerOpponent;

    String yourSecret, opponentSecret;
    String yourGuess, opponentGuess;

    int yourAttempts = 0;

    // 🔑 ROOM CODE
    String roomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        // Receive data from previous activity
        yourSecret = getIntent().getStringExtra("YOUR_SECRET");
        opponentSecret = getIntent().getStringExtra("OPPONENT_SECRET");
        roomCode = getIntent().getStringExtra("ROOM_CODE"); // <-- room code

        etGuess = findViewById(R.id.etGuess);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvWaiting = findViewById(R.id.tvWaiting);

        containerYou = findViewById(R.id.containerYou);
        containerOpponent = findViewById(R.id.containerOpponent);

        btnSubmit.setOnClickListener(v -> submitGuess());
    }

    private void submitGuess() {
        yourGuess = etGuess.getText().toString().trim();

        if (!isValidGuess(yourGuess)) {
            etGuess.setError("Invalid guess");
            return;
        }

        yourAttempts++;
        tvWaiting.setText("Waiting for opponent...");
        etGuess.setText("");

        // TEMP: simulate opponent guess (Firebase will replace this later)
        opponentGuess = "1234";

        processRound();
    }

    private void processRound() {
        int[] yourResult = calculateResult(yourGuess, opponentSecret);
        int[] opponentResult = calculateResult(opponentGuess, yourSecret);

        addRow(containerYou, yourGuess, yourResult);
        addRow(containerOpponent, opponentGuess, opponentResult);

        checkWin(yourResult, opponentResult);

        yourGuess = null;
        opponentGuess = null;
        tvWaiting.setText("");
    }

    private void addRow(LinearLayout container, String guess, int[] result) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(createCell(guess));
        row.addView(createCell(String.valueOf(result[0])));
        row.addView(createCell(String.valueOf(result[1])));

        container.addView(row);
    }

    private TextView createCell(String text) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        ));
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12);
        return tv;
    }

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

    private void checkWin(int[] yourResult, int[] opponentResult) {

        boolean youWin = yourResult[1] == 4;
        boolean opponentWin = opponentResult[1] == 4;

        if (youWin || opponentWin) {
            Intent intent = new Intent(this, OnlineResultActivity.class);

            if (youWin && opponentWin) {
                intent.putExtra("RESULT", "DRAW");
            } else if (youWin) {
                intent.putExtra("RESULT", "YOU WIN");
            } else {
                intent.putExtra("RESULT", "YOU LOSE");
            }

            intent.putExtra("ATTEMPTS", yourAttempts);
            intent.putExtra("YOUR_SECRET", yourSecret);
            intent.putExtra("OPPONENT_SECRET", opponentSecret);

            // 🔑 Send room code to result activity
            intent.putExtra("ROOM_CODE", roomCode);

            startActivity(intent);
            finish();
        }
    }
}