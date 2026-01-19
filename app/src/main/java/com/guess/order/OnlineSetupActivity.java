package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

import java.util.HashSet;
import java.util.Set;

public class OnlineSetupActivity extends Activity {

    EditText etSecret;
    TextView tvError, tvStatus;
    Button btnSet, btnClear;

    DatabaseReference roomRef;

    String roomCode;
    String myRole; // "player1" or "player2"

    boolean gameStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_setup);

        roomCode = getIntent().getStringExtra("ROOM_CODE");
        myRole = getIntent().getStringExtra("MY_ROLE");

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode);

        etSecret = findViewById(R.id.etSecret);
        tvError = findViewById(R.id.tvError);
        tvStatus = findViewById(R.id.tvStatus);
        btnSet = findViewById(R.id.btnSet);
        btnClear = findViewById(R.id.btnClear);

        btnSet.setOnClickListener(v -> setSecret());
        btnClear.setOnClickListener(v -> clearSecret());

        listenForOpponent();
        listenForGameStart();
    }

    // ---------------- LOGIC ----------------

    private boolean isValidNumber(String number) {
        if (number.length() != 4) return false;
        if (number.contains("0")) return false;

        Set<Character> set = new HashSet<>();
        for (char c : number.toCharArray()) set.add(c);

        return set.size() == 4;
    }

    private void setSecret() {
        String input = etSecret.getText().toString().trim();

        if (!isValidNumber(input)) {
            tvError.setText("Invalid format!");
            return;
        }

        roomRef.child("secrets").child(myRole).setValue(input);
        roomRef.child("ready").child(myRole).setValue(true);

        etSecret.setEnabled(false);
        tvError.setText("Number set ✔");
        tvStatus.setText("Waiting for opponent…");
    }

    private void clearSecret() {
        etSecret.setText("");
        etSecret.setEnabled(true);
        tvError.setText("");

        roomRef.child("ready").child(myRole).setValue(false);
    }

    // ---------------- SYNC ----------------

    private void listenForOpponent() {
        roomRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean p1 = Boolean.TRUE.equals(
                        snapshot.child("player1").getValue(Boolean.class));
                boolean p2 = Boolean.TRUE.equals(
                        snapshot.child("player2").getValue(Boolean.class));

                if (p1 && p2) {
                    tvStatus.setText("Both players ready ✔");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void listenForGameStart() {
        roomRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean p1 = snapshot.child("player1").getValue(Boolean.class);
                Boolean p2 = snapshot.child("player2").getValue(Boolean.class);

                if (Boolean.TRUE.equals(p1) && Boolean.TRUE.equals(p2)) {
                    startGameOnce();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void startGameOnce() {
        if (gameStarted) return;
        gameStarted = true;

        roomRef.child("started").setValue(true);

        Intent intent = new Intent(this, OnlineGameActivity.class);
        intent.putExtra("ROOM_CODE", roomCode);
        intent.putExtra("MY_ROLE", myRole);
        startActivity(intent);
        finish();
    }
}