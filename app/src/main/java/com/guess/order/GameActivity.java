package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import java.util.HashSet;
import java.util.Set;

public class GameActivity extends Activity {

    EditText etGuessP1, etGuessP2;
    Button btnSubmitP1, btnSubmitP2;
    TextView tvWaiting;
    LinearLayout resultContainer;

    String secretP1, secretP2;
    String guessP1 = null, guessP2 = null;

    int attemptsP1 = 0, attemptsP2 = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        secretP1 = getIntent().getStringExtra("P1_SECRET");
        secretP2 = getIntent().getStringExtra("P2_SECRET");

        etGuessP1 = findViewById(R.id.etGuessP1);
       etGuessP2 = findViewById(R.id.etGuessP2);
       btnSubmitP1 = findViewById(R.id.btnSubmitP1);
       btnSubmitP2 = findViewById(R.id.btnSubmitP2);
      tvWaiting = findViewById(R.id.tvWaiting);
       resultContainer = findViewById(R.id.resultContainer);

        btnSubmitP1.setOnClickListener(v -> {
            if (guessP1 != null) return;
            String input = etGuessP1.getText().toString().trim();

            if (!isValidGuess(input)) {
                etGuessP1.setError("Invalid guess");
                return;
            }

            guessP1 = input;
            btnSubmitP1.setEnabled(false);
            attemptsP1++;
            tvWaiting.setText("Player 1 submitted. Waiting for Player 2...");
			tvWaiting.setTextColor(Color.BLACK);
            checkBothSubmitted();
        });

        btnSubmitP2.setOnClickListener(v -> {
            if (guessP2 != null) return;
            String input = etGuessP2.getText().toString().trim();

            if (!isValidGuess(input)) {
                etGuessP2.setError("Invalid guess");
                return;
            }

            guessP2 = input;
            btnSubmitP2.setEnabled(false);
            attemptsP2++;
            tvWaiting.setText("Player 2 submitted. Waiting for Player 1...");
			tvWaiting.setTextColor(Color.BLACK);
            checkBothSubmitted();
        });
    }

    // ---------- HELPER METHODS ----------

    private boolean isValidGuess(String guess) {
        return GameRules.isValidNumber(guess);
    }

    private void checkBothSubmitted() {
        if (guessP1 != null && guessP2 != null) {

            tvWaiting.setText("");

            int[] resultP1 = GameRules.score(guessP1, secretP2);
            int[] resultP2 = GameRules.score(guessP2, secretP1);

            addResultRow(guessP1, resultP1, guessP2, resultP2);
            
            checkWin(resultP1, resultP2);

            guessP1 = null;
            guessP2 = null;
            btnSubmitP1.setEnabled(true);
            btnSubmitP2.setEnabled(true);

            etGuessP1.setText("");
            etGuessP2.setText("");
        }
    }

    private void addResultRow(
        String p1Guess, int[] p1Result,
        String p2Guess, int[] p2Result
) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(8));
        row.addView(playerResult("PLAYER ONE", p1Guess, p1Result));
        row.addView(playerResult("PLAYER TWO", p2Guess, p2Result), new LinearLayout.LayoutParams(0, -2, 1));
        resultContainer.addView(row);
}

    private View playerResult(String label, String guess, int[] score) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(8), dp(10));
        card.setBackgroundResource(R.drawable.rounded_edit_text);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(0, -2, 1);
        outer.setMarginEnd(dp(6));
        card.setLayoutParams(outer);
        TextView heading = new TextView(this); heading.setText(label); heading.setTextSize(10); heading.setTextColor(0xff5856d6);
        TextView guessText = new TextView(this); guessText.setText(guess); guessText.setTextSize(21); guessText.setTextColor(0xff191a24); guessText.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView scoreText = new TextView(this); scoreText.setText("True " + score[0] + "   Position " + score[1]); scoreText.setTextSize(12); scoreText.setTextColor(0xff74778a);
        card.addView(heading); card.addView(guessText); card.addView(scoreText); return card;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void checkWin(int[] p1Result, int[] p2Result) {

        boolean p1Win = p1Result[1] == 4;
        boolean p2Win = p2Result[1] == 4;

        if (p1Win || p2Win) {
            Intent intent = new Intent(this, ResultActivity.class);

            if (p1Win && p2Win) {
                intent.putExtra("RESULT", "DRAW");
            } else if (p1Win) {
                intent.putExtra("RESULT", "PLAYER 1 WINS");
            } else {
                intent.putExtra("RESULT", "PLAYER 2 WINS");
            }

            intent.putExtra("SECRET_P1", secretP1);
            intent.putExtra("SECRET_P2", secretP2);
            intent.putExtra("ATTEMPTS_P1", attemptsP1);
            intent.putExtra("ATTEMPTS_P2", attemptsP2);
            startActivity(intent);
            finish();
        }
    }
}
