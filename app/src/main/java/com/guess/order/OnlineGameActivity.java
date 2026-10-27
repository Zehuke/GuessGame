package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashSet;
import java.util.Set;

public class OnlineGameActivity extends Activity {
    private String code, uid, opponentUid, mySecret, roundKey;
    private DatabaseReference room;
    private EditText guessInput;
    private Button submit;
    private TextView state, history, secretView, roundView;
    private ValueEventListener metaListener, playersListener, secretListener, guessListener, doneListener, scoreListener, presenceListener;
    private String myGuess, opponentGuess;
    private boolean scoringOpponentGuess, resolving, finished;
    private final Set<String> rendered = new HashSet<>();
    private int attempts;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        code = getIntent().getStringExtra("ROOM_CODE");
        if (user == null || code == null) { finish(); return; }
        uid = user.getUid(); room = FirebaseDatabase.getInstance().getReference("rooms").child(code);
        buildUi(); attach();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(28), dp(22), dp(24));
        TextView label = text("LIVE MATCH", 13, R.color.brand, true); page.addView(label);
        TextView title = text("True & Position", 28, R.color.ink, true); LinearLayout.LayoutParams tp = params(); tp.topMargin = dp(8); page.addView(title, tp);
        roundView = text("Room " + code, 15, R.color.muted, false); page.addView(roundView);
        secretView = text("Your secret: ••••", 16, R.color.ink, true); LinearLayout.LayoutParams sp = params(); sp.topMargin = dp(20); page.addView(secretView, sp);
        state = text("Connecting to your opponent…", 15, R.color.muted, false); LinearLayout.LayoutParams st = params(); st.topMargin = dp(14); page.addView(state, st);
        guessInput = new EditText(this); guessInput.setSingleLine(true); guessInput.setInputType(2); guessInput.setHint("Enter four unique digits"); guessInput.setTextSize(22); guessInput.setLetterSpacing(.16f); guessInput.setBackgroundResource(R.drawable.rounded_edit_text); guessInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(62)); gp.topMargin = dp(20); page.addView(guessInput, gp);
        submit = new Button(this); submit.setText("Submit this round"); submit.setAllCaps(false); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54)); bp.topMargin = dp(10); page.addView(submit, bp);
        TextView histTitle = text("ROUND HISTORY", 13, R.color.muted, true); LinearLayout.LayoutParams hp = params(); hp.topMargin = dp(28); page.addView(histTitle, hp);
        history = text("Your guess     True     Position\n", 14, R.color.ink, false); LinearLayout.LayoutParams h = params(); h.topMargin = dp(8); page.addView(history, h);
        scroll.addView(page); setContentView(scroll);
        submit.setOnClickListener(v -> submitGuess());
    }

    private void attach() {
        playersListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                opponentUid = null;
                for (DataSnapshot p : snapshot.getChildren()) if (!uid.equals(p.getKey())) { opponentUid = p.getKey(); break; }
                if (opponentUid != null) listenPresence();
                if (opponentUid != null && roundKey != null && guessListener != null)
                    room.child("rounds").child(roundKey).child("guesses").addListenerForSingleValueEvent(guessListener);
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Players unavailable: " + error.getMessage()); }
        };
        room.child("players").addValueEventListener(playersListener);
        secretListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                mySecret = snapshot.getValue(String.class);
                if (mySecret != null) { secretView.setText("Your secret: " + mySecret); state.setText("Make your guess. Your opponent will score it against their secret."); attachMeta(); }
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Your private secret is unavailable: " + error.getMessage()); }
        };
        room.child("secrets").child(uid).addValueEventListener(secretListener);
    }

    private void attachMeta() {
        if (metaListener != null) return;
        metaListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                String statusText = snapshot.child("status").getValue(String.class);
                if ("finished".equals(statusText)) { showFinished(snapshot); return; }
                if (!"playing".equals(statusText)) { state.setText("Match status: " + statusText); return; }
                String next = snapshot.child("roundKey").getValue(String.class);
                if (next != null && !next.equals(roundKey)) beginRound(next);
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Match sync failed: " + error.getMessage()); }
        };
        room.child("meta").addValueEventListener(metaListener);
    }

    private void beginRound(String next) {
        removeRoundListeners(); roundKey = next; myGuess = null; opponentGuess = null; scoringOpponentGuess = false; resolving = false;
        guessInput.setText(""); guessInput.setEnabled(true); submit.setEnabled(true); roundView.setText("Room " + code + " · Round " + next.substring(1));
        state.setText("Choose one four-digit guess. You can submit once this round.");
        DatabaseReference guesses = room.child("rounds").child(next).child("guesses");
        guessListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                myGuess = snapshot.child(uid).getValue(String.class);
                opponentGuess = opponentUid == null ? null : snapshot.child(opponentUid).getValue(String.class);
                if (myGuess != null) { guessInput.setEnabled(false); submit.setEnabled(false); state.setText("Guess submitted. Waiting for the other player."); }
                if (opponentGuess != null) scoreOpponentGuess();
                if (opponentGuess != null && myGuess != null) listenDone();
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Could not load this round: " + error.getMessage()); }
        };
        guesses.addValueEventListener(guessListener);
        scoreListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) state.setText("Your score is in. Waiting for the round to resolve.");
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Could not load your score: " + error.getMessage()); }
        };
        room.child("rounds").child(next).child("scores").child(uid).addValueEventListener(scoreListener);
    }

    private void scoreOpponentGuess() {
        if (scoringOpponentGuess || opponentUid == null || mySecret == null || roundKey == null) return;
        scoringOpponentGuess = true;
        int[] score = GameRules.score(opponentGuess, mySecret);
        java.util.Map<String, Object> result = new java.util.HashMap<>(); result.put("trueCount", score[0]); result.put("positionCount", score[1]);
        DatabaseReference scoreRef = room.child("rounds").child(roundKey).child("scores").child(opponentUid);
        scoreRef.setValue(result).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                scoreRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot existing) { if (existing.exists()) markDone(opponentUid); else { scoringOpponentGuess = false; state.setText("Could not score opponent’s guess. Retry shortly."); } }
                    @Override public void onCancelled(DatabaseError error) { scoringOpponentGuess = false; }
                });
            } else markDone(opponentUid);
        });
    }

    private void markDone(String guessOwnerUid) {
        if (roundKey == null) return;
        room.child("rounds").child(roundKey).child("done").child(guessOwnerUid).setValue(true)
                .addOnCompleteListener(task -> { if (task.isSuccessful()) listenDone(); });
    }

    private void listenDone() {
        if (doneListener != null || roundKey == null) return;
        doneListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getChildrenCount() >= 2 && myGuess != null && opponentGuess != null) resolveRound();
            }
            @Override public void onCancelled(DatabaseError error) { state.setText("Round resolution is waiting for sync."); }
        };
        room.child("rounds").child(roundKey).child("done").addValueEventListener(doneListener);
    }

    private void resolveRound() {
        if (resolving || finished) return; resolving = true;
        // The score for our guess is returned by the opponent in our private scores path.
        room.child("rounds").child(roundKey).child("scores").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot ownScoreSnapshot) {
                if (!ownScoreSnapshot.exists()) { resolving = false; return; }
                int myPosition = value(ownScoreSnapshot, "positionCount");
                int opponentPosition = GameRules.score(opponentGuess, mySecret)[1];
                appendRow(myGuess, value(ownScoreSnapshot, "trueCount"), myPosition, opponentGuess, GameRules.score(opponentGuess, mySecret)[0], opponentPosition);
                boolean iWon = myPosition == 4, theyWon = opponentPosition == 4;
                String winner = iWon && theyWon ? "draw" : iWon ? uid : theyWon ? opponentUid : null;
                advanceRound(winner);
            }
            @Override public void onCancelled(DatabaseError error) { resolving = false; state.setText("Waiting for your score: " + error.getMessage()); }
        });
    }

    private void advanceRound(String winnerUid) {
        String resolvedKey = roundKey;
        room.child("meta").runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                if (!"playing".equals(data.child("status").getValue(String.class)) || !resolvedKey.equals(data.child("roundKey").getValue(String.class))) return Transaction.abort();
                data.child("updatedAt").setValue(ServerValue.TIMESTAMP);
                if (winnerUid != null) {
                    data.child("status").setValue("finished"); data.child("result").setValue("draw".equals(winnerUid) ? "draw" : "win");
                    if (!"draw".equals(winnerUid)) data.child("winnerUid").setValue(winnerUid);
                } else {
                    int n = parseRound(resolvedKey) + 1; data.child("roundKey").setValue("r" + n); data.child("round").setValue(n);
                }
                return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot data) {
                if (error != null) { resolving = false; state.setText("Could not resolve round: " + error.getMessage()); return; }
                if (winnerUid == null && !committed) { resolving = false; state.setText("Round already resolved. Syncing…"); }
            }
        });
    }

    private void showFinished(DataSnapshot meta) {
        if (finished) return; finished = true; submit.setEnabled(false); guessInput.setEnabled(false);
        String winner = meta.child("winnerUid").getValue(String.class);
        String result = winner == null ? "DRAW" : winner.equals(uid) ? "YOU WIN" : "YOU LOSE";
        state.setText("Match complete · " + result);
        recordResult("DRAW".equals(result) ? "draw" : "YOU WIN".equals(result) ? "win" : "loss");
        Intent intent = new Intent(this, OnlineResultActivity.class); intent.putExtra("RESULT", result); intent.putExtra("ROOM_CODE", code);
        state.postDelayed(() -> { if (!isFinishing()) { startActivity(intent); finish(); } }, 900);
    }

    private void recordResult(String result) {
        DatabaseReference marker = room.child("resultRecorded").child(uid);
        marker.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) { if (data.getValue() != null) return Transaction.abort(); data.setValue(true); return Transaction.success(data); }
            @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot s) {
                if (!committed || e != null) return;
                DatabaseReference profile = FirebaseDatabase.getInstance().getReference("profiles").child(uid);
                profile.runTransaction(new Transaction.Handler() {
                    @Override public Transaction.Result doTransaction(MutableData d) {
                        d.child("gamesPlayed").setValue(number(d, "gamesPlayed") + 1);
                        String key = "win".equals(result) ? "wins" : "loss".equals(result) ? "losses" : "draws";
                        d.child(key).setValue(number(d, key) + 1); return Transaction.success(d);
                    }
                    @Override public void onComplete(DatabaseError error, boolean ok, DataSnapshot s) { }
                });
                DatabaseReference opponent = room.child("players").child(opponentUid == null ? "unknown" : opponentUid);
                opponent.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot s) {
                        String other = s.getValue(String.class); if (other == null) other = "Opponent";
                        java.util.Map<String, Object> row = new java.util.HashMap<>(); row.put("opponentUid", opponentUid); row.put("opponentName", other); row.put("result", result); row.put("timestamp", ServerValue.TIMESTAMP); row.put("summary", "Room " + code);
                        DatabaseReference history = FirebaseDatabase.getInstance().getReference("matchHistory").child(uid);
                        history.push().setValue(row).addOnCompleteListener(saved -> {
                            if (!saved.isSuccessful()) return;
                            history.orderByChild("timestamp").limitToFirst(21).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(DataSnapshot recent) {
                                    if (recent.getChildrenCount() <= 20) return;
                                    int remove = (int) recent.getChildrenCount() - 20;
                                    for (DataSnapshot old : recent.getChildren()) {
                                        if (remove-- <= 0) break;
                                        old.getRef().removeValue();
                                    }
                                }
                                @Override public void onCancelled(DatabaseError error) { }
                            });
                        });
                    }
                    @Override public void onCancelled(DatabaseError error) { }
                });
            }
        });
    }

    private void submitGuess() {
        String value = guessInput.getText().toString().trim();
        if (!GameRules.isValidNumber(value)) { guessInput.setError("Use four different digits from 1 to 9."); return; }
        if (roundKey == null) { state.setText("Wait for the round to start."); return; }
        submit.setEnabled(false);
        room.child("rounds").child(roundKey).child("guesses").child(uid).setValue(value).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { attempts++; guessInput.setEnabled(false); state.setText("Guess submitted. Waiting for your opponent."); }
            else { submit.setEnabled(true); state.setText("Guess not saved. It may already be submitted or the connection failed."); }
        });
    }

    private void listenPresence() {
        if (presenceListener != null || opponentUid == null) return;
        presenceListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) { if (Boolean.FALSE.equals(s.getValue(Boolean.class))) state.setText("Opponent disconnected. Waiting for them to reconnect…"); }
            @Override public void onCancelled(DatabaseError error) { }
        };
        room.child("presence").child(opponentUid).addValueEventListener(presenceListener);
    }

    private void appendRow(String mine, int trueN, int pos, String theirs, int oppTrue, int oppPos) {
        if (!rendered.add(roundKey)) return;
        history.append("\n" + (roundKey == null ? "" : roundKey.substring(1)) + "     " + mine + "     " + trueN + "     " + pos + "   |   " + theirs + "     " + oppTrue + "     " + oppPos);
    }
    private void removeRoundListeners() {
        if (roundKey == null) return;
        if (guessListener != null) room.child("rounds").child(roundKey).child("guesses").removeEventListener(guessListener);
        if (doneListener != null) room.child("rounds").child(roundKey).child("done").removeEventListener(doneListener);
        if (scoreListener != null) room.child("rounds").child(roundKey).child("scores").child(uid).removeEventListener(scoreListener);
        guessListener = null; doneListener = null; scoreListener = null;
    }
    private int parseRound(String key) { try { return Integer.parseInt(key.substring(1)); } catch (Exception e) { return 0; } }
    private long number(MutableData data, String key) { Long value = data.child(key).getValue(Long.class); return value == null ? 0 : value; }
    private int value(DataSnapshot data, String key) { Integer n = data.child(key).getValue(Integer.class); if (n != null) return n; Long v = data.child(key).getValue(Long.class); return v == null ? 0 : v.intValue(); }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(getColor(color)); if (bold) t.setTypeface(null, Typeface.BOLD); return t; }
    private LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onBackPressed() { room.child("meta").child("status").setValue("abandoned").addOnCompleteListener(task -> finish()); }
    @Override protected void onDestroy() {
        removeRoundListeners();
        if (metaListener != null) room.child("meta").removeEventListener(metaListener);
        if (playersListener != null) room.child("players").removeEventListener(playersListener);
        if (secretListener != null) room.child("secrets").child(uid).removeEventListener(secretListener);
        if (presenceListener != null && opponentUid != null) room.child("presence").child(opponentUid).removeEventListener(presenceListener);
        super.onDestroy();
    }
}
