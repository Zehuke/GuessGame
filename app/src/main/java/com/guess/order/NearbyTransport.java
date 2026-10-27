package com.guess.order;

import android.content.Context;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Small nearby transport adapter. Game rules stay in GameRules and payloads contain no secrets. */
public final class NearbyTransport {
    public interface Listener {
        void onStatus(String text);
        void onApproval(String endpointName, String authenticationCode, String endpointId);
        void onConnected(String endpointName);
        void onMessage(JSONObject message);
        void onDisconnected();
        void onError(String message);
    }
    private final ConnectionsClient client;
    private final String serviceId;
    private final Listener listener;
    private String endpointId;
    private boolean started;

    public NearbyTransport(Context context, Listener listener) {
        this.client = Nearby.getConnectionsClient(context.getApplicationContext());
        this.serviceId = context.getPackageName() + ".nearby";
        this.listener = listener;
    }

    public void advertise(String name) {
        stopDiscoveryAndAdvertising(); started = true;
        listener.onStatus("Making this phone discoverable nearby…");
        client.startAdvertising(name, serviceId, lifecycle, new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build())
                .addOnSuccessListener(unused -> listener.onStatus("Waiting for a nearby player to connect…"))
                .addOnFailureListener(error -> listener.onError(explain(error)));
    }

    public void discover() {
        stopDiscoveryAndAdvertising(); started = true;
        listener.onStatus("Looking for a nearby GuessGame player…");
        client.startDiscovery(serviceId, discovery, new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build())
                .addOnFailureListener(error -> listener.onError(explain(error)));
    }

    public void accept(String endpoint) {
        endpointId = endpoint;
        client.acceptConnection(endpoint, payloads).addOnFailureListener(error -> listener.onError(explain(error)));
    }
    public void reject(String endpoint) { client.rejectConnection(endpoint); }

    public void send(JSONObject message) {
        if (endpointId == null) { listener.onError("No nearby player is connected."); return; }
        byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
        client.sendPayload(endpointId, Payload.fromBytes(data)).addOnFailureListener(error -> listener.onError(explain(error)));
    }

    public void stop() {
        started = false; stopDiscoveryAndAdvertising(); client.stopAllEndpoints(); endpointId = null;
    }

    private void stopDiscoveryAndAdvertising() {
        client.stopAdvertising(); client.stopDiscovery();
    }

    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (endpointId != null) return;
            endpointId = id; listener.onStatus("Found " + info.getEndpointName() + ". Requesting a secure connection…");
            client.requestConnection("GuessGame", id, lifecycle)
                    .addOnFailureListener(error -> listener.onError(explain(error)));
        }
        @Override public void onEndpointLost(String id) {
            if (id.equals(endpointId)) { endpointId = null; if (started) listener.onStatus("Player moved out of range. Searching again…"); }
        }
    };

    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String id, ConnectionInfo info) {
            endpointId = id; stopDiscoveryAndAdvertising();
            listener.onApproval(info.getEndpointName(), info.getAuthenticationDigits(), id);
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (result.getStatus().isSuccess()) { endpointId = id; listener.onConnected("Nearby player"); }
            else { endpointId = null; listener.onError("Connection was not approved (" + result.getStatus().getStatusCode() + ")."); }
        }
        @Override public void onDisconnected(String id) {
            if (id.equals(endpointId)) { endpointId = null; listener.onDisconnected(); }
        }
    };

    private final PayloadCallback payloads = new PayloadCallback() {
        @Override public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            try { listener.onMessage(new JSONObject(new String(payload.asBytes(), StandardCharsets.UTF_8))); }
            catch (Exception e) { listener.onError("Received an unreadable game message."); }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) { }
    };

    private String explain(Exception error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Nearby connection failed. Check Bluetooth and Wi-Fi are enabled.";
        return message;
    }
}
