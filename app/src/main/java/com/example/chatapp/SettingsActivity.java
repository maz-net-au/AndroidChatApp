package com.example.chatapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private EditText etIp, etPort;
    private AutoCompleteTextView atvModel;
    private MaterialCheckBox cbAllowThinking, cbDebug;
    private MaterialButton btnConnect, btnStartChat;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        atvModel = findViewById(R.id.atvModel);
        cbAllowThinking = findViewById(R.id.cbAllowThinking);
        cbDebug = findViewById(R.id.cbDebug);
        btnConnect = findViewById(R.id.btnConnect);
        btnStartChat = findViewById(R.id.btnStartChat);

        // Load saved settings
        String savedIp = prefs.getString("ip", "");
        String savedPort = prefs.getString("port", "");
        boolean savedAllowThinking = prefs.getBoolean("allowThinking", false);
        boolean savedDebug = prefs.getBoolean("debug", false);
        etIp.setText(savedIp);
        etPort.setText(savedPort);
        cbAllowThinking.setChecked(savedAllowThinking);
        cbDebug.setChecked(savedDebug);

        // "Connect" button — fetch /models from the llama-server
        btnConnect.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String port = etPort.getText().toString().trim();

            if (ip.isEmpty() || port.isEmpty()) {
                Toast.makeText(this, "Enter IP and port first", Toast.LENGTH_SHORT).show();
                return;
            }

            String baseUrl = "http://" + ip + ":" + port;
            fetchModels(baseUrl);
        });

        // "Start Chat" button — save settings and go to chat
        btnStartChat.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String model = atvModel.getText().toString().trim();
            String port = etPort.getText().toString().trim();

            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (model.isEmpty() || model.equals("Select model")) {
                Toast.makeText(this, "Please select a model", Toast.LENGTH_SHORT).show();
                return;
            }
            if (port.isEmpty()) {
                Toast.makeText(this, "Please enter a port", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putString("ip", ip)
                    .putString("model", model)
                    .putString("port", port)
                    .putBoolean("allowThinking", cbAllowThinking.isChecked())
                    .putBoolean("debug", cbDebug.isChecked())
                    .apply();

            startActivity(new Intent(SettingsActivity.this, ChatActivity.class));
        });
    }

    private void fetchModels(String baseUrl) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.connecting));
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        executor.execute(() -> {
            HttpURLConnection conn = null;
            List<String> models = null;
            try {
                URL url = new URL(baseUrl + "/v1/models");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                final int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    reader.close();
                    models = parseModelIds(json.toString());
                } else {
                    final int respCode = code;
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        Toast.makeText(SettingsActivity.this,
                                "Server returned " + respCode, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
            } catch (IOException e) {
                final String err = e.getMessage();
                mainHandler.post(() -> {
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            (err == null ? "" : err) + " — check IP/port", Toast.LENGTH_LONG).show();
                });
                return;
            } finally {
                if (conn != null) conn.disconnect();
            }

            final List<String> finalModels = models;
            mainHandler.post(() -> {
                dialog.dismiss();
                if (finalModels == null || finalModels.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.err_no_models), Toast.LENGTH_LONG).show();
                    return;
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        SettingsActivity.this,
                        android.R.layout.simple_dropdown_item_1line,
                        finalModels
                );
                atvModel.setAdapter(adapter);
                atvModel.setHint(null);
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.models_loaded) + " (" + finalModels.size() + ")",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * Minimal JSON parser that extracts "id" values from a llama.cpp /v1/models response
     * without pulling in a heavy library.
     */
    private static List<String> parseModelIds(String json) {
        List<String> ids = new ArrayList<>();
        if (json == null) return ids;

        // Find all "id" keys and extract their string values
        // Pattern: "id":"some-model-name"
        int i = 0;
        while (i < json.length()) {
            int idIdx = json.indexOf("\"id\"", i);
            if (idIdx == -1) break;
            // Move past "id"
            int colonIdx = json.indexOf(":", idIdx + 4);
            if (colonIdx == -1) break;
            int quoteStart = json.indexOf("\"", colonIdx + 1);
            if (quoteStart == -1) break;
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) break;
            ids.add(json.substring(quoteStart + 1, quoteEnd));
            i = quoteEnd + 1;
        }
        return ids;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
