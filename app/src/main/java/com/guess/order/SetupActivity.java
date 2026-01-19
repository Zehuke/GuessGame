package com.guess.order;


import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.app.Activity;



import java.util.HashSet;
import java.util.Set;

public class SetupActivity extends Activity {

    EditText etP1, etP2;
    TextView tvP1Error, tvP2Error;
    Button btnP1Set, btnP1Clear, btnP2Set, btnP2Clear, btnStart;
	
	DatabaseReference database;
	
      
    String player1Secret = null;
    String player2Secret = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
		
	    database = FirebaseDatabase.getInstance().getReference();

        etP1 = findViewById(R.id.etPlayer1Secret);
        etP2 = findViewById(R.id.etPlayer2Secret);

        tvP1Error = findViewById(R.id.tvPlayer1Error);
        tvP2Error = findViewById(R.id.tvPlayer2Error);

        btnP1Set = findViewById(R.id.btnP1Set);
        btnP1Clear = findViewById(R.id.btnP1Clear);
        btnP2Set = findViewById(R.id.btnP2Set);
        btnP2Clear = findViewById(R.id.btnP2Clear);
        btnStart = findViewById(R.id.btnStartGame);

        btnStart.setEnabled(false);

        btnP1Set.setOnClickListener(v -> handleSetPlayer1());
        btnP1Clear.setOnClickListener(v -> clearPlayer1());

        btnP2Set.setOnClickListener(v -> handleSetPlayer2());
        btnP2Clear.setOnClickListener(v -> clearPlayer2());

        btnStart.setOnClickListener(v -> startGame());
    }

    private boolean isValidNumber(String number) {
        if (number.length() != 4) return false;
        if (number.contains("0")) return false;

        Set<Character> set = new HashSet<>();
        for (char c : number.toCharArray()) set.add(c);

        return set.size() == 4;
    }

    private void handleSetPlayer1() {
        String input = etP1.getText().toString();
        if (!isValidNumber(input)) {
            tvP1Error.setText("Invalid format!");
        } else {
            player1Secret = input;
            tvP1Error.setText("Number set ✔");
            etP1.setEnabled(false);
			database.setValue(input);
            checkStart();
        }
    }

    private void clearPlayer1() {
        etP1.setText("");
        etP1.setEnabled(true);
        tvP1Error.setText("");
        player1Secret = null;
        btnStart.setEnabled(false);
		btnStart.setAlpha(0.4f);
    }

    private void handleSetPlayer2() {
        String input = etP2.getText().toString();
        if (!isValidNumber(input)) {
            tvP2Error.setText("Invalid format!");
        } else {
            player2Secret = input;
            tvP2Error.setText("Number set ✔");
            etP2.setEnabled(false);
            checkStart();
        }
    }

    private void clearPlayer2() {
        etP2.setText("");
        etP2.setEnabled(true);
        tvP2Error.setText("");
        player2Secret = null;
        btnStart.setEnabled(false);
		btnStart.setAlpha(0.4f);
    }

    private void checkStart() {
        if (player1Secret != null && player2Secret != null) {
            btnStart.setEnabled(true);
			btnStart.setAlpha(1f);
        }
    }

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("P1_SECRET", player1Secret);
        intent.putExtra("P2_SECRET", player2Secret);
        startActivity(intent);
        finish();
    }
}