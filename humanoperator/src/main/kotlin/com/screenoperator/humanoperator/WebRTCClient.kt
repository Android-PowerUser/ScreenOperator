package com.screenoperator.humanoperator

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.webrtc.*

/**
 * Manages WebRTC peer connection for the Human Operator side.
 * Receives video stream from ScreenOperator and sends tap coordinates back.
 */
class WebRTCClient(
    private val context: Context,
    private val listener: WebRTCListener
) {
    companion object {
        private const val TAG = "WebRTCClient"
        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    interface WebRTCListener {
        fun onLocalICECandidate(candidate: IceCandidate)
        fun onVideoTrackReceived(track: VideoTrack)
        fun onDataChannelMessage(message: String)
        fun onConnectionStateChanged(state: String)
        fun onDataChannelOpen()
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val eglBase = EglBase.create()
    private val gson = Gson()

    fun initialize() {
        Log.d(TAG, "Initializing WebRTC")
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        Log.d(TAG, "WebRTC initialized")
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(STUN_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate: ${candidate.sdp?.take(50)}")
                listener.onLocalICECandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "Stream added with ${stream.videoTracks.size} video tracks")
                if (stream.videoTracks.isNotEmpty()) {
                    listener.onVideoTrackReceived(stream.videoTracks[0])
                }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    Log.d(TAG, "Video track received via onTrack")
                    listener.onVideoTrackReceived(track)
                }
            }
            override fun onDataChannel(dc: DataChannel) {
                Log.d(TAG, "Data channel received: ${dc.label()}")
                dataChannel = dc
                dc.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previous: Long) {}
                    override fun onStateChange() {
                        Log.d(TAG, "DataChannel state: ${dc.state()}")
                        if (dc.state() == DataChannel.State.OPEN) {
                            listener.onDataChannelOpen()
                        }
                    }
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = ByteArray(buffer.data.remaining())
                        buffer.data.get(data)
                        listener.onDataChannelMessage(String(data))
                    }
                })
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state: $state")
                listener.onConnectionStateChanged(state.name)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        }) ?: throw IllegalStateException("Failed to create PeerConnection")

        Log.d(TAG, "PeerConnection created")
    }

    fun setRemoteOffer(sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote offer set, creating answer")
                createAnswer()
            }
            override fun onSetFailure(error: String) { Log.e(TAG, "Set remote offer failed: $error") }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, desc)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { Log.d(TAG, "Local description set") }
                    override fun onSetFailure(error: String) { Log.e(TAG, "Set local desc failed: $error") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
                listener.onConnectionStateChanged("ANSWER_CREATED")
            }
            override fun onCreateFailure(error: String) { Log.e(TAG, "Create answer failed: $error") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun addICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate))
    }

    fun getLocalDescription(): SessionDescription? = peerConnection?.localDescription

    fun sendTap(x: Float, y: Float) {
        sendDataChannelMessage(gson.toJson(mapOf("type" to "tap", "x" to x, "y" to y)))
    }

    fun sendClaim() = sendDataChannelMessage("{\"type\":\"claim\"}")
    fun sendReject() = sendDataChannelMessage("{\"type\":\"reject\"}")
    
    fun sendText(text: String) {
        val payload = mapOf("type" to "text", "text" to text)
        sendDataChannelMessage(gson.toJson(payload))
    }

    private fun sendDataChannelMessage(message: String) {
        val buffer = DataChannel.Buffer(java.nio.ByteBuffer.wrap(message.toByteArray()), false)
        if (dataChannel?.send(buffer) != true) {
            Log.w(TAG, "Failed to send DataChannel message: ${message.take(50)}")
        }
    }

    fun getEglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun dispose() {
        dataChannel?.close()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        eglBase.release()
    }
}
