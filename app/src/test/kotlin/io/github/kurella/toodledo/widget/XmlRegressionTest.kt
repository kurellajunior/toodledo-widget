package io.github.kurella.toodledo.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression tests verifying critical XML resource attributes.
 * These guard against bugs found during Phase 1 testing.
 */
class XmlRegressionTest {

    private val resDir = File("src/main/res")
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun parseXml(path: String) =
        DocumentBuilderFactory.newInstance()
            .also { it.isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(path))
            .documentElement

    // Finding #8: Widget default size must be 4x1, not 3x1
    // minWidth=290dp maps to 4 columns; 250dp only maps to 3.
    @Test
    fun `widget_info has correct default size for 4x1`() {
        val root = parseXml("$resDir/xml/widget_info.xml")
        assertEquals("290dp", root.getAttributeNS(ns, "minWidth"))
        assertEquals("4", root.getAttributeNS(ns, "targetCellWidth"))
        assertEquals("1", root.getAttributeNS(ns, "targetCellHeight"))
    }

    // Finding #4: configuration_optional prevents Settings from opening on every widget add
    @Test
    fun `widget_info has configuration_optional`() {
        val root = parseXml("$resDir/xml/widget_info.xml")
        val features = root.getAttributeNS(ns, "widgetFeatures")
        assertTrue(features.contains("configuration_optional"),
            "widgetFeatures must include configuration_optional, was: $features")
        assertTrue(features.contains("reconfigurable"),
            "widgetFeatures must include reconfigurable, was: $features")
    }

    // Finding #7: widget_border.xml must have explicit transparent solid,
    // otherwise shape defaults to black fill, blocking transparency.
    @Test
    fun `widget_border has explicit transparent solid`() {
        val root = parseXml("$resDir/drawable/widget_border.xml")
        val solids = root.getElementsByTagName("solid")
        assertTrue(solids.length > 0, "widget_border.xml must have a <solid> element")
        val color = solids.item(0).attributes.getNamedItemNS(ns, "color")?.nodeValue
        assertNotNull(color, "<solid> must have android:color")
        assertTrue(color!!.contains("transparent"),
            "solid color must be transparent, was: $color")
    }

    // Finding #9: <queries> declaration needed for Toodledo package visibility (API 30+)
    @Test
    fun `manifest declares toodledo package in queries`() {
        val root = parseXml("src/main/AndroidManifest.xml")
        val queries = root.getElementsByTagName("queries")
        assertTrue(queries.length > 0, "Manifest must have <queries> element")
        val children = queries.item(0).childNodes
        val found = (0 until children.length)
            .map { children.item(it) }
            .any { it.nodeName == "package" &&
                   it.attributes.getNamedItemNS(ns, "name")?.nodeValue == "com.toodledo" }
        assertTrue(found, "Manifest must declare <package android:name=\"com.toodledo\" /> in <queries>")
    }
}
