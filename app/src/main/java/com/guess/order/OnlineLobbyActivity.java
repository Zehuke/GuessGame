package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.widget.Toast;

import com.google.firebase.database.*;

import java.util.Random;

public class OnlineLobbyActivity extends Activity {

    Button btnCreate, btnJoin;
    EditText etRoomCode;
    TextView tvStatus;

    DatabaseReference roomsRef;

    ValueEventListener playersListener;
    ValueEventListener roomDeleteListener;

    // 🔑 Track created room (host only)
    String createdRoomCode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_lobby);

        roomsRef = FirebaseDatabase.getInstance().getReference("rooms");

        btnCreate = findViewById(R.id.btnCreateRoom);
        btnJoin = findViewById(R.id.btnJoinRoom);
        etRoomCode = findViewById(R.id.etRoomCode);
        tvStatus = findViewById(R.id.tvStatus);

        btnCreate.setOnClickListener(v -> createRoom());
        btnJoin.setOnClickListener(v -> joinRoom());
    }

    // ---------------- CREATE ROOM ----------------
    private void createRoom() {
        String code = generateRoomCode();
        createdRoomCode = code;

        DatabaseReference room = roomsRef.child(code);

        // Clean old data
        room.removeValue();

        // Auto-delete if host loses connection
        room.onDisconnect().removeValue();

        // Add host
        room.child("players").child("player1").setValue(true);

        tvStatus.setText("Room created: " + code + "\nWaiting for player…");

        listenForRoomDeletion(code);
        waitForSecondPlayer(code);
    }

    // ---------------- JOIN ROOM ----------------
    private void joinRoom() {
        String code = etRoomCode.getText().toString().trim();

        if (code.isEmpty()) {
            tvStatus.setText("Enter room code");
            return;
        }

        DatabaseReference room = roomsRef.child(code);

        room.child("players").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists() || snapshot.hasChild("player2")) {
                            tvStatus.setText("Room unavailable");
                            return;
                        }

                        room.child("players").child("player2").setValue(true);
                        startSetup(code, false);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        tvStatus.setText("Error joining room");
                    }
                }
        );
    }

    // ---------------- WAIT FOR PLAYER 2 ----------------
    private void waitForSecondPlayer(String code) {
        playersListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.hasChild("player2")) {

                    roomsRef.child(code)
                            .child("players")
                            .removeEventListener(this);

                    if (roomDeleteListener != null) {
                        roomsRef.child(code)
                                .removeEventListener(roomDeleteListener);
                    }

                    startSetup(code, true);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        roomsRef.child(code)
                .child("players")
                .addValueEventListener(playersListener);
    }

    // ---------------- ROOM DELETION LISTENER ----------------
    private void listenForRoomDeletion(String code) {
        DatabaseReference roomRef = roomsRef.child(code);

        roomDeleteListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {

                    Toast.makeText(
                            OnlineLobbyActivity.this,
                            "Connection lost. Room was deleted.",
                            Toast.LENGTH_LONG
                    ).show();

                    tvStatus.setText("");
                    createdRoomCode = null;

                    roomRef.removeEventListener(this);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        roomRef.addValueEventListener(roomDeleteListener);
    }

    // ---------------- BACK BUTTON ----------------
    @Override
    public void onBackPressed() {

        // If host created a room, delete it
        if (createdRoomCode != null) {
            DatabaseReference roomRef =
                    roomsRef.child(createdRoomCode);

            roomRef.removeValue();
            createdRoomCode = null;
        }

        // Clean listeners
        if (playersListener != null) {
            roomsRef.removeEventListener(playersListener);
        }

        if (roomDeleteListener != null) {
            roomsRef.removeEventListener(roomDeleteListener);
        }

        finish();
    }

    // ---------------- NAVIGATION ----------------
    private void startSetup(String code, boolean isHost) {
        Intent intent = new Intent(
                OnlineLobbyActivity.this,
                OnlineSetupActivity.class
        );
        intent.putExtra("ROOM_CODE", code);
        intent.putExtra("MY_ROLE", isHost ? "player1" : "player2");
        startActivity(intent);
        finish();
    }

    // ---------------- UTIL ----------------
    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ123456789";
        Random r = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(r.nextInt(chars.length())));
        }

        return code.toString();
    }
}