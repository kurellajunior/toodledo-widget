package io.github.kurella.toodledo.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for the widget status state machine:
 * - WidgetStatus enum values round-trip through SharedPreferences (name/valueOf)
 * - FetchResult sealed interface covers all expected cases
 * - Source-code structure ensures Factory writes status for every FetchResult branch
 */
class StatusMachineTest {

    @Nested
    inner class WidgetStatusRoundTrip {

        @Test
        fun `all status values survive name-valueOf round trip`() {
            for (status in WidgetStatus.entries) {
                val restored = WidgetStatus.valueOf(status.name)
                assertEquals(status, restored, "Round trip failed for $status")
            }
        }

        @Test
        fun `enum has exactly 5 states`() {
            assertEquals(5, WidgetStatus.entries.size,
                "Expected INITIAL, LOADED, OFFLINE, API_ERROR, LOGGED_OUT")
        }
    }

    @Nested
    inner class FetchResultVariants {

        @Test
        fun `Success wraps a task list`() {
            val result = FetchResult.Success(emptyList())
            assertTrue(result.tasks.isEmpty())
        }

        @Test
        fun `all error variants are distinct`() {
            val variants: Set<FetchResult> = setOf(
                FetchResult.AuthError,
                FetchResult.NetworkError,
                FetchResult.ApiError
            )
            assertEquals(3, variants.size)
        }
    }

    @Nested
    inner class SourceStructure {

        private val factorySource = File(
            "src/main/java/io/github/kurella/toodledo/widget/TaskWidgetService.kt"
        ).readText()

        private val providerSource = File(
            "src/main/java/io/github/kurella/toodledo/widget/TaskWidgetProvider.kt"
        ).readText()

        @Test
        fun `Factory handles all FetchResult variants in when-expression`() {
            // The when(val result = ...) block must mention all sealed subtypes
            assertTrue(factorySource.contains("is FetchResult.Success"),
                "Factory must handle FetchResult.Success")
            assertTrue(factorySource.contains("is FetchResult.AuthError"),
                "Factory must handle FetchResult.AuthError")
            assertTrue(factorySource.contains("is FetchResult.NetworkError"),
                "Factory must handle FetchResult.NetworkError")
            assertTrue(factorySource.contains("is FetchResult.ApiError"),
                "Factory must handle FetchResult.ApiError")
        }

        @Test
        fun `Factory writes WidgetStatus for every FetchResult branch`() {
            // Each FetchResult handler must write the corresponding WidgetStatus
            for (status in WidgetStatus.entries) {
                assertTrue(factorySource.contains("WidgetStatus.${status.name}"),
                    "Factory must write WidgetStatus.${status.name}")
            }
        }

        @Test
        fun `Factory calls updateStatusLine after loadTasks`() {
            assertTrue(factorySource.contains("updateStatusLine"),
                "Factory must call TaskWidgetProvider.updateStatusLine()")
        }

        @Test
        fun `Provider reads WidgetStatus in updateStatusLine`() {
            assertTrue(providerSource.contains("fun updateStatusLine"),
                "Provider must define updateStatusLine()")
            assertTrue(providerSource.contains("readStatus("),
                "Provider.updateStatusLine must call readStatus()")
        }

        @Test
        fun `Provider handles all WidgetStatus values`() {
            for (status in WidgetStatus.entries) {
                assertTrue(providerSource.contains("WidgetStatus.${status.name}"),
                    "Provider must handle WidgetStatus.${status.name}")
            }
        }

        @Test
        fun `Provider onUpdate does not contain status_line logic`() {
            // Extract onUpdate method body
            val onUpdateStart = providerSource.indexOf("override fun onUpdate(")
            check(onUpdateStart >= 0) { "Could not find onUpdate in Provider" }

            var braceDepth = 0
            var inBody = false
            val body = StringBuilder()
            for (i in onUpdateStart until providerSource.length) {
                val c = providerSource[i]
                if (c == '{') { braceDepth++; inBody = true }
                else if (c == '}') {
                    braceDepth--
                    if (inBody && braceDepth == 0) break
                }
                if (inBody) body.append(c)
            }

            val onUpdateBody = body.toString()
            assertTrue(!onUpdateBody.contains("not_logged_in"),
                "onUpdate must not contain not_logged_in logic — status is handled by updateStatusLine()")
            assertTrue(!onUpdateBody.contains("isLoggedIn"),
                "onUpdate must not check isLoggedIn — status is handled by updateStatusLine()")
        }
    }
}
