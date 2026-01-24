package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class OnlineResultActivity extends Activity {

    TextView tvWinner, tvScoreSummary;
    Button btnPlayAgain, btnClose;

    String roomCode;
    String myRole;

    DatabaseReference roomRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_result);

        tvWinner = findViewById(R.id.tvWinner);
        tvScoreSummary = findViewById(R.id.tvScoreSummary);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnClose = findViewById(R.id.btnClose);

        Intent intent = getIntent();

        // 🔑 Data from GameActivity
        String result = intent.getStringExtra("RESULT");
        String mySecret = intent.getStringExtra("MY_SECRET");
        String opponentSecret = intent.getStringExtra("OPPONENT_SECRET");
        int attempts = intent.getIntExtra("ATTEMPTS", 0);

        roomCode = intent.getStringExtra("ROOM_CODE");
        myRole = intent.getStringExtra("MY_ROLE");

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode);

        // ---------------- UI ----------------
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

        // 🔁 PLAY AGAIN → SAME ROOM → SETUP
        btnPlayAgain.setOnClickListener(v -> resetRoomAndRestart());

        // ❌ CLOSE → DELETE ROOM → EXIT
        btnClose.setOnClickListener(v -> {
            roomRef.removeValue();
            finishAffinity();
        });
    }

    // ---------------- RESET GAME ----------------
    private void resetRoomAndRestart() {

        // Clear previous game data
        roomRef.child("secrets").removeValue();
        roomRef.child("guesses").removeValue();
        roomRef.child("started").removeValue();

        // Mark player as active again
        roomRef.child("players").child(myRole).setValue(true);

        Intent intent = new Intent(
                OnlineResultActivity.this,
                OnlineSetupActivity.class
        );
        intent.putExtra("ROOM_CODE", roomCode);
        intent.putExtra("MY_ROLE", myRole);

        startActivity(intent);
        finish();
    }
}