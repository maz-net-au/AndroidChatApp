package com.example.chatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.checkbox.MaterialCheckBox;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private EditText etIp, etModel, etPort;
    private MaterialCheckBox cbDebug;
    private MaterialButton btnStartChat;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE);

        etIp = findViewById(R.id.etIp);
        etModel = findViewById(R.id.etModel);
        etPort = findViewById(R.id.etPort);
        cbDebug = findViewById(R.id.cbDebug);
        btnStartChat = findViewById(R.id.btnStartChat);

        // Load saved settings
        String savedIp = prefs.getString("ip", "");
        String savedModel = prefs.getString("model", "llama-2-7b");
        String savedPort = prefs.getString("port", "");
        boolean savedDebug = prefs.getBoolean("debug", false);
        etIp.setText(savedIp);
        etModel.setText(savedModel);
        etPort.setText(savedPort);
        cbDebug.setChecked(savedDebug);

        btnStartChat.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            String port = etPort.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (model.isEmpty()) {
                Toast.makeText(this, "Please enter a model name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (port.isEmpty()) {
                Toast.makeText(this, "Please enter a port", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save settings
            prefs.edit()
                .putString("ip", ip)
                .putString("model", model)
                .putString("port", port)
                .putBoolean("debug", cbDebug.isChecked())
                .apply();

            // Navigate to chat
            Intent intent = new Intent(SettingsActivity.this, ChatActivity.class);
            startActivity(intent);
        });
    }
}
