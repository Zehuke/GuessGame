package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
            String input = etGuessP1.getText().toString().trim();

            if (!isValidGuess(input)) {
                etGuessP1.setError("Invalid guess");
                return;
            }

            guessP1 = input;
            attemptsP1++;
            tvWaiting.setText("Player 1 submitted. Waiting for Player 2...");
			tvWaiting.setTextColor(Color.BLACK);
            checkBothSubmitted();
        });

        btnSubmitP2.setOnClickListener(v -> {
            String input = etGuessP2.getText().toString().trim();

            if (!isValidGuess(input)) {
                etGuessP2.setError("Invalid guess");
                return;
            }

            guessP2 = input;
            attemptsP2++;
            tvWaiting.setText("Player 2 submitted. Waiting for Player 1...");
			tvWaiting.setTextColor(Color.BLACK);
            checkBothSubmitted();
        });
    }

    // ---------- HELPER METHODS ----------

    private boolean isValidGuess(String guess) {
        if (guess.length() != 4) return false;
        if (guess.contains("0")) return false;

        Set<Character> set = new HashSet<>();
        for (char c : guess.toCharArray()) set.add(c);

        return set.size() == 4;
    }

    private void checkBothSubmitted() {
        if (guessP1 != null && guessP2 != null) {

            tvWaiting.setText("");

            int[] resultP1 = calculateResult(guessP1, secretP2);
            int[] resultP2 = calculateResult(guessP2, secretP1);

            addResultRow(guessP1, resultP1, guessP2, resultP2);
            
            checkWin(resultP1, resultP2);

            guessP1 = null;
            guessP2 = null;

            etGuessP1.setText("");
            etGuessP2.setText("");
        }
    }

    private int[] calculateResult(String guess, String secret) {
        int position = 0;
        int trueCount = 0;

        for (int i = 0; i < 4; i++) {
            if (guess.charAt(i) == secret.charAt(i)) {
                position++;
            }  if (secret.indexOf(guess.charAt(i)) != -1) {
                trueCount++;
            }
        }
        return new int[]{trueCount, position};
    }

    private void addResultRow(
        String p1Guess, int[] p1Result,
        String p2Guess, int[] p2Result
) {

    // One row per round
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    ));

    // Each cell = equal width
    // --- Player 1 cells ---
LinearLayout.LayoutParams p1Params =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
		
		
		    // Player 2 cells
    TextView p2GuessTv = new TextView(this);
    TextView p2NumTv = new TextView(this);
    TextView p2PosTv = new TextView(this);
		

      // --- Player 2 cells ---
int marginPx = (int) (16 * getResources().getDisplayMetrics().density);

// First cell (with margin)
LinearLayout.LayoutParams p2GuessParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
p2GuessParams.setMarginStart(marginPx);
p2GuessTv.setLayoutParams(p2GuessParams);

// Remaining cells (no margin)
LinearLayout.LayoutParams p2NumParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
p2NumTv.setLayoutParams(p2NumParams);

LinearLayout.LayoutParams p2PosParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
p2PosTv.setLayoutParams(p2PosParams);

    // Player 1 cells
    TextView p1GuessTv = new TextView(this);
    TextView p1NumTv = new TextView(this);
    TextView p1PosTv = new TextView(this);


    // Apply params
    p1GuessTv.setLayoutParams(p1Params);
    p1NumTv.setLayoutParams(p1Params);
    p1PosTv.setLayoutParams(p1Params);
    
	  

    // Set text
    p1GuessTv.setText(p1Guess);
    p1NumTv.setText(String.valueOf(p1Result[0]));
    p1PosTv.setText(String.valueOf(p1Result[1]));

    p2GuessTv.setText(p2Guess);
    p2NumTv.setText(String.valueOf(p2Result[0]));
    p2PosTv.setText(String.valueOf(p2Result[1]));

    // Optional: text size
    p1GuessTv.setTextSize(12);
    p1NumTv.setTextSize(12);
    p1PosTv.setTextSize(12);
    p2GuessTv.setTextSize(12);
    p2NumTv.setTextSize(12);
    p2PosTv.setTextSize(12);
	
	//--- Set text color to white ---
p1GuessTv.setTextColor(Color.BLACK);
p1NumTv.setTextColor(Color.BLACK);
p1PosTv.setTextColor(Color.BLACK);
p2GuessTv.setTextColor(Color.BLACK);
p2NumTv.setTextColor(Color.BLACK);
p2PosTv.setTextColor(Color.BLACK);

    // Add all 6 cells
    row.addView(p1GuessTv);
    row.addView(p1NumTv);
    row.addView(p1PosTv);
    row.addView(p2GuessTv);
    row.addView(p2NumTv);
    row.addView(p2PosTv);

    // Add row to container
    resultContainer.addView(row);
}

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
            intent.putExtra("TOTAL_ATTEMPTS", attemptsP1);
            startActivity(intent);
            finish();
        }
    }
}