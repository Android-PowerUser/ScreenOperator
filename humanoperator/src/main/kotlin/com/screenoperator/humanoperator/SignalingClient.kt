package com.screenoperator.humanoperator

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database signaling client for the task broker.
 * Operators listen for open tasks, claim them, and exchange WebRTC signals.
 */
class SignalingClient(
    private val listener: SignalingListener
) {
    companion object {
        private const val TAG = "SignalingClient"
    }

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val tasksRef: DatabaseReference = database.getReference("tasks")
    
    // Listener references for cleanup
    private var tasksListener: ChildEventListener? = null
    private var offerListener: ValueEventListener? = null
    private var iceListener: ChildEventListener? = null
    private var currentTaskId: String? = null

    interface SignalingListener {
        fun onNewTask(taskId: String, text: String)
        fun onTaskRemoved(taskId: String)
        fun onClaimed(taskId: String)
        fun onClaimFailed(reason: String)
        fun onSDPOffer(sdp: String)
        fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onPeerDisconnected()
        fun onError(message: String)
    }

    fun startListeningForTasks() {
        Log.d(TAG, "Starting to listen for open tasks...")
        
        if (tasksListener != null) return

        tasksListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "open") {
                    val taskId = snapshot.key ?: return
                    val text = snapshot.child("text").getValue(String::class.java) ?: ""
                    Log.d(TAG, "New open task found: $taskId")
                    listener.onNewTask(taskId, text)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                val taskId = snapshot.key ?: return
                
                // If task is no longer open (claimed by someone else or cancelled), remove it from list
                if (status != "open") {
                    listener.onTaskRemoved(taskId)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val taskId = snapshot.key ?: return
                listener.onTaskRemoved(taskId)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Tasks listener cancelled: ${error.message}")
                listener.onError("Failed to listen for tasks: ${error.message}")
            }
        }
        
        tasksRef.orderByChild("status").equalTo("open").addChildEventListener(tasksListener!!)
    }

    fun claimTask(taskId: String) {
        Log.d(TAG, "Attempting to claim task: $taskId")
        val taskStatusRef = tasksRef.child(taskId).child("status")
        
        taskStatusRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val status = currentData.getValue(String::class.java)
                if (status == null || status == "open") {
                    currentData.value = "claimed"
                    return com.google.firebase.database.Transaction.success(currentData)
                }
                return com.google.firebase.database.Transaction.abort()
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Claim transaction error: ${error.message}")
                    listener.onClaimFailed(error.message)
                } else if (committed) {
                    Log.d(TAG, "Task claimed successfully: $taskId")
                    currentTaskId = taskId
                    listener.onClaimed(taskId)
                    listenForSignaling(taskId)
                } else {
                    Log.d(TAG, "Claim failed: Task already claimed or invalid.")
                    listener.onClaimFailed("Task already taken")
                }
            }
        })
    }

    private fun listenForSignaling(taskId: String) {
        val taskRef = tasksRef.child(taskId)

        // Listen for SDP Offer from Requester
        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.child("type").getValue(String::class.java)
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                
                if (type == "offer" && sdp != null) {
                    Log.d(TAG, "Received SDP Offer")
                    listener.onSDPOffer(sdp)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Offer listener cancelled", error.toException())
            }
        }
        taskRef.child("offer").addValueEventListener(offerListener!!)

        // Listen for ICE Candidates from Requester
        iceListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sender = snapshot.child("sender").getValue(String::class.java)
                if (sender == "requester") {
                    val candidate = snapshot.child("candidate").getValue(String::class.java)
                    val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                    
                    if (candidate != null) {
                        Log.d(TAG, "Received ICE candidate from requester")
                        listener.onICECandidate(candidate, sdpMid, sdpMLineIndex)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        taskRef.child("ice").addChildEventListener(iceListener!!)
    }

    fun sendAnswer(sdp: String) {
        val taskId = currentTaskId ?: return
        Log.d(TAG, "Sending SDP Answer")
        val answer = mapOf(
            "type" to "answer",
            "sdp" to sdp
        )
        tasksRef.child(taskId).child("answer").setValue(answer)
    }

    fun sendICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val taskId = currentTaskId ?: return
        val ice = mapOf(
            "candidate" to candidate,
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex,
            "sender" to "operator"
        )
        tasksRef.child(taskId).child("ice").push().setValue(ice)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting SignalingClient")
        tasksListener?.let { tasksRef.removeEventListener(it) }
        
        currentTaskId?.let { taskId ->
            offerListener?.let { tasksRef.child(taskId).child("offer").removeEventListener(it) }
            iceListener?.let { tasksRef.child(taskId).child("ice").removeEventListener(it) }
        }
        
        tasksListener = null
        offerListener = null
        iceListener = null
        currentTaskId = null
    }
}
