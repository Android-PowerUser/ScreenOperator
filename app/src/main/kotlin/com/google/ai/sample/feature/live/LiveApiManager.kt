package com.google.ai.sample.feature.live

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class LiveApiManager(
    private val apiKey: String,
    private val modelName: String = "gemini-2.5-flash-live-preview"
) {
    private val TAG = "LiveApiManager"

    private val _messages = MutableStateFlow<String>("")
    val messages: StateFlow<String> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _turnComplete = MutableStateFlow(false)
    val turnComplete: StateFlow<Boolean> = _turnComplete.asStateFlow()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentResponseText = StringBuilder()
    private var isSetupComplete = false

    // Store system message and initial history for setup
    private var systemMessage: String? = null
    private var initialHistory: List<Pair<String, String>>? = null // role to text pairs

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    fun setSystemMessageAndHistory(systemMsg: String?, history: List<Pair<String, String>>?) {
        systemMessage = systemMsg
        initialHistory = history
        // If already connected, we need to reconnect with new setup
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "Reconnecting with new system message and history")
            close()
            connect()
        }
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        isSetupComplete = false

        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .build()

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        Log.d(TAG, "Attempting to connect to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Goog-Api-Key", apiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully")
                _connectionState.value = ConnectionState.CONNECTED
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: ${text.take(500)}")
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Received bytes: ${bytes.size}")
                try {
                    val text = bytes.utf8()
                    scope.launch {
                        handleMessage(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting bytes to string", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
                currentResponseText.clear()
                isSetupComplete = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.ERROR
                _messages.value = "Connection error: ${t.message}"
                currentResponseText.clear()
                isSetupComplete = false

                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (_connectionState.value == ConnectionState.ERROR) {
                        Log.d(TAG, "Retrying connection...")
                        connect()
                    }
                }
            }
        })
    }

    private fun sendSetupMessage() {
        // Verwende den Modellnamen direkt wie er übergeben wurde
        // Die Live API erwartet "models/" als Präfix
        val apiModelName = modelName // Verwende den übergebenen Namen direkt

        // Setup-Nachricht gemäß Dokumentation
        val setupMessage = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$apiModelName") // z.B. "models/gemini-live-2.5-flash-preview"
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 8192)
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                    })
                })

                // Add system instruction if available
                systemMessage?.let { sysMsg ->
                    if (sysMsg.isNotBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", sysMsg)
                                })
                            })
                        })
                    }
                }
            })
        }

        Log.d(TAG, "Sending setup message with model: models/$apiModelName")
        Log.d(TAG, "Full setup message: $setupMessage")
        webSocket?.send(setupMessage.toString())

        // Send initial history after setup if available
        initialHistory?.let { history ->
            if (history.isNotEmpty()) {
                scope.launch {
                    // Wait a bit for setup to complete
                    kotlinx.coroutines.delay(500)
                    sendInitialHistory(history)
                }
            }
        }
    }

    private fun sendInitialHistory(history: List<Pair<String, String>>) {
        Log.d(TAG, "Sending initial history with ${history.size} messages")

        val historyMessage = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    history.forEach { (role, text) ->
                        put(JSONObject().apply {
                            put("role", role)
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", text)
                                })
                            })
                        })
                    }
                })
                put("turnComplete", false) // Don't trigger generation yet
            })
        }

        webSocket?.send(historyMessage.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Log das gesamte Setup-Response
            if (json.has("setupComplete")) {
                Log.d(TAG, "Full setup response: $json")

                // Prüfen Sie, ob das Modell im Response enthalten ist
                if (json.has("model")) {
                    Log.d(TAG, "Server confirmed model: ${json.getString("model")}")
                }
            }

            // Bei Fehlern detaillierter loggen
            if (json.has("error")) {
                Log.e(TAG, "Full error response: $json")
            }

            when {
                // Setup abgeschlossen
                json.has("setupComplete") -> {
                    isSetupComplete = true
                    _messages.value = "Connected and ready"
                }

                // Server-generierte Inhalte
                json.has("serverContent") -> {
                    val serverContent = json.getJSONObject("serverContent")

                    if (serverContent.has("modelTurn")) {
                        val modelTurn = serverContent.getJSONObject("modelTurn")
                        if (modelTurn.has("parts")) {
                            val parts = modelTurn.getJSONArray("parts")

                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                when {
                                    part.has("text") -> {
                                        val textChunk = part.getString("text")
                                        currentResponseText.append(textChunk)
                                        _messages.value = currentResponseText.toString()
                                    }
                                }
                            }
                        }
                    }

                    // Generation abgeschlossen
                    if (serverContent.optBoolean("generationComplete", false)) {
                        Log.d(TAG, "Generation complete")
                    }

                    // Turn abgeschlossen
                    if (serverContent.optBoolean("turnComplete", false)) {
                        Log.d(TAG, "Turn complete")
                        _turnComplete.value = true
                        // Reset for next turn
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            _turnComplete.value = false
                        }
                    }

                    // Unterbrochen
                    if (serverContent.optBoolean("interrupted", false)) {
                        Log.d(TAG, "Generation interrupted")
                    }
                }

                // Nutzungsmetadaten
                json.has("usageMetadata") -> {
                    val usage = json.getJSONObject("usageMetadata")
                    Log.d(TAG, "Usage - prompt tokens: ${usage.optInt("promptTokenCount")}, response tokens: ${usage.optInt("responseTokenCount")}")
                }

                // Fehler
                json.has("error") -> {
                    val error = json.getJSONObject("error")
                    val errorMessage = error.optString("message", "Unknown error")
                    Log.e(TAG, "Server error: $errorMessage")
                    _messages.value = "Error: $errorMessage"
                    _connectionState.value = ConnectionState.ERROR
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }

    fun sendMessage(message: String, imageDataList: List<String>? = null) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Not connected, attempting to connect...")
            connect()
            scope.launch {
                kotlinx.coroutines.delay(3000)
                if (_connectionState.value == ConnectionState.CONNECTED && isSetupComplete) {
                    sendMessage(message, imageDataList)
                } else {
                    Log.e(TAG, "Still not connected after delay")
                    _messages.value = "Error: Could not establish connection"
                }
            }
            return
        }

        if (!isSetupComplete) {
            Log.w(TAG, "Setup not complete, waiting...")
            scope.launch {
                kotlinx.coroutines.delay(1000)
                if (isSetupComplete) {
                    sendMessage(message, imageDataList)
                }
            }
            return
        }

        currentResponseText.clear()

        val clientMessage = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", message)
                            })

                            imageDataList?.forEach { imageData ->
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", imageData)
                                    })
                                })
                            }
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        Log.d(TAG, "Sending message with ${imageDataList?.size ?: 0} images")
        webSocket?.send(clientMessage.toString())
    }

    fun close() {
        Log.d(TAG, "Closing WebSocket connection")
        webSocket?.close(1000, "User closed the connection")
        _connectionState.value = ConnectionState.DISCONNECTED
        currentResponseText.clear()
        isSetupComplete = false
    }
}
