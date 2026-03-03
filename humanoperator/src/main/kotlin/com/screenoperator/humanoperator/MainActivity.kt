package com.screenoperator.humanoperator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "HumanOperator"
        private const val NOTIFICATION_CHANNEL_ID = "human_operator_tasks"
    }

    private var webRTCClient: WebRTCClient? = null
    private var signalingClient: SignalingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            HumanOperatorTheme {
                HumanOperatorScreen()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Incoming Tasks",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new tasks from Screen Operator"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showTaskNotification(taskId: String, text: String) {
        try {
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Task Available")
                .setContentText(if (text.isNotBlank()) text.take(100) else "A new task is waiting")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(taskId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification permission", e)
        }
    }

    private fun cancelTaskNotification(taskId: String) {
        NotificationManagerCompat.from(this).cancel(taskId.hashCode())
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

    data class TaskInfo(val taskId: String, val text: String)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HumanOperatorScreen() {
        var connectionState by remember { mutableStateOf("Disconnected") }
        var isConnected by remember { mutableStateOf(false) }
        var isPaired by remember { mutableStateOf(false) }
        var hasVideoTrack by remember { mutableStateOf(false) }
        var dataChannelOpen by remember { mutableStateOf(false) }
        var videoTrack by remember { mutableStateOf<VideoTrack?>(null) }
        var eglContext by remember { mutableStateOf<EglBase.Context?>(null) }
        val availableTasks = remember { mutableStateListOf<TaskInfo>() }
        var claimedTaskId by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        fun connectToServer() {
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
                    Log.d(TAG, "Message: ${message.take(100)}")
                }
                override fun onConnectionStateChanged(state: String) {
                    Log.d(TAG, "WebRTC state: $state")
                    if (state == "CONNECTED" || state == "COMPLETED") {
                        isPaired = true
                        connectionState = "Paired - viewing screen"
                    } else if (state == "DISCONNECTED" || state == "FAILED") {
                        isPaired = false
                        hasVideoTrack = false
                        connectionState = "Peer disconnected"
                    }
                }
                override fun onDataChannelOpen() {
                    dataChannelOpen = true
                }
            })
            rtcClient.initialize()
            rtcClient.createPeerConnection()
            webRTCClient = rtcClient
            eglContext = rtcClient.getEglBaseContext()

            // Connect signaling
            val signaling = SignalingClient(object : SignalingClient.SignalingListener {
                override fun onNewTask(taskId: String, text: String) {
                    Log.d(TAG, "New task: $taskId")
                    availableTasks.add(TaskInfo(taskId, text))
                    showTaskNotification(taskId, text)
                    if (!isConnected) {
                        isConnected = true
                        connectionState = "Waiting for tasks..."
                    }
                }
                override fun onTaskRemoved(taskId: String) {
                    Log.d(TAG, "Task removed: $taskId")
                    availableTasks.removeAll { it.taskId == taskId }
                    cancelTaskNotification(taskId)
                }
                override fun onClaimed(taskId: String) {
                    Log.d(TAG, "Successfully claimed: $taskId")
                    claimedTaskId = taskId
                    connectionState = "Task claimed, connecting..."
                    // Clear all other tasks
                    availableTasks.clear()
                    NotificationManagerCompat.from(context).cancelAll()
                }
                override fun onClaimFailed(reason: String) {
                    Log.w(TAG, "Claim failed: $reason")
                    Toast.makeText(context, "Someone was faster: $reason", Toast.LENGTH_SHORT).show()
                }
                override fun onSDPOffer(sdp: String) {
                    rtcClient.setRemoteOffer(sdp)
                    // Send answer after a short delay
                    android.os.Handler(mainLooper).postDelayed({
                        val answer = rtcClient.getLocalDescription()
                        if (answer != null) {
                            signalingClient?.sendAnswer(answer.description)
                        }
                    }, 1000)
                }
                override fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                    rtcClient.addICECandidate(candidate, sdpMid, sdpMLineIndex)
                }
                override fun onPeerDisconnected() {
                    isPaired = false
                    hasVideoTrack = false
                    dataChannelOpen = false
                    claimedTaskId = null
                    connectionState = "Peer disconnected - waiting for tasks..."
                    // Resume listening for tasks
                    signalingClient?.startListeningForTasks()
                }
                override fun onError(message: String) {
                    connectionState = "Error: $message"
                }
            })
            signaling.startListeningForTasks()
            // Set initial state
            isConnected = true 
            connectionState = "Waiting for tasks..."
            signalingClient = signaling
        }

        // Auto-connect on launch
        LaunchedEffect(Unit) {
            connectToServer()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Human Operator", fontWeight = FontWeight.Bold) },
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
                // Connection status bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isPaired) Color(0xFF4CAF50) else if (isConnected) Color(0xFFFFA726) else Color(0xFFFF6B6B),
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

                if (isPaired && hasVideoTrack && videoTrack != null && eglContext != null) {
                    // === PAIRED VIEW: Video + Tap Overlay ===
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
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
                                        Log.d(TAG, "Tap: ($normalizedX, $normalizedY)")
                                        webRTCClient?.sendTap(normalizedX, normalizedY)
                                    }
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tap hint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Tap on the screen to interact",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                } else if (isPaired) {
                    // Paired but waiting for video
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Waiting for screen stream...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                } else {
                    // === TASK LIST VIEW ===
                    if (availableTasks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isConnected) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Waiting for tasks...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Tasks appear here when someone needs help",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        "Connecting to server...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "${availableTasks.size} task${if (availableTasks.size != 1) "s" else ""} available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableTasks, key = { it.taskId }) { task ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        if (task.text.isNotBlank()) {
                                            Text(
                                                text = task.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        Button(
                                            onClick = { signalingClient?.claimTask(task.taskId) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Claim this task", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
