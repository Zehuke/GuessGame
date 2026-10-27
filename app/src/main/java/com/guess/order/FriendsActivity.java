package com.guess.order;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
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
import java.util.Map;

/** Exact username lookup and owner-scoped requests, friends and room invitations. */
public class FriendsActivity extends Activity {
    private String uid, displayName;
    private DatabaseReference root;
    private LinearLayout people, requests, friends, invites;
    private EditText search;
    private TextView status;
    private ValueEventListener requestListener, friendsListener, invitesListener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid(); root = FirebaseDatabase.getInstance().getReference();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20), dp(24), dp(20), dp(24));
        TextView title = text("Friends", 28, R.color.ink, true); page.addView(title);
        TextView sub = text("Find players by their exact username. Emails are never shown.", 14, R.color.muted, false); page.addView(sub);
        search = new EditText(this); search.setSingleLine(true); search.setHint("Search username"); search.setBackgroundResource(R.drawable.rounded_edit_text); search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams si = new LinearLayout.LayoutParams(-1, dp(54)); si.topMargin = dp(18); page.addView(search, si);
        Button find = button("Find player"); page.addView(find); status = text("", 14, R.color.muted, false); page.addView(status);
        people = section(page, "SEARCH RESULT"); requests = section(page, "FRIEND REQUESTS"); friends = section(page, "YOUR FRIENDS"); invites = section(page, "ROOM INVITATIONS");
        scroll.addView(page); setContentView(scroll);
        find.setOnClickListener(v -> findUser());
        root.child("profiles").child(uid).child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) { displayName = s.getValue(String.class); if (displayName == null) displayName = "Player"; attachLists(); }
            @Override public void onCancelled(DatabaseError e) { displayName = "Player"; attachLists(); }
        });
    }

    private LinearLayout section(LinearLayout parent, String title) {
        TextView heading = text(title, 12, R.color.muted, true); LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(-1, -2); h.topMargin = dp(24); parent.addView(heading, h);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(-1, -2); r.topMargin = dp(8); parent.addView(rows, r); return rows;
    }

    private void findUser() {
        String name = search.getText().toString().trim().toLowerCase(java.util.Locale.US);
        if (!name.matches("[a-z0-9_]{3,20}")) { search.setError("Enter a valid username."); return; }
        people.removeAllViews(); status.setText("Searching…");
        root.child("usernames").child(name).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                String foundUid = snapshot.getValue(String.class);
                if (foundUid == null) { status.setText("No account found with that username."); return; }
                if (uid.equals(foundUid)) { status.setText("That’s your username."); return; }
                root.child("publicUsers").child(foundUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot profile) {
                        String shown = profile.child("displayName").getValue(String.class);
                        status.setText(""); addSearchResult(foundUid, shown == null ? name : shown, name);
                    }
                    @Override public void onCancelled(DatabaseError error) { status.setText("Could not load player details."); }
                });
            }
            @Override public void onCancelled(DatabaseError error) { status.setText("Search failed: " + error.getMessage()); }
        });
    }

    private void addSearchResult(String otherUid, String shown, String username) {
        LinearLayout row = row(); row.addView(text(shown + "  @" + username, 15, R.color.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button send = button("Add"); row.addView(send); people.addView(row, rowParams());
        send.setOnClickListener(v -> {
            root.child("friends").child(uid).child(otherUid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot existing) {
                    if (existing.exists()) { toast("You are already friends."); return; }
                    DatabaseReference request = root.child("friendRequests").child(otherUid).child(uid);
                    request.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(DataSnapshot current) {
                            String requestState = current.child("status").getValue(String.class);
                            if ("pending".equals(requestState)) { request.removeValue().addOnCompleteListener(task -> toast(task.isSuccessful() ? "Friend request cancelled." : "Could not cancel request.")); return; }
                            if ("accepted".equals(requestState)) { toast("You are already friends."); return; }
                            Map<String, Object> value = new HashMap<>(); value.put("senderUid", uid); value.put("recipientUid", otherUid);
                            value.put("senderName", displayName); value.put("createdAt", ServerValue.TIMESTAMP); value.put("status", "pending");
                            request.setValue(value).addOnCompleteListener(task -> toast(task.isSuccessful() ? "Friend request sent." : "Could not send request. Check connection and rules."));
                        }
                        @Override public void onCancelled(DatabaseError error) { toast("Could not check request status."); }
                    });
                }
                @Override public void onCancelled(DatabaseError error) { toast("Could not check friend status."); }
            });
        });
    }

    private void attachLists() {
        requestListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                requests.removeAllViews();
                for (DataSnapshot request : snapshot.getChildren()) {
                    if (!"pending".equals(request.child("status").getValue(String.class))) continue;
                    String senderUid = request.getKey(); String senderName = request.child("senderName").getValue(String.class);
                    LinearLayout row = row(); row.addView(text((senderName == null ? "Player" : senderName) + " wants to connect", 14, R.color.ink, false), new LinearLayout.LayoutParams(0, -2, 1));
                    Button accept = button("Accept"); Button reject = button("Decline"); row.addView(accept); row.addView(reject); requests.addView(row, rowParams());
                    accept.setOnClickListener(v -> answerRequest(senderUid, true)); reject.setOnClickListener(v -> answerRequest(senderUid, false));
                }
            }
            @Override public void onCancelled(DatabaseError e) { status.setText("Could not load friend requests: " + e.getMessage()); }
        };
        root.child("friendRequests").child(uid).addValueEventListener(requestListener);
        friendsListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                friends.removeAllViews();
                for (DataSnapshot friend : snapshot.getChildren()) {
                    String friendUid = friend.getKey();
                    root.child("publicUsers").child(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(DataSnapshot p) {
                            String name = p.child("displayName").getValue(String.class); String username = p.child("username").getValue(String.class);
                            LinearLayout row = row(); row.addView(text((name == null ? "Player" : name) + (username == null ? "" : "  @" + username), 14, R.color.ink, false), new LinearLayout.LayoutParams(0, -2, 1));
                            Button invite = button("Invite"); row.addView(invite); friends.addView(row, rowParams()); invite.setOnClickListener(v -> inviteFriend(friendUid));
                        }
                        @Override public void onCancelled(DatabaseError e) { }
                    });
                }
            }
            @Override public void onCancelled(DatabaseError e) { status.setText("Could not load friends: " + e.getMessage()); }
        };
        root.child("friends").child(uid).addValueEventListener(friendsListener);
        invitesListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                invites.removeAllViews();
                for (DataSnapshot invite : snapshot.getChildren()) {
                    String code = invite.getKey(); LinearLayout row = row(); row.addView(text("Private room  " + code, 14, R.color.ink, false), new LinearLayout.LayoutParams(0, -2, 1));
                    Button join = button("Join"); row.addView(join); invites.addView(row, rowParams());
                    join.setOnClickListener(v -> joinInvite(code));
                }
            }
            @Override public void onCancelled(DatabaseError e) { }
        };
        root.child("roomInvites").child(uid).addValueEventListener(invitesListener);
    }

    private void answerRequest(String senderUid, boolean accept) {
        DatabaseReference request = root.child("friendRequests").child(uid).child(senderUid);
        request.child("status").setValue(accept ? "accepted" : "rejected").addOnCompleteListener(task -> {
            if (!task.isSuccessful()) { toast("Could not update request."); return; }
            if (accept) {
                Map<String, Object> updates = new HashMap<>(); updates.put("friends/" + uid + "/" + senderUid, true); updates.put("friends/" + senderUid + "/" + uid, true);
                root.updateChildren(updates).addOnCompleteListener(done -> toast(done.isSuccessful() ? "You’re now friends." : "Request accepted. Friendship sync will retry when connected."));
            }
        });
    }

    private void inviteFriend(String friendUid) {
        new OnlineRoomRepository(displayName).createRoom(false, (code, error) -> {
            if (error != null) { toast(error); return; }
            root.child("roomInvites").child(friendUid).child(code).setValue(true).addOnCompleteListener(task -> {
                toast(task.isSuccessful() ? "Room " + code + " created and invitation sent." : "Room created, but invite could not be delivered.");
                if (task.isSuccessful()) { android.content.Intent intent = new android.content.Intent(this, OnlineSetupActivity.class); intent.putExtra("ROOM_CODE", code); startActivity(intent); }
            });
        });
    }

    private void joinInvite(String code) {
        new OnlineRoomRepository(displayName).joinRoom(code, (joined, error) -> {
            if (error != null) { toast(error); return; }
            root.child("roomInvites").child(uid).child(code).removeValue();
            android.content.Intent intent = new android.content.Intent(this, OnlineSetupActivity.class); intent.putExtra("ROOM_CODE", joined); startActivity(intent);
        });
    }

    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(8), dp(4), dp(8)); row.setBackgroundResource(R.drawable.rounded_edit_text); return row; }
    private LinearLayout.LayoutParams rowParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(7); return p; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(42)); return b; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(getColor(color)); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t; }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onDestroy() {
        if (requestListener != null) root.child("friendRequests").child(uid).removeEventListener(requestListener);
        if (friendsListener != null) root.child("friends").child(uid).removeEventListener(friendsListener);
        if (invitesListener != null) root.child("roomInvites").child(uid).removeEventListener(invitesListener);
        super.onDestroy();
    }
}
