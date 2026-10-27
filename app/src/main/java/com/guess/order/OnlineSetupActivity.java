package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

public class OnlineSetupActivity extends Activity {
    private String code, uid;
    private DatabaseReference room;
    private EditText secretInput;
    private TextView state, roomCode;
    private Button readyButton, cancelButton;
    private ValueEventListener roomListener, playersListener, readyListener;
    private boolean started;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        code = getIntent().getStringExtra("ROOM_CODE");
        if (user == null || code == null) { finish(); return; }
        uid = user.getUid();
        room = FirebaseDatabase.getInstance().getReference("rooms").child(code);
        buildUi();
        registerPresence();
        listen();
        readyButton.setOnClickListener(v -> setSecret());
        cancelButton.setOnClickListener(v -> leaveRoom());
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24)); page.setGravity(Gravity.CENTER_VERTICAL);
        page.setBackgroundColor(getColor(R.color.surface));
        TextView label = new TextView(this); label.setText("ROOM SETUP"); label.setTextColor(getColor(R.color.brand)); label.setTypeface(null, Typeface.BOLD); label.setTextSize(13);
        page.addView(label);
        TextView title = new TextView(this); title.setText("Choose your secret"); title.setTextSize(28); title.setTypeface(null, Typeface.BOLD); title.setTextColor(getColor(R.color.ink));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(10); page.addView(title, titleParams);
        roomCode = new TextView(this); roomCode.setText("Room " + code); roomCode.setTextSize(15); roomCode.setTextColor(getColor(R.color.muted));
        page.addView(roomCode);
        secretInput = new EditText(this); secretInput.setSingleLine(true); secretInput.setInputType(2); secretInput.setHint("Four unique digits from 1 to 9"); secretInput.setTextSize(20); secretInput.setLetterSpacing(.18f); secretInput.setBackgroundResource(R.drawable.rounded_edit_text); secretInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(62)); inputParams.topMargin = dp(30); page.addView(secretInput, inputParams);
        readyButton = new Button(this); readyButton.setText("Lock in secret"); readyButton.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(54)); buttonParams.topMargin = dp(12); page.addView(readyButton, buttonParams);
        state = new TextView(this); state.setText("Waiting for another player…"); state.setTextSize(15); state.setTextColor(getColor(R.color.muted));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2); stateParams.topMargin = dp(20); page.addView(state, stateParams);
        cancelButton = new Button(this); cancelButton.setText("Leave room"); cancelButton.setAllCaps(false);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(48)); cancelParams.topMargin = dp(12); page.addView(cancelButton, cancelParams);
        setContentView(page);
    }

    private void listen() {
        roomListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) { state.setText("This room is no longer available."); return; }
                String status = snapshot.child("meta/status").getValue(String.class);
                if ("waiting".equals(status)) state.setText("Share code " + code + " and wait for a player.");
                else if ("setup".equals(status)) state.setText("Both players are connected. Set your secret when you’re ready.");
                if ("playing".equals(status)) startGame();
                if ("finished".equals(status) || "abandoned".equals(status)) state.setText("This match has ended.");
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Room connection failed: " + error.getMessage()); }
        };
        room.child("meta").addValueEventListener(roomListener);
        playersListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getChildrenCount() < 2) state.setText("Share code " + code + " and wait for a player.");
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Player list unavailable: " + error.getMessage()); }
        };
        room.child("players").addValueEventListener(playersListener);
        readyListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                boolean ready = Boolean.TRUE.equals(snapshot.child(uid).getValue(Boolean.class));
                if (ready) { secretInput.setEnabled(false); readyButton.setEnabled(false); readyButton.setText("Secret saved"); }
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Status sync failed: " + error.getMessage()); }
        };
        room.child("ready").addValueEventListener(readyListener);
    }

    private void registerPresence() {
        DatabaseReference connected = FirebaseDatabase.getInstance().getReference(".info/connected");
        connected.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    room.child("presence").child(uid).onDisconnect().setValue(false);
                    room.child("presence").child(uid).setValue(true);
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void setSecret() {
        String value = secretInput.getText().toString().trim();
        if (!GameRules.isValidNumber(value)) { secretInput.setError("Use four different digits from 1 to 9."); return; }
        readyButton.setEnabled(false);
        room.child("secrets").child(uid).setValue(value).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) { readyButton.setEnabled(true); state.setText("Could not save secret. Check connection and room rules."); return; }
            room.child("ready").child(uid).setValue(true).addOnCompleteListener(ready -> {
                if (!ready.isSuccessful()) { readyButton.setEnabled(true); state.setText("Secret saved, but ready state could not sync."); return; }
                secretInput.setEnabled(false); readyButton.setText("Secret saved");
                maybeStartGame();
            });
        });
    }

    private void maybeStartGame() {
        room.child("ready").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot ready) {
                if (ready.getChildrenCount() < 2) return;
                DatabaseReference meta = room.child("meta");
                meta.runTransaction(new Transaction.Handler() {
                    @Override public Transaction.Result doTransaction(com.google.firebase.database.MutableData data) {
                        if (!"setup".equals(data.child("status").getValue(String.class))) return Transaction.abort();
                        data.child("status").setValue("playing"); data.child("updatedAt").setValue(ServerValue.TIMESTAMP);
                        return Transaction.success(data);
                    }
                    @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                        if (error != null && !isFinishing()) state.setText("Could not start match: " + error.getMessage());
                    }
                });
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Could not check player readiness."); }
        });
    }

    private void startGame() {
        if (started || isFinishing()) return; started = true;
        Intent intent = new Intent(this, OnlineGameActivity.class); intent.putExtra("ROOM_CODE", code);
        startActivity(intent); finish();
    }

    private void leaveRoom() {
        room.child("meta").child("status").setValue("abandoned").addOnCompleteListener(task -> finish());
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onBackPressed() { leaveRoom(); }
    @Override protected void onDestroy() {
        if (roomListener != null) room.child("meta").removeEventListener(roomListener);
        if (playersListener != null) room.child("players").removeEventListener(playersListener);
        if (readyListener != null) room.child("ready").removeEventListener(readyListener);
        super.onDestroy();
    }
}
