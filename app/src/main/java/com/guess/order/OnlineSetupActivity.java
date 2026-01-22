package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.*;

import java.util.HashSet;
import java.util.Set;

public class OnlineSetupActivity extends Activity {

    EditText etSecret;
    TextView tvError, tvStatus;
    Button btnSet, btnClear;

    DatabaseReference roomRef;
    ValueEventListener secretsListener;
	     DatabaseReference myPlayerRef;
         DatabaseReference opponentPlayerRef;
         ValueEventListener opponentListener;

    String roomCode;
    String myRole;
    String opponentRole;

    private boolean gameStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_setup);

        roomCode = getIntent().getStringExtra("ROOM_CODE");
        myRole = getIntent().getStringExtra("MY_ROLE");
        opponentRole = myRole.equals("player1") ? "player2" : "player1";

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode);
				
	    myPlayerRef = roomRef.child("players").child(myRole);
        opponentPlayerRef = roomRef.child("players").child(opponentRole);
		
		myPlayerRef.onDisconnect().setValue(false);
		
		listenForOpponentDisconnect();

        etSecret = findViewById(R.id.etSecret);
        tvError = findViewById(R.id.tvError);
        tvStatus = findViewById(R.id.tvStatus);
        btnSet = findViewById(R.id.btnSet);
        btnClear = findViewById(R.id.btnClear);

        btnSet.setOnClickListener(v -> setSecret());
        btnClear.setOnClickListener(v -> clearSecret());

        listenForSecrets();
		
    }

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

        // Write the secret to Firebase
        roomRef.child("secrets").child(myRole).setValue(input);

        etSecret.setEnabled(false);
        tvError.setText("Number set ✔");
        tvStatus.setText("Waiting for opponent…");
    }

    private void clearSecret() {
        etSecret.setText("");
        etSecret.setEnabled(true);
        tvError.setText("");

        roomRef.child("secrets").child(myRole).removeValue();
    }

    private void listenForSecrets() {
        // Listen only to secrets node
        secretsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (gameStarted) return;

                String mySecret = snapshot.child(myRole).getValue(String.class);
                String oppSecret = snapshot.child(opponentRole).getValue(String.class);

                // Update opponent status dynamically
                if (mySecret != null && oppSecret == null) {
                    tvStatus.setText("Waiting for opponent...");
                } else if (oppSecret != null && mySecret == null) {
                    tvStatus.setText("Opponent ready. Enter your number!");
                } else if (oppSecret != null && mySecret != null) {
                    // Both secrets exist, start game safely
                    startGameOnce();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        roomRef.child("secrets").addValueEventListener(secretsListener);
    }
	
	
	private void listenForOpponentDisconnect() {

    opponentListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            Boolean online = snapshot.getValue(Boolean.class);

            if (online != null && !online) {
                opponentDisconnected();
            }
        }

        @Override
        public void onCancelled(DatabaseError error) {}
    };

    opponentPlayerRef.addValueEventListener(opponentListener);
    }
	
	
	private void opponentDisconnected() {

    Toast.makeText(
            this,
            "Opponent disconnected",
            Toast.LENGTH_LONG
    ).show();

    roomRef.removeValue(); // delete room

    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
    finish();
    }
	
	
    @Override
    public void onBackPressed() {

    // Mark player offline
    if (myPlayerRef != null) {
        myPlayerRef.setValue(false);
    }

    // If host, delete room immediately
    if ("player1".equals(myRole) && roomRef != null) {
        roomRef.removeValue();
    }

    // Navigate back safely
    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
    finish();
   }
	
	

    private void startGameOnce() {
        if (gameStarted) return;
        gameStarted = true;

        // Mark as started once, so only one write happens
        roomRef.child("started").setValue(true);

        // Remove listener before moving to game
        if (secretsListener != null) {
            roomRef.child("secrets").removeEventListener(secretsListener);
        }

        Intent intent = new Intent(OnlineSetupActivity.this, OnlineGameActivity.class);
        intent.putExtra("ROOM_CODE", roomCode);
        intent.putExtra("MY_ROLE", myRole);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
    super.onDestroy();

    if (secretsListener != null) {
        roomRef.child("secrets").removeEventListener(secretsListener);
     } 

    if (opponentListener != null) {
        opponentPlayerRef.removeEventListener(opponentListener);
     }
    }  
}