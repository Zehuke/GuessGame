package com.guess.order;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Email/password account screen and idempotent profile initialization. */
public class AccountActivity extends Activity {
    private FirebaseAuth auth;
    private EditText emailInput, passwordInput, nameInput;
    private TextView status;
    private Button registerButton, loginButton, resetButton, signOutButton, continueButton, profileButton, claimButton, friendsButton;
    private boolean profileReady;
    private ProgressDialog progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        buildView();
        refreshSignedInState();
    }

    private void buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.setBackgroundResource(R.drawable.home_background);

        TextView title = new TextView(this);
        title.setText("Online account"); title.setTextSize(26); title.setTextColor(0xff202124);
        root.addView(title);
        status = new TextView(this); status.setTextColor(0xff202124); status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);
        emailInput = field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        passwordInput = field("Password (at least 6 characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        nameInput = field("Unique username (3–20 letters, numbers, _)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(emailInput); root.addView(passwordInput); root.addView(nameInput);
        registerButton = button("Create account"); loginButton = button("Sign in");
        resetButton = button("Reset password"); continueButton = button("Continue to online rooms"); signOutButton = button("Sign out"); profileButton = button("My profile and recent matches");
        claimButton = button("Save username"); friendsButton = button("Friends and invites");
        root.addView(registerButton); root.addView(loginButton); root.addView(resetButton); root.addView(claimButton); root.addView(continueButton); root.addView(profileButton); root.addView(friendsButton); root.addView(signOutButton);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        registerButton.setOnClickListener(v -> register());
        loginButton.setOnClickListener(v -> login());
        resetButton.setOnClickListener(v -> resetPassword());
        profileButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProfileActivity.class)));
        friendsButton.setOnClickListener(v -> startActivity(new android.content.Intent(this, FriendsActivity.class)));
        claimButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser(); if (user != null) claimUsername(user, nameInput.getText().toString());
        });
        signOutButton.setOnClickListener(v -> { auth.signOut(); refreshSignedInState(); });
        continueButton.setOnClickListener(v -> {
            if (auth.getCurrentUser() == null || !profileReady) { message("Complete your username setup before online play."); return; }
            startActivity(new android.content.Intent(this, OnlineLobbyActivity.class));
            finish();
        });
    }

    private EditText field(String hint, int type) {
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setInputType(type); edit.setHint(hint);
        edit.setTextColor(0xff202124); edit.setHintTextColor(0xff666666); return edit;
    }
    private Button button(String text) { Button b = new Button(this); b.setText(text); return b; }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void refreshSignedInState() {
        FirebaseUser user = auth.getCurrentUser();
        boolean signedIn = user != null;
        nameInput.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        registerButton.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        loginButton.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        resetButton.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        continueButton.setVisibility(signedIn && profileReady ? View.VISIBLE : View.GONE);
        signOutButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        profileButton.setVisibility(signedIn && profileReady ? View.VISIBLE : View.GONE);
        friendsButton.setVisibility(signedIn && profileReady ? View.VISIBLE : View.GONE);
        claimButton.setVisibility(View.GONE);
        status.setText(signedIn ? "Signed in as " + user.getEmail() : "Sign in or create an account for online play. Offline play does not require an account.");
        profileReady = false;
        if (signedIn) FirebaseDatabase.getInstance().getReference("profiles").child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) {
                boolean hasUsername = s.child("username").exists();
                profileReady = hasUsername;
                continueButton.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
                profileButton.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
                friendsButton.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
                claimButton.setVisibility(hasUsername ? View.GONE : View.VISIBLE);
                nameInput.setVisibility(hasUsername ? View.GONE : View.VISIBLE);
                if (hasUsername) ensureProfile(user);
                else status.setText("Choose a unique username to finish setting up this account.");
            }
            @Override public void onCancelled(DatabaseError error) { status.setText("Profile unavailable: " + error.getMessage()); }
        });
    }

    private void register() {
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        final String displayName = nameInput.getText().toString().trim();
        if (!validEmail(email)) { emailInput.setError("Enter a valid email address"); return; }
        if (password.length() < 6) { passwordInput.setError("Use at least 6 characters"); return; }
        if (!validUsername(displayName)) {
            nameInput.setError("Use 3–20 lowercase-compatible letters, numbers, or underscores."); return;
        }
        busy(true, "Creating account…");
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) { busy(false, null); showAuthError(task.getException()); return; }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { busy(false, null); message("Account created. Sign in to continue."); return; }
            busy(false, null);
            claimUsername(user, displayName);
        });
    }

    private void login() {
        final String email = emailInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (!validEmail(email)) { emailInput.setError("Enter a valid email address"); return; }
        if (password.isEmpty()) { passwordInput.setError("Enter your password"); return; }
        busy(true, "Signing in…");
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) { busy(false, null); showAuthError(task.getException()); return; }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { busy(false, null); message("Sign-in completed, but the account could not be loaded."); return; }
            busy(false, null); refreshSignedInState();
        });
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (!validEmail(email)) { emailInput.setError("Enter your account email first"); return; }
        busy(true, "Sending reset email…");
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            busy(false, null);
            if (task.isSuccessful()) message("If the account exists, a password reset email was sent.");
            else showAuthError(task.getException());
        });
    }

    private void ensureProfile(FirebaseUser user) { ensureProfile(user, null, null); }

    private void ensureProfile(FirebaseUser user, String username, Runnable complete) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("profiles").child(user.getUid());
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                Map<String, Object> patch = new HashMap<>();
                if (!snapshot.child("uid").exists()) patch.put("uid", user.getUid());
                if (!snapshot.child("username").exists()) {
                    if (username == null) { if (complete != null) complete.run(); return; }
                    patch.put("username", username);
                }
                if (!snapshot.child("displayName").exists()) {
                    String chosen = nameInput.getText().toString().trim();
                    if (chosen.length() < 2) chosen = user.getDisplayName();
                    if (chosen == null || chosen.trim().isEmpty()) chosen = user.getEmail() == null ? "Player" : user.getEmail().split("@")[0];
                    patch.put("displayName", chosen.length() > 24 ? chosen.substring(0, 24) : chosen);
                }
                if (!snapshot.child("createdAt").exists()) patch.put("createdAt", ServerValue.TIMESTAMP);
                if (!snapshot.child("gamesPlayed").exists()) patch.put("gamesPlayed", 0);
                if (!snapshot.child("wins").exists()) patch.put("wins", 0);
                if (!snapshot.child("losses").exists()) patch.put("losses", 0);
                if (!snapshot.child("draws").exists()) patch.put("draws", 0);
                if (patch.isEmpty()) { if (complete != null) complete.run(); return; }
                ref.updateChildren(patch).addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) message("Signed in, but profile sync failed. Check database rules and connection.");
                    if (complete != null) complete.run();
                });
            }
            @Override public void onCancelled(DatabaseError error) {
                message("Could not read profile: " + error.getMessage()); if (complete != null) complete.run();
            }
        });
    }

    private boolean validUsername(String value) { return value != null && value.matches("[A-Za-z0-9_]{3,20}"); }

    private void claimUsername(FirebaseUser user, String entered) {
        String username = entered == null ? "" : entered.trim().toLowerCase(Locale.US);
        if (!validUsername(username)) { nameInput.setError("Use 3–20 letters, numbers, or underscores."); return; }
        busy(true, "Saving username…");
        FirebaseDatabase.getInstance().getReference("usernames").child(username).runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData data) {
                Object owner = data.getValue();
                if (owner != null && !user.getUid().equals(String.valueOf(owner))) return com.google.firebase.database.Transaction.abort();
                data.setValue(user.getUid()); return com.google.firebase.database.Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null || !committed) {
                    busy(false, null);
                    message(error == null ? "That username is already taken." : "Could not reserve username: " + error.getMessage());
                    refreshSignedInState(); return;
                }
                ensureProfile(user, username, () -> {
                    DatabaseReference profile = FirebaseDatabase.getInstance().getReference("profiles").child(user.getUid());
                    profile.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(DataSnapshot s) {
                            String displayName = s.getValue(String.class); if (displayName == null || displayName.trim().isEmpty()) displayName = entered.trim();
                            Map<String, Object> publicRow = new HashMap<>(); publicRow.put("username", username); publicRow.put("displayName", displayName);
                            FirebaseDatabase.getInstance().getReference("publicUsers").child(user.getUid()).setValue(publicRow)
                                    .addOnCompleteListener(publicTask -> { busy(false, null); refreshSignedInState(); });
                        }
                        @Override public void onCancelled(DatabaseError e) { busy(false, null); message("Could not update public profile."); }
                    });
                });
            }
        });
    }

    private boolean validEmail(String value) { return android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches(); }
    private void busy(boolean on, String text) {
        if (on) { progress = ProgressDialog.show(this, "Please wait", text, true, false); }
        else if (progress != null) { progress.dismiss(); progress = null; }
        registerButton.setEnabled(!on); loginButton.setEnabled(!on); resetButton.setEnabled(!on);
    }
    private void showAuthError(Exception error) {
        String message = error == null ? "Authentication failed. Please try again." : error.getMessage();
        if (message != null && message.toLowerCase(Locale.US).contains("already in use")) message = "An account already uses this email. Try signing in.";
        message(message == null ? "Authentication failed." : message);
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
