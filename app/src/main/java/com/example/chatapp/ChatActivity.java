package com.example.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private EditText etMessage;
    private MaterialButton btnSend;
    private TextView tvBack;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;
    private String serverUrl;
    private String model;
    private boolean debugMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        prefs = getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        String ip = prefs.getString("ip", "");
        model = prefs.getString("model", "llama-2-7b");
        String port = prefs.getString("port", "");
        debugMode = prefs.getBoolean("debug", false);
        // Use chat completions endpoint for chat models
        serverUrl = "http://" + ip + ":" + port + "/v1/chat/completions";

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvBack = findViewById(R.id.tvBack);
        rvMessages = findViewById(R.id.rvMessages);

        adapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        tvBack.setOnClickListener(v -> finish());
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) return;

        // Add user message
        adapter.addMessage(new MessageAdapter.Message(message, true));
        etMessage.setText("");

        // Add empty server message placeholder
        adapter.addServerMessage("");

        // Send request to server
        executor.execute(() -> sendToServer(message));
    }

    private void sendToServer(String message) {
        try {
            URL url = new URL(serverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setReadTimeout(30000);
            conn.setConnectTimeout(10000);

            // Chat format payload for /v1/chat/completions
            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"stream\": true}",
                model, message.replace("\"", "\\\"")
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes());
            }

            final int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                readSSE(conn);
            } else if (code >= 400) { // 201 etc cn be ignored for now
                if (debugMode) {
                    String errorMsg = "Error " + code;
                    try {
                        InputStream errorStream = conn.getErrorStream();
                        if (errorStream != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream));
                            StringBuilder errResponse = new StringBuilder();
                            String errLine;
                            while ((errLine = errReader.readLine()) != null) {
                                errResponse.append(errLine);
                            }
                            errorMsg = "Error " + code + ": " + errResponse.toString();
                        }
                    } catch (IOException e) {
                        errorMsg = "Error " + code + ": " + e.getMessage();
                    }

                    final String finalErrorMsg = errorMsg;
                    mainHandler.post(() ->
                            adapter.addMessage(new MessageAdapter.Message(finalErrorMsg, false))
                    );
                }
            }
        } catch (IOException e) {
            final String errorMsg = "Error: " + e.getMessage();
            mainHandler.post(() -> 
                adapter.addMessage(new MessageAdapter.Message(errorMsg, false))
            );
        }
    }

    private void readSSE(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.equals("[DONE]")) {
                    break;
                }
                
                // Parse JSON to extract content from delta
                // Expected format: {"choices": [{"delta": {"content": "..."}}]}
                try {
                    int contentIndex = data.indexOf("\"content\":");
                    if (contentIndex != -1) {
                        int colonIndex = data.indexOf(":", contentIndex);
                        int quoteStart = data.indexOf("\"", colonIndex + 1);
                        if (quoteStart != -1) {
                            int quoteEnd = data.indexOf("\"", quoteStart + 1);
                            if (quoteEnd != -1) {
                                String content = data.substring(quoteStart + 1, quoteEnd);
                                response.append(content);
                                final String finalResponse = response.toString();
                                mainHandler.post(() -> adapter.updateLastServerMessage(finalResponse));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing errors for now
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
