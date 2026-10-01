package com.pocketarcade.godot

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Records the raw touch stream on Godot's view, as build-13's game canvas forwarded it: every
 * pointer that went down, moved or came up, and for a move every batched (historical) sample
 * first, oldest first, each at its own event time, so a flick's speed is measured on all of them.
 * A pointer only reports a move when its position changed. It never consumes an event: Godot
 * still gets them all.
 *
 * Samples are five ints: action, pointer id, x and y in view pixels times 16, and the event time
 * in ms since capture began. [take] hands them over (the clock first) and empties the buffer.
 */
internal class TouchRecorder : View.OnTouchListener {
    companion object {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val CANCEL = 3
        const val STRIDE = 5
        const val FIXED = 16f

        /** Samples kept between two frames before the oldest are dropped (a stalled game). */
        private const val MAX_SAMPLES = 4096
    }

    private val lock = Any()
    private var buf = IntArray(256 * STRIDE)
    private var count = 0
    private var base = 0L
    private val lastX = HashMap<Int, Float>()
    private val lastY = HashMap<Int, Float>()

    @Volatile var capturing = false
        private set

    /** Deliver the first finger's stream unbatched (build-13 did this while a round was playing). */
    @Volatile var unbuffered = false

    fun start() {
        synchronized(lock) {
            base = SystemClock.uptimeMillis()
            count = 0
            lastX.clear()
            lastY.clear()
        }
        capturing = true
    }

    fun stop() {
        capturing = false
        synchronized(lock) { count = 0 }
    }

    fun take(): IntArray {
        synchronized(lock) {
            val out = IntArray(1 + count * STRIDE)
            out[0] = (SystemClock.uptimeMillis() - base).toInt()
            System.arraycopy(buf, 0, out, 1, count * STRIDE)
            count = 0
            return out
        }
    }

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        if (!capturing) return false
        val action = e.actionMasked
        if (unbuffered && action == MotionEvent.ACTION_DOWN) v.requestUnbufferedDispatch(e)
        synchronized(lock) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    moves(e, e.actionIndex)
                    point(DOWN, e, e.actionIndex)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    moves(e, e.actionIndex)
                    point(UP, e, e.actionIndex)
                }
                MotionEvent.ACTION_CANCEL -> for (p in 0 until e.pointerCount) point(CANCEL, e, p)
                MotionEvent.ACTION_MOVE -> {
                    for (h in 0 until e.historySize) {
                        for (p in 0 until e.pointerCount) {
                            moved(e.getPointerId(p), e.getHistoricalX(p, h), e.getHistoricalY(p, h), e.getHistoricalEventTime(h))
                        }
                    }
                    moves(e, -1)
                }
            }
        }
        return false
    }

    /** Every pointer but [except] that has moved, at the event's own time. */
    private fun moves(e: MotionEvent, except: Int) {
        for (p in 0 until e.pointerCount) {
            if (p != except) moved(e.getPointerId(p), e.getX(p), e.getY(p), e.eventTime)
        }
    }

    private fun moved(id: Int, x: Float, y: Float, time: Long) {
        if (lastX[id] == x && lastY[id] == y) return
        add(MOVE, id, x, y, time)
    }

    private fun point(kind: Int, e: MotionEvent, index: Int) {
        add(kind, e.getPointerId(index), e.getX(index), e.getY(index), e.eventTime)
        if (kind != DOWN) {
            lastX.remove(e.getPointerId(index))
            lastY.remove(e.getPointerId(index))
        }
    }

    private fun add(kind: Int, id: Int, x: Float, y: Float, time: Long) {
        if (kind == DOWN || kind == MOVE) {
            lastX[id] = x
            lastY[id] = y
        }
        if (count >= MAX_SAMPLES) {
            // Drop the oldest half rather than grow without bound.
            val keep = count / 2
            System.arraycopy(buf, (count - keep) * STRIDE, buf, 0, keep * STRIDE)
            count = keep
        }
        if ((count + 1) * STRIDE > buf.size) buf = buf.copyOf(buf.size * 2)
        val at = count * STRIDE
        buf[at] = kind
        buf[at + 1] = id
        buf[at + 2] = (x * FIXED).toInt()
        buf[at + 3] = (y * FIXED).toInt()
        buf[at + 4] = (time - base).toInt()
        count++
    }
}
