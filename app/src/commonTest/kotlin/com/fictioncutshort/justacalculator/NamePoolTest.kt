package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.screens.NamePool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Building 6 draws its cast from the player's phonebook. Repeats inside one
 * role are what made it look broken, so these hold the rotation to it.
 */
class NamePoolTest {

    private val contacts = listOf("Ada", "Bo", "Cleo", "Dev", "Eve")
    private val first: (List<String>) -> String = { it.first() }

    @Test
    fun everyContactGetsATurnBeforeAnyRepeats() {
        val pool = NamePool()
        val drawn = (1..contacts.size).map { pool.next(contacts, first) }
        assertEquals(contacts, drawn, "should walk the phonebook in order")
        assertEquals(contacts.toSet(), drawn.toSet(), "no repeats within one cycle")
    }

    @Test
    fun randomPickingAlsoNeverRepeatsWithinACycle() {
        val pool = NamePool()
        val drawn = (1..contacts.size).map { pool.next(contacts) { it.random() } }
        assertEquals(contacts.size, drawn.toSet().size, "drew a duplicate: $drawn")
        assertEquals(contacts.toSet(), drawn.toSet())
    }

    @Test
    fun theRotationStartsOverRatherThanRunningDry() {
        val pool = NamePool()
        repeat(contacts.size) { pool.next(contacts, first) }
        // Everyone has had a turn; a name is still better than none.
        assertNotNull(pool.next(contacts, first), "should start a fresh cycle")
        // And the fresh cycle is itself repeat-free.
        val second = (2..contacts.size).map { pool.next(contacts, first) }
        assertEquals(contacts.drop(1), second)
    }

    @Test
    fun rolesRotateIndependently() {
        // A friend who came out to help is still free to call later — that
        // crossover is deliberate, it's repetition within a role that is not.
        val helpers = NamePool()
        val askers = NamePool()
        val helped = helpers.next(contacts, first)
        val asked = askers.next(contacts, first)
        assertEquals(helped, asked, "a separate pool is not blocked by the other")
    }

    @Test
    fun anEmptyPhonebookYieldsNothingToNameWith() {
        assertNull(NamePool().next(emptyList(), first))
    }

    @Test
    fun issuedTracksWhoHasBeenSeen() {
        val pool = NamePool()
        pool.next(contacts, first)
        pool.next(contacts, first)
        assertEquals(setOf("Ada", "Bo"), pool.issued)
        assertTrue("Cleo" !in pool.issued)
    }
}
