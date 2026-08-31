package com.example.parentalchild

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.Executors

class WebRTCManager(
    private val context: Context,
    private val deviceId: String,
    private val apiBase: String,
    private val secret: String,
) {
    private val http    = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: ScreenCapturerAndroid? = null

    @Volatile var isStreaming = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
    )

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    EglBase.create().eglBaseContext, true, true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(EglBase.create().eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startStream(mediaProjectionPermission: android.content.Intent) {
        if (isStreaming) return
        isStreaming = true
        executor.execute {
            setupPeerConnection(mediaProjectionPermission)
        }
    }

    fun stopStream() {
        isStreaming = false
        peerConnection?.close()
        peerConnection = null
        capturer?.stopCapture()
        capturer?.dispose()
        capturer = null
        videoTrack?.dispose()
        videoSource?.dispose()
        // Signallarni tozalash
        deleteSignals()
    }

    private fun setupPeerConnection(mediaProjectionPermission: android.content.Intent) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    // ICE candidate ni serverga yuboramiz
                    sendSignal(JSONObject().apply {
                        put("type", "ice-device")
                        put("candidate", candidate.sdp)
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                    })
                }
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                        state == PeerConnection.IceConnectionState.FAILED) {
                        isStreaming = false
                    }
                }
                override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(b: Boolean) {}
                override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(a: Array<out IceCandidate>?) {}
                override fun onAddStream(s: MediaStream?) {}
                override fun onRemoveStream(s: MediaStream?) {}
                override fun onDataChannel(d: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
            }
        )

        // Video source — ekran capture
        val eglBase = EglBase.create()
        videoSource = factory?.createVideoSource(false)
        videoTrack  = factory?.createVideoTrack("screen", videoSource)

        capturer = ScreenCapturerAndroid(
            mediaProjectionPermission,
            object : MediaProjection.Callback() {
                override fun onStop() { stopStream() }
            }
        )
        capturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
            context,
            videoSource?.capturerObserver
        )
        capturer?.startCapture(1280, 720, 15) // 1280x720, 15fps

        peerConnection?.addTrack(videoTrack, listOf("stream"))

        // Offer yaratamiz
        peerConnection?.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // Offer ni serverga yuboramiz
                            sendSignal(JSONObject().apply {
                                put("type", "offer")
                                put("sdp", sdp.description)
                            })
                            // Answer ni kutamiz
                            waitForAnswer()
                        }
                        override fun onCreateSuccess(s: SessionDescription?) {}
                        override fun onSetFailure(s: String?) {}
                        override fun onCreateFailure(s: String?) {}
                    }, sdp)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(s: String?) { isStreaming = false }
                override fun onSetFailure(s: String?) {}
            },
            MediaConstraints()
        )
    }

    private fun waitForAnswer() {
        val th = HandlerThread("answer-wait").also { it.start() }
        val handler = Handler(th.looper)
        handler.post(object : Runnable {
            override fun run() {
                if (!isStreaming) { th.quitSafely(); return }
                val answer = getSignal("answer")
                if (answer != null) {
                    val sdp = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        answer.getString("sdp")
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() { pollIceCandidates(handler, th) }
                        override fun onCreateSuccess(s: SessionDescription?) {}
                        override fun onSetFailure(s: String?) {}
                        override fun onCreateFailure(s: String?) {}
                    }, sdp)
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun pollIceCandidates(handler: Handler, th: HandlerThread) {
        handler.post(object : Runnable {
            override fun run() {
                if (!isStreaming) { th.quitSafely(); return }
                val ice = getSignal("ice-viewer")
                if (ice != null) {
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            ice.getString("sdpMid"),
                            ice.getInt("sdpMLineIndex"),
                            ice.getString("candidate")
                        )
                    )
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun sendSignal(data: JSONObject) {
        try {
            val body = data.toString().toRequestBody("application/json".toMediaType())
            val req  = Request.Builder()
                .url("$apiBase/api/signal/$deviceId")
                .header("x-device-secret", secret)
                .post(body)
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun getSignal(type: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("$apiBase/api/signal/$deviceId?type=$type")
                .header("x-device-secret", secret)
                .get()
                .build()
            val res  = http.newCall(req).execute()
            val body = JSONObject(res.body?.string() ?: return null)
            if (body.isNull("signal")) null
            else body.getJSONObject("signal")
        } catch (_: Exception) { null }
    }

    private fun deleteSignals() {
        try {
            val req = Request.Builder()
                .url("$apiBase/api/signal/$deviceId")
                .header("x-device-secret", secret)
                .delete()
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {}
    }
}
