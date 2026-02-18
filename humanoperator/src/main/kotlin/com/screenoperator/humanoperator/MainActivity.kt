package com.screenoperator.humanoperator

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "HumanOperator"
        // TODO: Replace with your deployed signaling server URL
        private const val SIGNALING_SERVER_URL = "wss://screenoperator-signaling.onrender.com"
    }

    private var webRTCClient: WebRTCClient? = null
    private var signalingClient: SignalingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HumanOperatorTheme {
                HumanOperatorScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webRTCClient?.dispose()
        signalingClient?.disconnect()
    }

    @Composable
    fun HumanOperatorTheme(content: @Composable () -> Unit) {
        val darkColorScheme = darkColorScheme(
            primary = Color(0xFF7C4DFF),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            surfaceVariant = Color(0xFF21262D),
            onPrimary = Color.White,
            onBackground = Color(0xFFE6EDF3),
            onSurface = Color(0xFFE6EDF3),
            error = Color(0xFFFF6B6B)
        )
        MaterialTheme(colorScheme = darkColorScheme, content = content)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HumanOperatorScreen() {
        var roomCode by remember { mutableStateOf("") }
        var connectionState by remember { mutableStateOf("Disconnected") }
        var isConnected by remember { mutableStateOf(false) }
        var hasVideoTrack by remember { mutableStateOf(false) }
        var taskText by remember { mutableStateOf("") }
        var dataChannelOpen by remember { mutableStateOf(false) }
        var videoTrack by remember { mutableStateOf<VideoTrack?>(null) }
        var eglContext by remember { mutableStateOf<EglBase.Context?>(null) }
        val context = LocalContext.current

        fun connect() {
            if (roomCode.isBlank()) {
                Toast.makeText(context, "Enter a room code", Toast.LENGTH_SHORT).show()
                return
            }
            connectionState = "Connecting..."

            // Initialize WebRTC
            val rtcClient = WebRTCClient(context, object : WebRTCClient.WebRTCListener {
                override fun onLocalICECandidate(candidate: IceCandidate) {
                    signalingClient?.sendICECandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                }
                override fun onVideoTrackReceived(track: VideoTrack) {
                    Log.d(TAG, "Video track received")
                    videoTrack = track
                    hasVideoTrack = true
                }
                override fun onDataChannelMessage(message: String) {
                    Log.d(TAG, "Task message: ${message.take(100)}")
                    try {
                        val json = com.google.gson.JsonParser.parseString(message).asJsonObject
                        when (json.get("type")?.asString) {
                            "task" -> taskText = json.get("text")?.asString ?: ""
                            "status" -> {
                                val state = json.get("state")?.asString ?: ""
                                Log.d(TAG, "Status update: $state")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message", e)
                    }
                }
                override fun onConnectionStateChanged(state: String) {
                    connectionState = state
                    isConnected = state == "CONNECTED" || state == "COMPLETED"
                }
                override fun onDataChannelOpen() {
                    dataChannelOpen = true
                    Log.d(TAG, "DataChannel is open")
                }
            })
            rtcClient.initialize()
            rtcClient.createPeerConnection()
            webRTCClient = rtcClient
            eglContext = rtcClient.getEglBaseContext()

            // Connect signaling
            val signaling = SignalingClient(SIGNALING_SERVER_URL, object : SignalingClient.SignalingListener {
                override fun onSDPOffer(sdp: String) {
                    rtcClient.setRemoteOffer(sdp)
                    // After answer is created, send it back
                    android.os.Handler(mainLooper).postDelayed({
                        val answer = rtcClient.getLocalDescription()
                        if (answer != null) {
                            signalingClient?.sendAnswer(answer.description)
                        }
                    }, 1000)
                }
                override fun onSDPAnswer(sdp: String) {
                    // Human Operator is the answerer, shouldn't receive answers
                    Log.w(TAG, "Received unexpected SDP answer")
                }
                override fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                    rtcClient.addICECandidate(candidate, sdpMid, sdpMLineIndex)
                }
                override fun onPeerJoined() {
                    connectionState = "Peer joined, waiting for offer..."
                }
                override fun onPeerLeft() {
                    connectionState = "Peer disconnected"
                    isConnected = false
                }
                override fun onError(message: String) {
                    connectionState = "Error: $message"
                }
                override fun onConnected() {
                    connectionState = "Connected to signaling, waiting for peer..."
                }
            })
            signaling.connect(roomCode.uppercase().trim())
            signalingClient = signaling
        }

        fun disconnect() {
            webRTCClient?.dispose()
            signalingClient?.disconnect()
            webRTCClient = null
            signalingClient = null
            isConnected = false
            hasVideoTrack = false
            dataChannelOpen = false
            videoTrack = null
            connectionState = "Disconnected"
            taskText = ""
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Human Operator", fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionState,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isConnected) {
                    // Room code input
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase().take(6) },
                        label = { Text("Room Code") },
                        placeholder = { Text("e.g. ABC123") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { connect() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Disconnect button
                    OutlinedButton(
                        onClick = { disconnect() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Task text
                    if (taskText.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = taskText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Video view with tap overlay
                    if (hasVideoTrack && videoTrack != null && eglContext != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            // WebRTC video renderer
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceViewRenderer(ctx).apply {
                                        init(eglContext, null)
                                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                        setEnableHardwareScaler(true)
                                        videoTrack?.addSink(this)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Tap overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            val normalizedX = offset.x / size.width
                                            val normalizedY = offset.y / size.height
                                            Log.d(TAG, "Tap at normalized: ($normalizedX, $normalizedY)")
                                            webRTCClient?.sendTap(normalizedX, normalizedY)
                                            Toast.makeText(context, "Tap sent", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    } else {
                        // Waiting for video
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Waiting for screen stream...",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    if (dataChannelOpen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { webRTCClient?.sendClaim() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Claim Task")
                            }
                            OutlinedButton(
                                onClick = { webRTCClient?.sendReject() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}
