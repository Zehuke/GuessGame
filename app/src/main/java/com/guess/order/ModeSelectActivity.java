package com.guess.order;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class ModeSelectActivity extends Activity {

    Button btnOffline, btnOnline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        btnOffline = findViewById(R.id.btnOffline);
        btnOnline = findViewById(R.id.btnOnline);

        btnOffline.setOnClickListener(v ->
                startActivity(new Intent(this, SetupActivity.class))
        );

        btnOnline.setOnClickListener(v ->
                startActivity(new Intent(this, OnlineLobbyActivity.class))
        );
    }
}