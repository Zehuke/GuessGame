package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.database.*;

import java.util.Random;

public class OnlineLobbyActivity extends Activity {

    Button btnCreate, btnJoin;
    EditText etRoomCode;
    TextView tvStatus;

    DatabaseReference roomsRef;

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

    private void createRoom() {
        String code = generateRoomCode();

        DatabaseReference room = roomsRef.child(code);
        room.child("status").setValue("waiting");
        room.child("player1").setValue("connected");

        tvStatus.setText("Room created: " + code);

        waitForPlayer(code);
    }

    private void joinRoom() {
        String code = etRoomCode.getText().toString().trim();

        if (code.isEmpty()) {
            tvStatus.setText("Enter room code");
            return;
        }

        DatabaseReference room = roomsRef.child(code);

        room.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    tvStatus.setText("Room not found");
                    return;
                }

                room.child("player2").setValue("connected");
                room.child("status").setValue("ready");

                startOnlineGame(code, false);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                tvStatus.setText("Error joining room");
            }
        });
    }

    private void waitForPlayer(String code) {
        roomsRef.child(code).child("status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if ("ready".equals(snapshot.getValue(String.class))) {
                            startOnlineGame(code, true);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void startOnlineGame(String code, boolean isHost) {
    Intent intent = new Intent(this, OnlineGameActivity.class);
    intent.putExtra("ROOM_CODE", code);
    intent.putExtra("IS_HOST", isHost);
    startActivity(intent);
    finish();
}

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ123456789";
        Random r = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++)
            code.append(chars.charAt(r.nextInt(chars.length())));

        return code.toString();
    }
}