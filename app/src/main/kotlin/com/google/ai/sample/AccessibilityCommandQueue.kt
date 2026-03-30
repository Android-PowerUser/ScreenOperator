package com.google.ai.sample

import com.google.ai.sample.util.Command
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

internal class AccessibilityCommandQueue {
    private val queue = LinkedList<Command>()
    private val processing = AtomicBoolean(false)

    @Synchronized
    fun clearAndUnlock() {
        queue.clear()
        processing.set(false)
    }

    @Synchronized
    fun enqueue(command: Command): Int {
        queue.add(command)
        return queue.size
    }

    @Synchronized
    fun peek(): Command? = queue.peek()
    
    @Synchronized
    fun poll(): Command? = queue.poll()
    
    @Synchronized
    fun size(): Int = queue.size
    
    @Synchronized
    fun isEmpty(): Boolean = queue.isEmpty()
    fun tryAcquireProcessing(): Boolean = processing.compareAndSet(false, true)
    fun releaseProcessing() = processing.set(false)
}
/*
test
*/
