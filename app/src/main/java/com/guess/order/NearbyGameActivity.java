package com.guess.order;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local peer game. Secret numbers stay on their owner's phone; only guesses and scores cross Nearby. */
public class NearbyGameActivity extends Activity implements NearbyTransport.Listener {
    private static final int PERMISSIONS_REQUEST = 702;
    private NearbyTransport transport;
    private LinearLayout page;
    private TextView status, history, roundLabel;
    private EditText secretInput, guessInput;
    private Button secretButton, guessButton;
    private String mySecret, myGuess, opponentGuess, result;
    private int round = 1, myTrue, myPosition = -1, attempts;
    private boolean connected, myReady, opponentReady, scoredIncoming, rematchPending;
    private final List<String> historyRows = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { transport = new NearbyTransport(this, this); }
        catch (Exception e) { buildStart(); status.setText("Google Play services is required for nearby play."); return; }
        buildStart();
    }

    private void buildStart() {
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24)); page.setBackgroundColor(getColor(R.color.surface));
        page.addView(text("NO INTERNET GAME DATA", 13, R.color.brand, true));
        TextView title = text("Play nearby", 30, R.color.ink, true); LinearLayout.LayoutParams ti = params(); ti.topMargin = dp(10); page.addView(title, ti);
        page.addView(text("Both players need GuessGame and Google Play services. Turn on Bluetooth and Wi-Fi. Connection codes are shown on both phones before either accepts.", 15, R.color.muted, false));
        status = text("Choose a role to start.", 15, R.color.muted, false); LinearLayout.LayoutParams st = params(); st.topMargin = dp(18); page.addView(status, st);
        Button advertise = button("Host a nearby game"); Button discover = button("Find a nearby game");
        LinearLayout.LayoutParams bp = params(); bp.topMargin = dp(18); page.addView(advertise, bp); LinearLayout.LayoutParams dp = params(); dp.topMargin = dp(8); page.addView(discover, dp);
        setContentView(page);
        advertise.setOnClickListener(v -> begin(true)); discover.setOnClickListener(v -> begin(false));
    }

    private void begin(boolean host) {
        if (!hasPermissions()) { requestPermissions(requiredPermissions(), PERMISSIONS_REQUEST); return; }
        if (transport == null) { status.setText("Nearby services are unavailable on this device."); return; }
        if (host) transport.advertise(deviceName()); else transport.discover();
    }

    private String deviceName() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) return "GuessGame player";
        return "GuessGame player";
    }

    private String[] requiredPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE); permissions.add(Manifest.permission.BLUETOOTH_CONNECT); permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        } else if (Build.VERSION.SDK_INT >= 29) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        else permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 32) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        return permissions.toArray(new String[0]);
    }

    private boolean hasPermissions() {
        for (String permission : requiredPermissions()) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != PERMISSIONS_REQUEST) return;
        boolean all = grants.length > 0;
        for (int grant : grants) all &= grant == PackageManager.PERMISSION_GRANTED;
        status.setText(all ? "Permissions ready. Tap host or find again." : "Nearby play needs the requested Bluetooth, Wi-Fi, and location permissions.");
    }

    @Override public void onStatus(String message) { if (status != null) status.setText(message); }
    @Override public void onApproval(String name, String digits, String endpointId) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Confirm nearby player")
                .setMessage("Connect to " + name + "? Compare this code on both phones:\n\n" + digits)
                .setPositiveButton("Codes match · connect", (dialog, which) -> transport.accept(endpointId))
                .setNegativeButton("Reject", (dialog, which) -> transport.reject(endpointId)).setCancelable(false).show());
    }
    @Override public void onConnected(String name) { runOnUiThread(() -> { connected = true; buildSecret(); }); }
    @Override public void onDisconnected() { runOnUiThread(() -> { connected = false; if (status != null) status.setText("Nearby connection ended. Return to the mode screen and reconnect to replay."); if (guessButton != null) guessButton.setEnabled(false); }); }
    @Override public void onError(String message) { runOnUiThread(() -> { if (status != null) status.setText(message); }); }

    @Override public void onMessage(JSONObject message) {
        runOnUiThread(() -> handleMessage(message));
    }

    private void buildSecret() {
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24), dp(28), dp(24), dp(24)); page.setGravity(Gravity.CENTER_VERTICAL); page.setBackgroundColor(getColor(R.color.surface));
        page.addView(text("NEARBY MATCH", 13, R.color.brand, true));
        TextView title = text("Set your secret", 29, R.color.ink, true); LinearLayout.LayoutParams tp = params(); tp.topMargin = dp(8); page.addView(title, tp);
        status = text("Connection confirmed. Your secret stays on this device.", 15, R.color.muted, false); LinearLayout.LayoutParams sp = params(); sp.topMargin = dp(8); page.addView(status, sp);
        secretInput = new EditText(this); secretInput.setSingleLine(true); secretInput.setInputType(2); secretInput.setHint("Four different digits from 1 to 9"); secretInput.setTextSize(20); secretInput.setLetterSpacing(.17f); secretInput.setBackgroundResource(R.drawable.rounded_edit_text); secretInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(60)); ip.topMargin = dp(24); page.addView(secretInput, ip);
        secretButton = button("Lock in secret"); LinearLayout.LayoutParams sb = params(); sb.topMargin = dp(12); page.addView(secretButton, sb);
        setContentView(page);
        secretButton.setOnClickListener(v -> lockSecret());
    }

    private void lockSecret() {
        String value = secretInput.getText().toString().trim();
        if (!GameRules.isValidNumber(value)) { secretInput.setError("Use four unique digits from 1 to 9."); return; }
        mySecret = value; myReady = true; secretInput.setEnabled(false); secretButton.setEnabled(false); secretButton.setText("Secret locked");
        send("READY");
        if (opponentReady) buildGame(); else status.setText("Secret locked. Waiting for the other player to finish setup.");
    }

    private void buildGame() {
        if (!myReady || !opponentReady) return;
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(26), dp(22), dp(22)); page.setBackgroundColor(getColor(R.color.surface));
        page.addView(text("NEARBY MATCH", 13, R.color.brand, true));
        TextView title = text("True & Position", 28, R.color.ink, true); LinearLayout.LayoutParams tp = params(); tp.topMargin = dp(8); page.addView(title, tp);
        roundLabel = text("Round " + round, 15, R.color.muted, false); page.addView(roundLabel);
        status = text("Enter your guess. One submission per round.", 15, R.color.muted, false); LinearLayout.LayoutParams st = params(); st.topMargin = dp(12); page.addView(status, st);
        guessInput = new EditText(this); guessInput.setSingleLine(true); guessInput.setInputType(2); guessInput.setHint("Four unique digits"); guessInput.setTextSize(22); guessInput.setLetterSpacing(.16f); guessInput.setBackgroundResource(R.drawable.rounded_edit_text); guessInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams gi = new LinearLayout.LayoutParams(-1, dp(60)); gi.topMargin = dp(18); page.addView(guessInput, gi);
        guessButton = button("Submit guess"); LinearLayout.LayoutParams gb = params(); gb.topMargin = dp(10); page.addView(guessButton, gb);
        TextView hTitle = text("ROUND HISTORY", 12, R.color.muted, true); LinearLayout.LayoutParams ht = params(); ht.topMargin = dp(24); page.addView(hTitle, ht);
        history = text("Round  You · True · Position   |   Opponent · True · Position", 13, R.color.ink, false); LinearLayout.LayoutParams h = params(); h.topMargin = dp(8); page.addView(history, h);
        setContentView(page);
        guessButton.setOnClickListener(v -> submitGuess());
    }

    private void submitGuess() {
        String guess = guessInput.getText().toString().trim();
        if (!GameRules.isValidNumber(guess)) { guessInput.setError("Use four unique digits from 1 to 9."); return; }
        if (myGuess != null) return;
        myGuess = guess; attempts++; guessInput.setEnabled(false); guessButton.setEnabled(false); status.setText("Guess sent. Waiting for the other player.");
        send("GUESS", "round", round, "value", guess);
    }

    private void handleMessage(JSONObject message) {
        String type = message.optString("type", "");
        if ("READY".equals(type)) { opponentReady = true; if (myReady) buildGame(); else if (status != null) status.setText("Other player is ready. Lock in your secret."); return; }
        if ("GUESS".equals(type) && message.optInt("round", -1) == round) {
            String incoming = message.optString("value", "");
            if (!GameRules.isValidNumber(incoming)) { onError("The other device sent an invalid guess."); return; }
            opponentGuess = incoming;
            int[] score = GameRules.score(incoming, mySecret);
            send("SCORE", "round", round, "trueCount", score[0], "positionCount", score[1]);
            scoredIncoming = true; maybeResolve(); return;
        }
        if ("SCORE".equals(type) && message.optInt("round", -1) == round && myGuess != null) {
            int truth = message.optInt("trueCount", -1), position = message.optInt("positionCount", -1);
            if (truth < 0 || truth > 4 || position < 0 || position > 4 || position > truth) return;
            myTrue = truth; myPosition = position; maybeResolve(); return;
        }
        if ("REMATCH".equals(type)) {
            new AlertDialog.Builder(this).setTitle("Rematch?").setMessage("The other player wants to play again. You will both choose new secrets.")
                    .setPositiveButton("Play again", (d, w) -> { send("REMATCH_OK"); resetForReplay(); })
                    .setNegativeButton("No thanks", (d, w) -> send("REMATCH_NO")).show(); return;
        }
        if ("REMATCH_OK".equals(type) && rematchPending) { resetForReplay(); return; }
        if ("REMATCH_NO".equals(type) && rematchPending) { rematchPending = false; status.setText("The other player declined the rematch."); }
    }

    private void maybeResolve() {
        if (myGuess == null || opponentGuess == null || !scoredIncoming || myPosition < 0) return;
        int[] opponentScore = GameRules.score(opponentGuess, mySecret);
        String row = round + "   " + myGuess + " · " + myTrue + " · " + myPosition + "   |   " + opponentGuess + " · " + opponentScore[0] + " · " + opponentScore[1];
        historyRows.add(row); StringBuilder all = new StringBuilder("Round  You · True · Position   |   Opponent · True · Position");
        for (String item : historyRows) all.append('\n').append(item); history.setText(all.toString());
        boolean mine = myPosition == 4, theirs = opponentScore[1] == 4;
        if (mine || theirs) { showResult(mine && theirs ? "DRAW" : mine ? "YOU WIN" : "YOU LOSE"); return; }
        round++; roundLabel.setText("Round " + round); myGuess = null; opponentGuess = null; myPosition = -1; myTrue = 0; scoredIncoming = false;
        guessInput.setText(""); guessInput.setEnabled(true); guessButton.setEnabled(true); status.setText("New round. Submit one guess.");
    }

    private void showResult(String value) {
        result = value; page.removeAllViews(); page.addView(text("MATCH COMPLETE", 13, R.color.brand, true));
        TextView title = text(value, 32, R.color.ink, true); LinearLayout.LayoutParams t = params(); t.topMargin = dp(10); page.addView(title, t);
        TextView detail = text("Your secret stayed on your phone throughout the game.", 15, R.color.muted, false); LinearLayout.LayoutParams d = params(); d.topMargin = dp(12); page.addView(detail, d);
        Button replay = button("Play a nearby rematch"); LinearLayout.LayoutParams r = params(); r.topMargin = dp(24); page.addView(replay, r);
        Button done = button("Finish"); page.addView(done, params());
        status = detail;
        replay.setOnClickListener(v -> { rematchPending = true; replay.setEnabled(false); detail.setText("Waiting for the other player…"); send("REMATCH"); });
        done.setOnClickListener(v -> { transport.stop(); finish(); });
    }

    private void resetForReplay() {
        round = 1; mySecret = null; myGuess = null; opponentGuess = null; myTrue = 0; myPosition = -1;
        myReady = false; opponentReady = false; scoredIncoming = false; rematchPending = false; historyRows.clear(); buildSecret();
    }

    private void send(String type, Object... pairs) {
        try { JSONObject message = new JSONObject(); message.put("type", type); for (int i = 0; i + 1 < pairs.length; i += 2) message.put(String.valueOf(pairs[i]), pairs[i + 1]); transport.send(message); }
        catch (Exception e) { onError("Could not send game message."); }
    }

    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(50)); return b; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(getColor(color)); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t; }
    private LinearLayout.LayoutParams params() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onBackPressed() { if (transport != null) transport.stop(); super.onBackPressed(); }
    @Override protected void onDestroy() { if (transport != null && !isChangingConfigurations()) transport.stop(); super.onDestroy(); }
}
