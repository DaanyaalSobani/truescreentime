package dev.daanyaal.truescreentime

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs against real SharedPreferences (via Robolectric) rather than a fake,
 * so the string-set copying in FilterStore is actually exercised — mutating
 * a set returned by SharedPreferences in place silently loses writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FilterStoreTest {

    private fun newStore() = FilterStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `apps are included by default`() {
        assertTrue(newStore().isIncluded("com.example.app"))
    }

    @Test
    fun `exclusions persist across store instances`() {
        newStore().setIncluded("com.example.app", false)

        val reopened = newStore()
        assertFalse(reopened.isIncluded("com.example.app"))
        assertEquals(setOf("com.example.app"), reopened.excludedPackages())
    }

    @Test
    fun `excluding several apps keeps every entry`() {
        val store = newStore()
        store.setIncluded("one", false)
        store.setIncluded("two", false)
        store.setIncluded("three", false)

        assertEquals(setOf("one", "two", "three"), newStore().excludedPackages())
    }

    @Test
    fun `re-including removes only that app`() {
        val store = newStore()
        store.setIncluded("one", false)
        store.setIncluded("two", false)
        store.setIncluded("one", true)

        assertEquals(setOf("two"), newStore().excludedPackages())
    }

    @Test
    fun `include all clears the list`() {
        val store = newStore()
        store.setIncluded("one", false)
        store.setIncluded("two", false)
        store.includeAll()

        assertTrue(newStore().excludedPackages().isEmpty())
    }

    @Test
    fun `display toggles default to off and persist`() {
        val store = newStore()
        assertFalse(store.showSystemApps)
        assertFalse(store.showExcludedApps)

        store.showSystemApps = true
        store.showExcludedApps = true

        val reopened = newStore()
        assertTrue(reopened.showSystemApps)
        assertTrue(reopened.showExcludedApps)
    }
}
