package com.screenoperator.humanoperator

import android.util.Log
import com.google.gson.Gson
import okhttp3.*

/**
 * WebSocket-based signaling client for WebRTC connection setup.
 * Connects to a stateless relay server that forwards SDP/ICE messages
 * between peers in the same room.
 */
class SignalingClient(
    private val serverUrl: String,
    private val listener: SignalingListener
) {
    companion object {
        private const val TAG = "SignalingClient"
    }

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    interface SignalingListener {
        fun onSDPOffer(sdp: String)
        fun onSDPAnswer(sdp: String)
        fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onPeerJoined()
        fun onPeerLeft()
        fun onError(message: String)
        fun onConnected()
    }

    data class SignalMessage(
        val type: String,
        val room: String? = null,
        val sdp: String? = null,
        val candidate: String? = null,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    )

    fun connect(roomCode: String) {
        Log.d(TAG, "Connecting to signaling server: $serverUrl")
        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, joining room: $roomCode")
                val joinMsg = gson.toJson(SignalMessage(type = "join", room = roomCode))
                webSocket.send(joinMsg)
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: ${text.take(200)}")
                try {
                    val msg = gson.fromJson(text, SignalMessage::class.java)
                    when (msg.type) {
                        "offer" -> msg.sdp?.let { listener.onSDPOffer(it) }
                        "answer" -> msg.sdp?.let { listener.onSDPAnswer(it) }
                        "ice" -> {
                            msg.candidate?.let {
                                listener.onICECandidate(it, msg.sdpMid, msg.sdpMLineIndex ?: 0)
                            }
                        }
                        "peer_joined" -> listener.onPeerJoined()
                        "peer_left" -> listener.onPeerLeft()
                        "error" -> listener.onError(msg.sdp ?: "Unknown error")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse signaling message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                listener.onError("Connection failed: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
            }
        })
    }

    fun sendOffer(sdp: String) {
        val msg = gson.toJson(SignalMessage(type = "offer", sdp = sdp))
        webSocket?.send(msg)
    }

    fun sendAnswer(sdp: String) {
        val msg = gson.toJson(SignalMessage(type = "answer", sdp = sdp))
        webSocket?.send(msg)
    }

    fun sendICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val msg = gson.toJson(SignalMessage(
            type = "ice",
            candidate = candidate,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex
        ))
        webSocket?.send(msg)
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
    }
}
