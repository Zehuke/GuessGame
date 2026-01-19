package com.guess.order;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class OnlineResultActivity extends Activity {

    TextView tvWinner, tvScoreSummary;
    Button btnPlayAgain, btnClose;

    DatabaseReference roomRef;
    String roomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_result);

        tvWinner = findViewById(R.id.tvWinner);
        tvScoreSummary = findViewById(R.id.tvScoreSummary);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnClose = findViewById(R.id.btnClose);

        Intent intent = getIntent();

        String result = intent.getStringExtra("RESULT");
        String secretP1 = intent.getStringExtra("SECRET_P1");
        String secretP2 = intent.getStringExtra("SECRET_P2");
        int totalAttempts = intent.getIntExtra("TOTAL_ATTEMPTS", 0);

        // 🔑 room code (VERY IMPORTANT)
        roomCode = intent.getStringExtra("ROOM_CODE");

        roomRef = FirebaseDatabase
                .getInstance()
                .getReference("rooms")
                .child(roomCode);

        // Winner text
        if ("DRAW".equals(result)) {
            tvWinner.setText("It's a Draw!");
        } else {
            tvWinner.setText(result);
        }

        tvScoreSummary.setText(
                "Total Attempts: " + totalAttempts +
                "\nHost Secret: " + secretP1 +
                "\nGuest Secret: " + secretP2
        );

        btnPlayAgain.setOnClickListener(v ->
                clearRoomAndThen(() -> {
                    Intent restart =
                            new Intent(OnlineResultActivity.this, MainActivity.class);
                    restart.setFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );
                    startActivity(restart);
                    finish();
                })
        );

        btnClose.setOnClickListener(v ->
                clearRoomAndThen(this::finishAffinity)
        );
    }

    // 🔥 CENTRALIZED CLEANUP
    private void clearRoomAndThen(Runnable nextAction) {
        if (roomRef != null) {
            roomRef.removeValue().addOnCompleteListener(task -> {
                nextAction.run();
            });
        } else {
            nextAction.run();
        }
    }
}