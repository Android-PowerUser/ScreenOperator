package com.google.ai.sample.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.google.gson.Gson
import org.webrtc.*

/**
 * Handles WebRTC PeerConnection for the sender (ScreenOperator).
 * Captures screen video and sends it to the connected Human Operator.
 * Receives touch events via DataChannel.
 */
class WebRTCSender(
    private val context: Context,
    private val listener: WebRTCSenderListener
) {
    companion object {
        private const val TAG = "WebRTCSender"
        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    interface WebRTCSenderListener {
        fun onLocalICECandidate(candidate: IceCandidate)
        fun onConnectionStateChanged(state: String)
        fun onTapReceived(x: Float, y: Float)
        fun onError(message: String)
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var dataChannel: DataChannel? = null
    private val eglBase = EglBase.create()
    private val gson = Gson()

    fun initialize() {
        Log.d(TAG, "Initializing WebRTCSender")
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun startScreenCapture(permissionResultData: Intent) {
        Log.d(TAG, "Starting screen capture")
        videoCapturer = ScreenCapturerAndroid(permissionResultData, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.e(TAG, "MediaProjection stopped")
                listener.onError("Screen capture stopped")
            }
        })

        val factory = peerConnectionFactory ?: return
        videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        
        // Initialize capturer
        (videoCapturer as ScreenCapturerAndroid).initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
            context,
            videoSource!!.capturerObserver
        )
        (videoCapturer as ScreenCapturerAndroid).startCapture(720, 1280, 30) // Adjust resolution/fps as needed

        videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
        videoTrack?.setEnabled(true)
    }

    fun createPeerConnection() {
        Log.d(TAG, "Creating PeerConnection")
        val rtcConfig = PeerConnection.RTCConfiguration(STUN_SERVERS)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                listener.onLocalICECandidate(candidate)
            }
            override fun onDataChannel(dc: DataChannel) {
                // Sender typically creates the channel, but handling incoming if peer creates it
                setupDataChannel(dc)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE State: $state")
                listener.onConnectionStateChanged(state.name)
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                 Log.d(TAG, "PeerConnection State: $newState")
            }
            // Unused
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onTrack(transceiver: RtpTransceiver) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        })

        // Add video track
        if (videoTrack != null) {
            peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
        }

        // Create DataChannel (Sender creates it usually)
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("task_channel", dcInit)
        setupDataChannel(dataChannel)
    }

    private fun setupDataChannel(dc: DataChannel?) {
        dc?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previous: Long) {}
            override fun onStateChange() {
                 Log.d(TAG, "DataChannel State: ${dc.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    val message = String(data)
                    Log.d(TAG, "Received DataChannel message: $message")
                    
                    val json = com.google.gson.JsonParser.parseString(message).asJsonObject
                    if (json.has("type") && json.get("type").asString == "tap") {
                        val x = json.get("x").asFloat
                        val y = json.get("y").asFloat
                        listener.onTapReceived(x, y)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing DataChannel message", e)
                }
            }
        })
    }

    fun createOffer(callback: (String) -> Unit) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d(TAG, "Offer created")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set")
                        callback(sdp.description)
                    }
                    override fun onSetFailure(s: String) { Log.e(TAG, "SetLocal failure: $s") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(s: String) { Log.e(TAG, "CreateOffer failure: $s") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun setRemoteAnswer(sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { Log.d(TAG, "Remote answer set") }
            override fun onSetFailure(s: String) { Log.e(TAG, "SetRemote failure: $s") }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, desc)
    }

    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate))
    }

    fun stop() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            peerConnection?.close()
            peerConnectionFactory?.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebRTCSender", e)
        }
    }
}
