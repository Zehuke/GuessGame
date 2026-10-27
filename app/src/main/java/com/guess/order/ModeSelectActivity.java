package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import com.google.firebase.auth.FirebaseAuth;

public class ModeSelectActivity extends Activity {

    Button btnOffline, btnOnline, btnAccount, btnNearby;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        btnOffline = findViewById(R.id.btnOffline);
        btnOnline = findViewById(R.id.btnOnline);
        btnAccount = findViewById(R.id.btnAccount);
        btnNearby = findViewById(R.id.btnNearby);

        btnOffline.setOnClickListener(v ->
                startActivity(new Intent(this, SetupActivity.class))
        );

        btnOnline.setOnClickListener(v -> startActivity(new Intent(this,
                FirebaseAuth.getInstance().getCurrentUser() == null ? AccountActivity.class : OnlineLobbyActivity.class)));
        btnAccount.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        btnNearby.setOnClickListener(v -> startActivity(new Intent(this, NearbyGameActivity.class)));
    }
}
