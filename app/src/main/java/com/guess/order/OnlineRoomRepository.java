package com.guess.order;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ServerValue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UID-based RTDB room operations. Room data never stores both secrets in a shared-readable node. */
public final class OnlineRoomRepository {
    public interface RoomCallback { void onResult(String code, String error); }
    public interface RoomsCallback { void onRooms(List<RoomSummary> rooms, String error); }
    public interface SearchCallback { void onState(String state, String code, String error); }
    public static final class RoomSummary {
        public String code, hostName, status;
        public int occupancy;
        public RoomSummary(String code, String hostName, String status, int occupancy) {
            this.code = code; this.hostName = hostName; this.status = status; this.occupancy = occupancy;
        }
    }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long LOCK_MS = 18000L;
    private static final long QUEUE_TTL_MS = 120000L;
    private final DatabaseReference root = FirebaseDatabase.getInstance().getReference();
    private final FirebaseUser user;
    private final String displayName;
    private final SecureRandom random = new SecureRandom();
    private DatabaseReference queueRef;
    private ValueEventListener queueListener;
    private ValueEventListener ownQueueListener;
    private SearchCallback searchCallback;
    private boolean searching, working;
    private final String lockToken;
    private long clockOffset;

    public OnlineRoomRepository(String displayName) {
        this.user = FirebaseAuth.getInstance().getCurrentUser();
        this.displayName = displayName;
        this.lockToken = user == null ? "" : user.getUid();
    }

    public boolean isSignedIn() { return user != null; }

    public void createRoom(boolean isPublic, RoomCallback callback) {
        if (user == null) { callback.onResult(null, "Sign in to create an online room."); return; }
        String code = newCode();
        DatabaseReference listing = root.child("roomListings").child(code);
        listing.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                if (data.getValue() != null) return Transaction.abort();
                Map<String, Object> row = new HashMap<>();
                row.put("hostUid", user.getUid()); row.put("hostName", displayName);
                row.put("status", "waiting"); row.put("browseKey", isPublic ? "waiting_public" : "waiting_private");
                row.put("occupancy", 1); row.put("public", isPublic); row.put("createdAt", ServerValue.TIMESTAMP);
                data.setValue(row); return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) { callback.onResult(null, error.getMessage()); return; }
                if (!committed) { createRoom(isPublic, callback); return; }
                Map<String, Object> updates = new HashMap<>();
                Map<String, Object> meta = new HashMap<>();
                meta.put("status", "waiting"); meta.put("round", 0); meta.put("roundKey", "r0"); meta.put("hostUid", user.getUid());
                meta.put("createdAt", ServerValue.TIMESTAMP); meta.put("updatedAt", ServerValue.TIMESTAMP);
                Map<String, Object> player = playerValue(displayName);
                updates.put("rooms/" + code + "/meta", meta);
                updates.put("rooms/" + code + "/players/" + user.getUid(), player);
                root.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onResult(code, null);
                    else {
                        listing.removeValue();
                        callback.onResult(null, task.getException() == null ? "Room creation failed." : task.getException().getMessage());
                    }
                });
            }
        });
    }

    public void joinRoom(String rawCode, RoomCallback callback) {
        if (user == null) { callback.onResult(null, "Sign in to join an online room."); return; }
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (!code.matches("[A-Z0-9]{6}")) { callback.onResult(null, "Room codes use six letters or numbers."); return; }
        DatabaseReference listing = root.child("roomListings").child(code);
        DatabaseReference reservation = root.child("roomReservations").child(code).child("guest");
        reservation.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                if (data.getValue() != null) return Transaction.abort();
                Map<String, Object> row = new HashMap<>(); row.put("uid", user.getUid()); row.put("displayName", displayName); row.put("reservedAt", ServerValue.TIMESTAMP);
                data.setValue(row);
                return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) { callback.onResult(null, error.getMessage()); return; }
                if (!committed) { callback.onResult(null, "Another player is joining or this room is unavailable."); return; }
                listing.runTransaction(new Transaction.Handler() {
                    @Override public Transaction.Result doTransaction(MutableData data) {
                        String host = data.child("hostUid").getValue(String.class);
                        Long count = data.child("occupancy").getValue(Long.class);
                        if (host == null || host.equals(user.getUid()) || count == null || count != 1 || !"waiting".equals(data.child("status").getValue(String.class))) return Transaction.abort();
                        data.child("occupancy").setValue(2); data.child("status").setValue("setup"); data.child("browseKey").setValue("full");
                        return Transaction.success(data);
                    }
                    @Override public void onComplete(DatabaseError listingError, boolean accepted, DataSnapshot listingSnapshot) {
                        if (listingError != null || !accepted) { reservation.removeValue(); callback.onResult(null, listingError == null ? "That room is full or unavailable." : listingError.getMessage()); return; }
                        root.child("rooms").child(code).child("players").child(user.getUid()).setValue(playerValue(displayName)).addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) { callback.onResult(null, task.getException() == null ? "Could not join room." : task.getException().getMessage()); return; }
                            root.child("rooms").child(code).child("meta").updateChildren(java.util.Collections.<String, Object>singletonMap("status", "setup"))
                                    .addOnCompleteListener(metaTask -> {
                                        if (metaTask.isSuccessful()) callback.onResult(code, null);
                                        else callback.onResult(null, metaTask.getException() == null ? "Room status could not update." : metaTask.getException().getMessage());
                                    });
                        });
                    }
                });
            }
        });
    }

    public Query publicRoomsQuery() {
        return root.child("roomListings").orderByChild("browseKey").equalTo("waiting_public").limitToFirst(30);
    }

    public static List<RoomSummary> parseRooms(DataSnapshot snapshot) {
        List<RoomSummary> rooms = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            String code = child.getKey();
            String host = child.child("hostName").getValue(String.class);
            String status = child.child("status").getValue(String.class);
            Long count = child.child("occupancy").getValue(Long.class);
            rooms.add(new RoomSummary(code, host == null ? "Player" : host, status == null ? "waiting" : status, count == null ? 1 : count.intValue()));
        }
        return rooms;
    }

    public static Map<String, Object> playerValue(String name) {
        Map<String, Object> map = new HashMap<>(); map.put("displayName", name); map.put("joinedAt", ServerValue.TIMESTAMP); return map;
    }

    public ValueEventListener listenForPublicRooms(RoomsCallback callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) { callback.onRooms(parseRooms(snapshot), null); }
            @Override public void onCancelled(DatabaseError error) { callback.onRooms(new ArrayList<>(), error.getMessage()); }
        };
        publicRoomsQuery().addValueEventListener(listener);
        return listener;
    }

    public void removePublicRoomsListener(ValueEventListener listener) {
        if (listener != null) publicRoomsQuery().removeEventListener(listener);
    }

    /** Starts client-only FIFO matching. A short database lock serializes claim attempts. */
    public void startQuickMatch(SearchCallback callback) {
        if (user == null) { callback.onState("error", null, "Sign in before searching."); return; }
        if (searching) { callback.onState("searching", null, null); return; }
        searching = true; searchCallback = callback; working = false;
        queueRef = root.child("matchQueue").child(user.getUid());
        root.child(".info/serverTimeOffset").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                Long offset = snapshot.getValue(Long.class); clockOffset = offset == null ? 0 : offset;
                queueRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot entry) {
                        if ("matched".equals(entry.child("state").getValue(String.class))) {
                            String code = entry.child("roomCode").getValue(String.class);
                            String hostUid = entry.child("hostUid").getValue(String.class);
                            finishSearch(user.getUid().equals(hostUid) ? "host" : "join", code, null);
                        } else enqueueSearch();
                    }
                    @Override public void onCancelled(DatabaseError error) { finishSearch("error", null, error.getMessage()); }
                });
            }
            @Override public void onCancelled(DatabaseError error) { finishSearch("error", null, "Could not sync the matchmaking clock."); }
        });
    }

    private void enqueueSearch() {
        if (!searching) return;
        Map<String, Object> entry = new HashMap<>(); entry.put("uid", user.getUid()); entry.put("displayName", displayName);
        entry.put("createdAt", ServerValue.TIMESTAMP); entry.put("state", "waiting");
        queueRef.setValue(entry).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) { finishSearch("error", null, task.getException() == null ? "Could not enter matchmaking." : task.getException().getMessage()); return; }
            ownQueueListener = new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    if (!searching) return;
                    if (!snapshot.exists()) { enqueueSearch(); return; }
                    String state = snapshot.child("state").getValue(String.class);
                    if ("matched".equals(state)) {
                        String roomCode = snapshot.child("roomCode").getValue(String.class);
                        String hostUid = snapshot.child("hostUid").getValue(String.class);
                        finishSearch(user.getUid().equals(hostUid) ? "host" : "join", roomCode, null);
                    }
                }
                @Override public void onCancelled(DatabaseError error) { finishSearch("error", null, error.getMessage()); }
            };
            queueRef.addValueEventListener(ownQueueListener);
            Query candidates = root.child("matchQueue").orderByChild("createdAt").limitToFirst(40);
            queueListener = new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) { attemptPair(snapshot); }
                @Override public void onCancelled(DatabaseError error) { finishSearch("error", null, error.getMessage()); }
            };
            candidates.addValueEventListener(queueListener);
            say("searching", null, null);
        });
    }

    private void attemptPair(DataSnapshot snapshot) {
        if (!searching || working) return;
        working = true;
        DatabaseReference lock = root.child("matchmaking").child("lock");
        lock.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                Long expiry = data.child("expiresAt").getValue(Long.class);
                String holder = data.child("uid").getValue(String.class);
                long now = System.currentTimeMillis() + clockOffset;
                if (holder != null && !holder.equals(user.getUid()) && expiry != null && expiry > now) return Transaction.abort();
                Map<String, Object> lease = new HashMap<>(); lease.put("uid", user.getUid()); lease.put("expiresAt", now + LOCK_MS);
                data.setValue(lease); return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot current) {
                if (error != null) { working = false; say("error", null, error.getMessage()); return; }
                if (!committed) { working = false; return; }
                Query fresh = root.child("matchQueue").orderByChild("createdAt").limitToFirst(40);
                fresh.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot queue) {
                        DataSnapshot candidate = oldestCandidate(queue);
                        if (candidate == null) { releaseLock(lock); working = false; return; }
                        matchCandidate(lock, candidate);
                    }
                    @Override public void onCancelled(DatabaseError failure) { releaseLock(lock); working = false; say("error", null, failure.getMessage()); }
                });
            }
        }, false);
    }

    private void matchCandidate(DatabaseReference lock, DataSnapshot candidate) {
        String candidateUid = candidate.child("uid").getValue(String.class);
        if (candidateUid == null) { releaseLock(lock); working = false; return; }
        createRoom(false, (code, createError) -> {
                    if (createError != null) { releaseLock(lock); working = false; say("error", null, createError); return; }
                    // Put the invite down while the candidate is still visibly waiting. Rules
                    // authorize this write only while this client owns the short matchmaking lease.
                    root.child("roomInvites").child(candidateUid).child(code).setValue(true).addOnCompleteListener(inviteTask -> {
                    if (!inviteTask.isSuccessful()) {
                        releaseLock(lock); working = false;
                        say("error", null, "Could not deliver the private match invitation.");
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    Map<String, Object> hostStatus = new HashMap<>(); hostStatus.put("uid", user.getUid()); hostStatus.put("displayName", displayName); hostStatus.put("createdAt", ServerValue.TIMESTAMP); hostStatus.put("state", "matched"); hostStatus.put("hostUid", user.getUid()); hostStatus.put("roomCode", code);
                    Map<String, Object> guestStatus = new HashMap<>(); guestStatus.put("uid", candidateUid); guestStatus.put("displayName", candidate.child("displayName").getValue(String.class)); guestStatus.put("createdAt", candidate.child("createdAt").getValue()); guestStatus.put("state", "matched"); guestStatus.put("hostUid", user.getUid()); guestStatus.put("roomCode", code);
                    update.put("matchQueue/" + user.getUid(), hostStatus); update.put("matchQueue/" + candidateUid, guestStatus);
                    root.updateChildren(update).addOnCompleteListener(task -> {
                        releaseLock(lock); working = false;
                        if (!task.isSuccessful()) {
                            root.child("roomInvites").child(candidateUid).child(code).removeValue();
                            say("error", null, task.getException() == null ? "Could not finish matching." : task.getException().getMessage());
                        }
                    });
                    });
                });
    }

    private DataSnapshot oldestCandidate(DataSnapshot queue) {
        long now = System.currentTimeMillis() + clockOffset;
        for (DataSnapshot row : queue.getChildren()) {
            String candidateUid = row.child("uid").getValue(String.class);
            String state = row.child("state").getValue(String.class);
            Long created = row.child("createdAt").getValue(Long.class);
            if ("waiting".equals(state) && created != null && now - created >= QUEUE_TTL_MS) {
                if (candidateUid != null && !candidateUid.equals(user.getUid())) root.child("matchQueue").child(candidateUid).removeValue();
                continue;
            }
            if (candidateUid == null || candidateUid.equals(user.getUid()) || !"waiting".equals(state) || created == null) continue;
            if (now - created < QUEUE_TTL_MS && now >= created - 30000) return row;
        }
        return null;
    }

    private void releaseLock(DatabaseReference lock) {
        lock.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                if (!user.getUid().equals(data.child("uid").getValue(String.class))) return Transaction.abort();
                data.setValue(null); return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) { }
        }, false);
    }

    public void cancelQuickMatch() {
        if (!searching || user == null) return;
        DatabaseReference lock = root.child("matchmaking").child("lock");
        if (queueRef != null) queueRef.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                if (!"waiting".equals(data.child("state").getValue(String.class))) return Transaction.abort();
                data.setValue(null); return Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                releaseLock(lock);
                if ("matched".equals(snapshot.child("state").getValue(String.class))) {
                    String code = snapshot.child("roomCode").getValue(String.class);
                    String hostUid = snapshot.child("hostUid").getValue(String.class);
                    finishSearch(user.getUid().equals(hostUid) ? "host" : "join", code, null);
                } else if (error != null) finishSearch("error", null, error.getMessage());
                else finishSearch("cancelled", null, null);
            }
        }, false);
    }

    public void consumeQuickMatch(String code) {
        if (user == null || code == null) return;
        DatabaseReference own = root.child("matchQueue").child(user.getUid());
        own.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (code.equals(snapshot.child("roomCode").getValue(String.class)) && "matched".equals(snapshot.child("state").getValue(String.class))) own.removeValue();
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
        root.child("roomInvites").child(user.getUid()).child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) { if (snapshot.exists()) snapshot.getRef().removeValue(); }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void finishSearch(String state, String code, String error) {
        searching = false;
        if (queueListener != null) root.child("matchQueue").orderByChild("createdAt").limitToFirst(40).removeEventListener(queueListener);
        if (ownQueueListener != null && queueRef != null) queueRef.removeEventListener(ownQueueListener);
        queueListener = null; ownQueueListener = null;
        say(state, code, error);
    }

    private String newCode() {
        StringBuilder out = new StringBuilder(6);
        for (int i = 0; i < 6; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return out.toString();
    }

    private void say(String state, String code, String error) {
        if (searchCallback != null) searchCallback.onState(state, code, error);
    }
}
