# ChatApp

Android chat client for a local llama.cpp server, using the OpenAI-compatible `/v1/chat/completions` API with SSE streaming.

## Tech Stack

- **Language**: Java (API 24+)
- **UI**: Material Design Components, RecyclerView
- **Networking**: `HttpURLConnection` (no HTTP client library)
- **JSON**: `org.json` (Android built-in, no external lib)
- **Build**: Gradle 8.2, Android Gradle Plugin, compileSdk 34

## Build

```bash
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # release APK
```

The project requires a valid Android SDK with build tools. The gradle wrapper was regenerated — if it's missing, run `/tmp/gradle-8.2/bin/gradle wrapper --gradle-version 8.2 -p /home/maz/ChatApp`.

## Project Structure

```
app/
├── build.gradle                  # Android plugin, SDK config, dependencies
├── src/main/
│   ├── AndroidManifest.xml       # INTERNET permission, two activities
│   ├── java/com/example/chatapp/
│   │   ├── SettingsActivity.java # Server config: IP, port, model dropdown, debug toggle
│   │   ├── ChatActivity.java     # Chat UI, SSE streaming, conversation history
│   │   └── MessageAdapter.java   # RecyclerView adapter for chat bubbles
│   └── res/
│       ├── layout/
│       │   ├── activity_settings.xml  # Settings screen layout
│       │   ├── activity_chat.xml      # Chat screen layout
│       │   └── item_message.xml       # Individual message bubble
│       ├── values/
│       │   ├── strings.xml      # All string resources
│       │   ├── colors.xml       # UI colors (user vs server message backgrounds)
│       │   └── themes.xml       # App theme
│       └── xml/
│           └── network_security_config.xml  # Allows HTTP (required for local IPs)
```

## Architecture

### SettingsActivity → ChatActivity flow

1. User enters IP + port, taps **Connect**
2. `GET /v1/models` is fetched from the llama-server; model IDs are parsed and populated into a dropdown
3. User selects a model, taps **Start Chat**
4. Settings are saved to `SharedPreferences` ("ChatAppPrefs")
5. ChatActivity is launched; it loads saved settings and connects to `/v1/chat/completions`

### Chat flow (open-ended conversation)

ChatActivity maintains a `List<JSONObject> conversationHistory` that tracks the full message history in OpenAI API format:

```json
{"role": "user", "content": "hello"}
{"role": "assistant", "content": "hi there"}
{"role": "user", "content": "what's up?"}
```

Every send includes the **entire history** as the `messages` array. After the SSE stream completes, the assistant response is appended to history so the next turn includes it.

### Networking

No HTTP client library — uses plain `HttpURLConnection`:
- **POST** to `/v1/chat/completions` with `Content-Type: application/json`, `Accept: text/event-stream`
- **SSE streaming**: reads `data: {...}` lines, extracts `choices[0].delta.content`
- Response 200 triggers SSE read; response >= 400 (debug only) shows error
- Read timeout 60s, connect timeout 10s
- All networking runs on a single-thread executor; UI updates via `Handler(Looper.getMainLooper())`

### Model selection

SettingsActivity has a minimal JSON parser (`parseModelIds()`) that scans for `"id":"..."` patterns in the `/v1/models` response, avoiding an external JSON library dependency.

## Key decisions / gotchas
- **Debug mode**: Only shows HTTP error responses (status >= 400). All 2xx codes are silently ignored.
- **SSE parser**: Uses `BufferedReader.readLine()` and manual string splitting. Fragile but works for llama.cpp's SSE format.

## llama.cpp server

The target server is llama.cpp's built-in HTTP server (`llama-server`). Its API is documented at:
https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md

The app uses two endpoints:
- `GET /v1/models` — list available models
- `POST /v1/chat/completions` — chat with streaming (`stream: true`)
