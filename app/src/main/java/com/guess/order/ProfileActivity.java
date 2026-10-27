package com.guess.order;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

/** Owner-only profile and bounded recent history view. */
public class ProfileActivity extends Activity {
    private TextView body;
    private String profileText = "Loading profile…\n\nRecent matches\n";
    private String historyText = "Loading recent matches…";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setGravity(Gravity.TOP); page.setBackgroundResource(R.drawable.home_background);
        body = new TextView(this); body.setTextSize(18); body.setTextColor(0xff202124);
        page.addView(body); scroll.addView(page); setContentView(scroll);
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        root.child("profiles").child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) {
                if (!s.exists()) { profileText = "Profile not found. Sign in again to repair it.\n\nRecent matches\n"; render(); return; }
                profileText = "Profile\n" + s.child("displayName").getValue(String.class) +
                        "\n\nGames: " + number(s, "gamesPlayed") +
                        "\nWins: " + number(s, "wins") +
                        "\nLosses: " + number(s, "losses") +
                        "\nDraws: " + number(s, "draws") + "\n\nRecent matches\n";
                render();
            }
            @Override public void onCancelled(DatabaseError e) { profileText = "Could not load profile: " + e.getMessage() + "\n\nRecent matches\n"; render(); }
        });
        Query recent = root.child("matchHistory").child(user.getUid()).orderByChild("timestamp").limitToLast(20);
        recent.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) {
                if (!s.exists()) { historyText = "No completed matches yet."; render(); return; }
                StringBuilder rows = new StringBuilder();
                for (DataSnapshot match : s.getChildren()) {
                    String opponent = match.child("opponentName").getValue(String.class);
                    String result = match.child("result").getValue(String.class);
                    rows.append(result == null ? "Match" : result.toUpperCase()).append(" vs ")
                            .append(opponent == null ? "Player" : opponent).append('\n');
                }
                historyText = rows.toString(); render();
            }
            @Override public void onCancelled(DatabaseError e) { historyText = "Could not load match history: " + e.getMessage(); render(); }
        });
    }

    private long number(DataSnapshot s, String key) { Long n = s.child(key).getValue(Long.class); return n == null ? 0 : n; }
    private void render() { body.setText(profileText + historyText); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
}
