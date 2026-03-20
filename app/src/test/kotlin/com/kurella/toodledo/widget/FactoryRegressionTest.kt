package com.kurella.toodledo.widget

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Finding #1: Factory.onCreate must NOT call loadTasks() — it runs on the main thread.
 * Data loading must only happen in onDataSetChanged (binder thread).
 *
 * This is a source-code structure test: we parse the source file and verify
 * that onCreate() does not contain a call to loadTasks().
 */
class FactoryRegressionTest {

    @Test
    fun `Factory onCreate does not call loadTasks`() {
        val source = File("src/main/java/com/kurella/toodledo/widget/TaskWidgetService.kt")
            .readText()

        // Extract the onCreate method body
        val onCreateStart = source.indexOf("override fun onCreate()")
        check(onCreateStart >= 0) { "Could not find Factory.onCreate() in source" }

        // Find the matching closing brace
        var braceDepth = 0
        var inBody = false
        val body = StringBuilder()
        for (i in onCreateStart until source.length) {
            val c = source[i]
            if (c == '{') {
                braceDepth++
                inBody = true
            } else if (c == '}') {
                braceDepth--
                if (inBody && braceDepth == 0) break
            }
            if (inBody) body.append(c)
        }

        val onCreateBody = body.toString()
        assertFalse(onCreateBody.contains("loadTasks"),
            "Factory.onCreate() must not call loadTasks() — it runs on the main thread!\n" +
            "Found in body: $onCreateBody")
    }
}
