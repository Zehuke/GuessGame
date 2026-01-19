package com.guess.order;


import com.google.firebase.FirebaseApp;
import android.app.*;
import android.os.*;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.app.Activity;


public class MainActivity extends Activity {

    Button btnPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		FirebaseApp.initializeApp(this);   

        btnPlay = findViewById(R.id.btnPlay);

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ModeSelectActivity.class);
            startActivity(intent);
        });
    }
}