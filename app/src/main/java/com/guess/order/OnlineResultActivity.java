package com.guess.order;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class OnlineResultActivity extends Activity {
    private String displayName = "Player";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String result = getIntent().getStringExtra("RESULT");
        String code = getIntent().getStringExtra("ROOM_CODE");
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER); page.setPadding(dp(24), dp(24), dp(24), dp(24)); page.setBackgroundColor(getColor(R.color.surface));
        TextView eyebrow = text("MATCH COMPLETE", 13, R.color.brand, true); page.addView(eyebrow);
        TextView title = text("DRAW".equals(result) ? "It’s a draw" : result == null ? "Match complete" : result, 30, R.color.ink, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(12); title.setGravity(Gravity.CENTER); page.addView(title, tp);
        TextView summary = text("Room " + (code == null ? "" : code) + "\nYour opponent’s secret stays private.\n\nCreate a new private room for a rematch and share its code.", 16, R.color.muted, false);
        summary.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(14); page.addView(summary, sp);
        Button rematch = new Button(this); rematch.setText("Create rematch room"); rematch.setAllCaps(false); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54)); bp.topMargin = dp(28); page.addView(rematch, bp);
        Button lobby = new Button(this); lobby.setText("Back to online lobby"); lobby.setAllCaps(false); page.addView(lobby, new LinearLayout.LayoutParams(-1, dp(50)));
        setContentView(page);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) FirebaseDatabase.getInstance().getReference("profiles").child(user.getUid()).child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) { String value = snapshot.getValue(String.class); if (value != null) displayName = value; }
            @Override public void onCancelled(DatabaseError error) { }
        });
        rematch.setOnClickListener(v -> new OnlineRoomRepository(displayName).createRoom(false, (roomCode, error) -> {
            if (error != null) { toast(error); return; }
            ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("GuessGame room", roomCode));
            toast("New room " + roomCode + " copied. Share it with your opponent.");
            Intent intent = new Intent(this, OnlineSetupActivity.class); intent.putExtra("ROOM_CODE", roomCode); startActivity(intent); finish();
        }));
        lobby.setOnClickListener(v -> { startActivity(new Intent(this, OnlineLobbyActivity.class)); finish(); });
    }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(getColor(color)); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
