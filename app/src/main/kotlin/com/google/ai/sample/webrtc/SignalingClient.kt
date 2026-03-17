package com.google.ai.sample.webrtc

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database signaling client for the ScreenOperator (Requester).
 * Posts tasks to the broker and handles waiting for a claim.
 */
class SignalingClient(
    private val listener: SignalingListener
) {
    companion object {
        private const val TAG = "SignalingClient"
    }

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val tasksRef: DatabaseReference = database.getReference("tasks")
    
    private var currentTaskId: String? = null
    
    // Listeners
    private var taskStatusListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null
    private var iceListener: ChildEventListener? = null

    interface SignalingListener {
        fun onTaskPosted(taskId: String)
        fun onTaskClaimed(taskId: String)
        fun onSDPAnswer(sdp: String)
        fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
        fun onPeerDisconnected()
        fun onError(message: String)
    }

    fun postTask(text: String, hasScreenshot: Boolean, supportId: String? = null) {
        // Create a new task entry
        val taskId = tasksRef.push().key
        if (taskId == null) {
            listener.onError("Failed to generate task ID")
            return
        }

        currentTaskId = taskId
        
        val taskData = mutableMapOf<String, Any>(
            "text" to text,
            "status" to "open",
            "timestamp" to System.currentTimeMillis()
        )
        if (supportId != null) {
            taskData["supportId"] = supportId
        }

        tasksRef.child(taskId).setValue(taskData)
            .addOnSuccessListener {
                Log.d(TAG, "Task posted successfully: $taskId")
                listener.onTaskPosted(taskId)
                listenForTaskStatus(taskId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to post task", e)
                listener.onError("Failed to post task: ${e.message}")
            }
    }

    private fun listenForTaskStatus(taskId: String) {
        taskStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                if (status == "claimed") {
                    Log.d(TAG, "Task claimed by operator")
                    listener.onTaskClaimed(taskId)
                    listenForSignaling(taskId)
                    // We can stop listening for status changes now if we want, 
                    // or keep it to detect cancellations/disconnects?
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Task status listener cancelled", error.toException())
            }
        }
        tasksRef.child(taskId).child("status").addValueEventListener(taskStatusListener!!)
    }

    private fun listenForSignaling(taskId: String) {
        val taskRef = tasksRef.child(taskId)

        // Listen for SDP Answer from Operator
        answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.child("type").getValue(String::class.java)
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                
                if (type == "answer" && sdp != null) {
                    Log.d(TAG, "Received SDP Answer")
                    listener.onSDPAnswer(sdp)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Answer listener cancelled", error.toException())
            }
        }
        taskRef.child("answer").addValueEventListener(answerListener!!)

        // Listen for ICE Candidates from Operator
        iceListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sender = snapshot.child("sender").getValue(String::class.java)
                if (sender == "operator") {
                    val candidate = snapshot.child("candidate").getValue(String::class.java)
                    val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                    
                    if (candidate != null) {
                        Log.d(TAG, "Received ICE candidate from operator")
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

    fun sendOffer(sdp: String) {
        val taskId = currentTaskId ?: return
        Log.d(TAG, "Sending SDP Offer")
        val offer = mapOf(
            "type" to "offer",
            "sdp" to sdp
        )
        tasksRef.child(taskId).child("offer").setValue(offer)
    }

    fun sendICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val taskId = currentTaskId ?: return
        val ice = mapOf(
            "candidate" to candidate,
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex,
            "sender" to "requester"
        )
        tasksRef.child(taskId).child("ice").push().setValue(ice)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting SignalingClient")
        currentTaskId?.let { taskId ->
            // Optionally close the task or mark it as cancelled?
            // For now, just stop listening.
            taskStatusListener?.let { tasksRef.child(taskId).child("status").removeEventListener(it) }
            answerListener?.let { tasksRef.child(taskId).child("answer").removeEventListener(it) }
            iceListener?.let { tasksRef.child(taskId).child("ice").removeEventListener(it) }
        }
        
        taskStatusListener = null
        answerListener = null
        iceListener = null
        currentTaskId = null
    }
}
