package com.guess.order;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import android.app.Activity;

public class ResultActivity extends Activity {

    TextView tvWinner, tvScoreSummary;
    Button btnPlayAgain, btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        tvWinner = findViewById(R.id.tvWinner);
        tvScoreSummary = findViewById(R.id.tvScoreSummary);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnClose = findViewById(R.id.btnClose);

        // Receive data from GameActivity
        Intent intent = getIntent();
        String result = intent.getStringExtra("RESULT");
        String secretP1 = intent.getStringExtra("SECRET_P1");
        String secretP2 = intent.getStringExtra("SECRET_P2");
        int attemptsP1 = intent.getIntExtra("ATTEMPTS_P1", 0);
        int attemptsP2 = intent.getIntExtra("ATTEMPTS_P2", 0);

        // Display winner / draw
        if ("DRAW".equals(result)) {
            tvWinner.setText("It's a Draw!");
        } else {
            tvWinner.setText(result);
        }

        // Display attempts summary
        tvScoreSummary.setText(
		"Player 1 · " + attemptsP1 + " guesses" +
   "\nPlayer 2 · " + attemptsP2 + " guesses" +
   "\n\nPlayer 1 secret: " + secretP1 +
   "\nPlayer 2 secret: " + secretP2
        );

        // Play again → go to home screen
        btnPlayAgain.setOnClickListener(v -> {
            Intent restart = new Intent(ResultActivity.this, MainActivity.class);
            restart.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restart);
            finish();
        });

        // Close app
        btnClose.setOnClickListener(v -> finishAffinity());
    }
}
