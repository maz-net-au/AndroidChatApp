package com.example.chatapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import android.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private EditText etMessage;
    private MaterialButton btnSend, btnNew;
    private TextView tvBack;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;
    private String serverUrl;
    private String model;
    private boolean debugMode;
    private boolean allowThinking;
    private volatile HttpURLConnection activeConnection;

    // Conversation history for the OpenAI-compatible API
    private final List<JSONObject> conversationHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        prefs = getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        String ip = prefs.getString("ip", "192.168.1.50");
        model = prefs.getString("model", "Qwen3.6-VL-35B-A3B-NR");
        String port = prefs.getString("port", "7800");
        debugMode = prefs.getBoolean("debug", false);
        allowThinking = prefs.getBoolean("allowThinking", false);
        serverUrl = "http://" + ip + ":" + port + "/v1/chat/completions";

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnNew = findViewById(R.id.btnNew);
        tvBack = findViewById(R.id.tvBack);
        rvMessages = findViewById(R.id.rvMessages);

        adapter = new MessageAdapter();
        if (debugMode) {
            adapter.setShowHistoryListener(this::showConversationHistory);
        }
        adapter.setRerollListener(this::handleReroll);
        adapter.setContinueListener(this::handleContinue);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> {
            if (btnSend.getText().toString().equals(getString(R.string.stop))) {
                stopGeneration();
            } else {
                sendMessage();
            }
        });
        btnNew.setOnClickListener(v -> clearConversation());
        tvBack.setOnClickListener(v -> finish());
    }

    private void handleReroll(int messagePosition) {
        // Remove the last assistant message from history and resend
        if (conversationHistory.isEmpty()) return;
        conversationHistory.remove(conversationHistory.size() - 1);

        // Rebuild response for the last user message
        if (conversationHistory.isEmpty()) return;
        JSONObject lastUserMsg = conversationHistory.get(conversationHistory.size() - 1);
        String lastUserContent = lastUserMsg.optString("content", "");

       mainHandler.post(() -> adapter.updateLastServerMessage(""));

        setStreaming(true);
        executor.execute(() -> sendToServer(lastUserContent, "", false));
    }

    private void handleContinue(int position) {
        if (conversationHistory.isEmpty()) return;

        // Must have an assistant message to continue from
        if (conversationHistory.get(conversationHistory.size() - 1).optString("role").equals("user")) {
            return;
        }

        // Get the base content of the last assistant message (tokens will be appended to this)
        String baseContent = conversationHistory.get(conversationHistory.size() - 1).optString("content", "");

        setStreaming(true);
        executor.execute(() -> sendToServer("", baseContent, true));
    }

    private void setStreaming(boolean streaming) {
        if (streaming) {
            btnSend.setText(R.string.stop);
        } else {
            btnSend.setText(R.string.send);
        }
    }

    private void stopGeneration() {
        setStreaming(false);
        HttpURLConnection conn = activeConnection;
        activeConnection = null;
        if (conn != null) {
            conn.disconnect();
            activeConnection = null;
        }
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) return;

        // Add user message to UI
        adapter.addMessage(new MessageAdapter.Message(message, true));
        // Add to conversation history
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", message);
            conversationHistory.add(userMsg);
        } catch (JSONException e) {
            // Should never happen
        }

      etMessage.setText("");

        // Add placeholder for assistant response
        adapter.addServerMessage("");

        setStreaming(true);
        executor.execute(() -> sendToServer(message, "", false));
    }

    private void sendToServer(String lastUserMessage, String baseContinueContent, boolean continueRequest) {
              HttpURLConnection conn = null;
        StringBuilder assistantContent = new StringBuilder(baseContinueContent);
        try {
            URL url = new URL(serverUrl);
            conn = (HttpURLConnection) url.openConnection();
            activeConnection = conn;
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setReadTimeout(60000);
            conn.setConnectTimeout(10000);

            // Build messages array from conversation history
            JSONArray messagesArray = new JSONArray();
            for (JSONObject msg : conversationHistory) {
                messagesArray.put(msg);
            }

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", model);
                requestBody.put("messages", messagesArray);
                requestBody.put("stream", true);
                if (continueRequest) {
                    requestBody.put("continue_", true);
                }
            } catch (JSONException e) {
                throw new IOException("Failed to build request", e);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.toString().getBytes("UTF-8"));
            }

            final int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                readSSE(conn, (delta) -> {
                    assistantContent.append(delta);
                    final String currentResponse = assistantContent.toString();
                    if (!continueRequest) {
                        mainHandler.post(() -> adapter.updateLastServerMessage(currentResponse));
                    } else {
                        mainHandler.post(() -> adapter.appendLastServerMessage(delta));
                    }
                });
                saveAssistantResponse(assistantContent.toString(), continueRequest, requestBody);
            } else if (code >= 400) {
                if (!debugMode) return;
                final int finalCode = code;
                StringBuilder errResponse = new StringBuilder();
                try {
                    InputStream errorStream = conn.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream));
                        String errLine;
                        while ((errLine = errReader.readLine()) != null) {
                            errResponse.append(errLine);
                        }
                    }
                } catch (IOException e) {
                    // ignore
                }

                final String finalResponse = errResponse.toString();
                mainHandler.post(() ->
                        adapter.addMessage(new MessageAdapter.Message("Error " + finalCode, false))
                );
                JSONObject errorBody = new JSONObject();
                errorBody.put("code", finalCode);
                errorBody.put("content", errResponse.toString());
                mainHandler.post(() -> showDebugDialog(requestBody, errorBody));
            }
        } catch (IOException e) {
            if (activeConnection != null) {
                final String errorMsg = e.getMessage();
                mainHandler.post(() ->
                    Toast.makeText(ChatActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show()
                );
            } else {
                // User-initiated stop — save the partial response
                saveAssistantResponse(assistantContent.toString(), continueRequest, null);
            }
        } catch (JSONException e) {
            mainHandler.post(() ->
                adapter.addMessage(new MessageAdapter.Message("Couldn't make json error", false))
            );
        } finally {
            activeConnection = null;
            if (conn != null) conn.disconnect();
            mainHandler.post(() -> setStreaming(false));
        }
    }

    private static String stripThoughtTags(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf("<think>", i);
            if (open == -1) {
                sb.append(text.substring(i));
                break;
            }
            sb.append(text.substring(i, open));
            int close = text.indexOf("</think>", open + 2);
            if (close == -1) {
                break;
            }
            i = close + 10;
        }
        return sb.toString();
    }

    private void saveAssistantResponse(String content, boolean continueRequest, JSONObject requestBody) {
        String finalContent = content;
        if (!allowThinking) {
            finalContent = stripThoughtTags(finalContent);
        }
        try {
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", finalContent);
            if (continueRequest) {
                conversationHistory.set(conversationHistory.size() - 1, assistantMsg);
            } else {
                conversationHistory.add(assistantMsg);
            }
        } catch (JSONException e) {
            // Should never happen
        }
        if (debugMode && requestBody != null) {
            final JSONObject finalRequestBody = requestBody;
            final JSONObject finalResponse = conversationHistory.get(conversationHistory.size() - 1);
            mainHandler.post(() -> showDebugDialog(finalRequestBody, finalResponse));
        }
    }

    private void readSSE(HttpURLConnection conn, OnDeltaReceived onDeltaReceived) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.equals("[DONE]")) {
                    break;
                }

                // Parse SSE delta: {"choices":[{"delta":{"content":"..."}}]}
                try {
                    JSONObject obj = new JSONObject(data);
                    JSONArray choices = obj.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.getJSONObject("delta");
                        if (delta.has("content") && !delta.isNull("content")) {
                            String content = delta.getString("content");
                            if (content != null && !content.isEmpty()) {
                                onDeltaReceived.onDelta(content);
                            }
                        }
                    }
                } catch (JSONException e) {
                    // Ignore parse errors
                }
            }
        }
    }

   private interface OnDeltaReceived {
        void onDelta(String delta);
    }

    private void clearConversation() {
        conversationHistory.clear();
        adapter.clear();
        etMessage.requestFocus();
    }

    private void showConversationHistory() {
        try {
            JSONArray historyJson = new JSONArray();
            for (JSONObject msg : conversationHistory) {
                historyJson.put(msg);
            }
            String pretty = historyJson.toString(2);
            new AlertDialog.Builder(this)
                    .setTitle("Conversation History")
                    .setMessage(pretty)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (JSONException e) {
            // Should never happen
        }
    }

    private void showDebugDialog(JSONObject request, JSONObject response) {
        try {
            String body = "REQUEST:\n" + request.toString(2) + "\n\nRESPONSE:\n" + response.toString(2);
            new AlertDialog.Builder(this)
                    .setTitle("Debug: Request/Response")
                    .setMessage(body)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (JSONException e) {
            // Should never happen
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
