package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class OnlineResultActivity extends Activity {

    TextView tvWinner, tvScoreSummary;
    Button btnPlayAgain, btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_result);

        tvWinner = findViewById(R.id.tvWinner);
        tvScoreSummary = findViewById(R.id.tvScoreSummary);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnClose = findViewById(R.id.btnClose);

        Intent intent = getIntent();

        // ✅ Data sent from OnlineGameActivity
        String result = intent.getStringExtra("RESULT");
        String mySecret = intent.getStringExtra("MY_SECRET");
        String opponentSecret = intent.getStringExtra("OPPONENT_SECRET");
        int attempts = intent.getIntExtra("ATTEMPTS", 0);

        // Winner text
        if ("DRAW".equals(result)) {
            tvWinner.setText("It's a Draw!");
        } else {
            tvWinner.setText(result);
        }

        tvScoreSummary.setText(
                "Total Attempts: " + attempts +
                "\nYour Secret: " + mySecret +
                "\nOpponent Secret: " + opponentSecret
        );

        // 🔁 Play again → go back to main screen (new game)
        btnPlayAgain.setOnClickListener(v -> {
            Intent restart =
                    new Intent(OnlineResultActivity.this, MainActivity.class);
            restart.setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );
            startActivity(restart);
            finish();
        });

        // ❌ Close app
        btnClose.setOnClickListener(v -> finishAffinity());
    }
}