package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.logic.BeatAction
import com.fictioncutshort.justacalculator.logic.DormancyManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dormancy escalation is twenty notifications on a 30s beat, and Doze does
 * not honour that: it holds the alarms back and releases them together. These
 * drive [DormancyManager.planBeat] through that burst and check the sequence
 * still reaches the user one at a time.
 */
class DormancyBeatTest {

    private val gap = DormancyManager.MIN_NOTIF_GAP_MS
    private val firstId = 10
    private val count = 20

    /** Runs the alarm queue the way the receiver would, and returns what landed. */
    private fun drain(firedAt: Map<Int, Long>): List<Pair<Long, Int>> {
        // Alarms pending as (fireAt, id), earliest first.
        val queue = firedAt.map { (id, at) -> at to id }.toMutableList()
        var lastPostedId = 0
        var lastPostedAt = 0L
        var reservedUntil = 0L
        val posted = mutableListOf<Pair<Long, Int>>()

        var iterations = 0
        while (queue.isNotEmpty()) {
            // A runaway re-arm loop is the bug this guards; bound it so a
            // regression fails the test rather than hanging it.
            if (iterations++ > 1000) break
            queue.sortWith(compareBy({ it.first }, { it.second }))
            val (now, id) = queue.removeAt(0)
            when (val action = DormancyManager.planBeat(
                id = id,
                now = now,
                lastPostedId = lastPostedId,
                lastPostedAt = lastPostedAt,
                reservedUntil = reservedUntil,
            )) {
                BeatAction.Skip -> {}
                BeatAction.PostNow -> {
                    lastPostedId = id
                    lastPostedAt = now
                    posted += now to id
                }
                is BeatAction.Defer -> {
                    reservedUntil = action.at
                    queue += action.at to id
                }
            }
        }
        return posted
    }

    @Test
    fun everyBeatSurvivesADozeBurst() {
        val t = 1_000_000L
        // Worst case: all twenty alarms released in one maintenance window.
        val posted = drain((0 until count).associate { firstId + it to t })

        assertEquals(count, posted.size, "every beat should reach the user")
        assertEquals(
            (0 until count).map { firstId + it },
            posted.map { it.second },
            "and in the order the escalation was written",
        )
    }

    @Test
    fun burstBeatsArriveSpreadOut() {
        val t = 1_000_000L
        val posted = drain((0 until count).associate { firstId + it to t })

        posted.zipWithNext { (earlier, _), (later, id) ->
            assertTrue(
                later - earlier >= gap,
                "beat $id landed ${later - earlier}ms after the previous, under the ${gap}ms gap",
            )
        }
    }

    @Test
    fun beatsAlreadyOnScheduleArePostedAtTheirOwnTime() {
        val t = 1_000_000L
        // The happy path: alarms fire 30s apart as armed, comfortably over the gap.
        val onTime = (0 until count).associate { firstId + it to t + 30_000L * it }
        val posted = drain(onTime)

        assertEquals(count, posted.size)
        assertEquals(onTime.values.sorted(), posted.map { it.first }, "no beat deferred")
    }

    @Test
    fun aBeatTheInAppLoopAlreadySentIsSkipped() {
        val action = DormancyManager.planBeat(
            id = firstId + 3,
            now = 1_000_000L,
            lastPostedId = firstId + 5,
            lastPostedAt = 999_000L,
            reservedUntil = 0L,
        )
        assertEquals(BeatAction.Skip, action)
    }

    @Test
    fun anAlarmFiringAHairEarlyPostsRatherThanReArming() {
        // Its own reserved slot, reached a half-second early. Deferring here is
        // what made the queue chase itself.
        val lastPostedAt = 1_000_000L
        val action = DormancyManager.planBeat(
            id = firstId + 1,
            now = lastPostedAt + gap - 500L,
            lastPostedId = firstId,
            lastPostedAt = lastPostedAt,
            reservedUntil = lastPostedAt + gap * 19,
        )
        assertEquals(BeatAction.PostNow, action)
    }
}
