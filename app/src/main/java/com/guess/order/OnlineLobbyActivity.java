package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class OnlineLobbyActivity extends Activity {
    private Button createButton, createPrivateButton, joinButton, quickButton, cancelButton;
    private EditText codeInput;
    private TextView status, greeting;
    private LinearLayout roomsContainer;
    private OnlineRoomRepository rooms;
    private ValueEventListener roomsListener;
    private String displayName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, AccountActivity.class)); finish(); return;
        }
        setContentView(R.layout.activity_online_lobby);
        createButton = findViewById(R.id.btnCreateRoom);
        createPrivateButton = findViewById(R.id.btnCreatePrivate);
        joinButton = findViewById(R.id.btnJoinRoom);
        quickButton = findViewById(R.id.btnQuickMatch);
        cancelButton = findViewById(R.id.btnCancelMatch);
        codeInput = findViewById(R.id.etRoomCode);
        status = findViewById(R.id.tvStatus);
        greeting = findViewById(R.id.tvGreeting);
        roomsContainer = findViewById(R.id.roomsContainer);
        createButton.setEnabled(false); createPrivateButton.setEnabled(false); joinButton.setEnabled(false);
        status.setText("Loading your account…");
        DatabaseReference profile = FirebaseDatabase.getInstance().getReference("profiles").child(user.getUid());
        profile.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                String name = snapshot.child("displayName").getValue(String.class);
                displayName = name == null || name.trim().isEmpty() ? "Player" : name;
                greeting.setText("Ready, " + displayName);
                rooms = new OnlineRoomRepository(displayName);
                createButton.setEnabled(true); createPrivateButton.setEnabled(true); joinButton.setEnabled(true); quickButton.setEnabled(true);
                status.setText("Create a room, enter a code, or join a public table.");
                roomsListener = rooms.listenForPublicRooms(OnlineLobbyActivity.this::renderRooms);
            }
            @Override public void onCancelled(DatabaseError error) {
                status.setText("Could not load your profile. Check connection and database rules.");
                toast(error.getMessage());
            }
        });
        createButton.setOnClickListener(v -> createRoom(true));
        createPrivateButton.setOnClickListener(v -> createRoom(false));
        joinButton.setOnClickListener(v -> joinRoom(codeInput.getText().toString()));
        quickButton.setOnClickListener(v -> {
            setBusy(true, "Looking for an opponent…"); quickButton.setVisibility(View.GONE); cancelButton.setVisibility(View.VISIBLE);
            rooms.startQuickMatch((searchState, code, error) -> {
                if (isFinishing()) return;
                if ("searching".equals(searchState)) { status.setText("Searching for a player. You can cancel any time."); return; }
                quickButton.setVisibility(View.VISIBLE); cancelButton.setVisibility(View.GONE); setBusy(false, null);
                if ("error".equals(searchState)) { status.setText(error == null ? "Matchmaking failed." : error); return; }
                if ("cancelled".equals(searchState)) { status.setText("Search cancelled."); return; }
                if ("host".equals(searchState)) { openSetup(code); return; }
                if ("join".equals(searchState)) joinRoom(code);
            });
        });
        cancelButton.setOnClickListener(v -> { status.setText("Cancelling search…"); rooms.cancelQuickMatch(); });
    }

    private void createRoom(boolean isPublic) {
        setBusy(true, "Creating room…");
        rooms.createRoom(isPublic, (code, error) -> {
            if (isFinishing()) return;
            setBusy(false, null);
            if (error != null) { status.setText(error); return; }
            openSetup(code);
        });
    }

    private void joinRoom(String code) {
        setBusy(true, "Joining room…");
        rooms.joinRoom(code, (joinedCode, error) -> {
            if (isFinishing()) return;
            setBusy(false, null);
            if (error != null) { status.setText(error); return; }
            openSetup(joinedCode);
        });
    }

    private void renderRooms(List<OnlineRoomRepository.RoomSummary> items, String error) {
        roomsContainer.removeAllViews();
        if (error != null) { status.setText("Room list unavailable: " + error); return; }
        if (items.isEmpty()) {
            TextView empty = new TextView(this); empty.setText("No public rooms yet. Create one and invite someone.");
            empty.setTextColor(getColor(R.color.muted)); empty.setPadding(4, 16, 4, 16); roomsContainer.addView(empty); return;
        }
        for (OnlineRoomRepository.RoomSummary room : items) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(12), dp(10), dp(12));
            row.setBackgroundResource(R.drawable.rounded_edit_text);
            LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
            TextView host = new TextView(this); host.setText(room.hostName); host.setTextSize(16); host.setTypeface(null, Typeface.BOLD); host.setTextColor(getColor(R.color.ink));
            TextView sub = new TextView(this); sub.setText("Waiting · " + room.occupancy + "/2 · " + room.code); sub.setTextSize(13); sub.setTextColor(getColor(R.color.muted));
            details.addView(host); details.addView(sub);
            row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button join = new Button(this); join.setText("Join"); join.setAllCaps(false); join.setMinHeight(dp(44));
            join.setOnClickListener(v -> joinRoom(room.code)); row.addView(join);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8);
            roomsContainer.addView(row, params);
        }
    }

    private void openSetup(String code) {
        if (rooms != null) rooms.consumeQuickMatch(code);
        Intent intent = new Intent(this, OnlineSetupActivity.class);
        intent.putExtra("ROOM_CODE", code);
        startActivity(intent); finish();
    }
    private void setBusy(boolean busy, String message) {
        createButton.setEnabled(!busy); createPrivateButton.setEnabled(!busy); joinButton.setEnabled(!busy); quickButton.setEnabled(!busy);
        if (message != null) status.setText(message);
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        if (rooms != null) rooms.removePublicRoomsListener(roomsListener);
        super.onDestroy();
    }
}
