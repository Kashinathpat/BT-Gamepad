package com.bluetooth.gamepad.layout

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayoutStorageDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "device_test_layouts"

    private fun prefs(): SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    private fun btn(id: String, extra: String = "") = """{"id":"$id","x":0.5,"y":0.5,"size":0.1$extra}"""

    private fun layoutJson(name: String, vararg buttons: String) =
        """{"name":"$name","buttons":[${buttons.joinToString(",")}]}"""

    @Before
    @After
    fun clean() {
        // commit() waits for any pending write, which would otherwise recreate the file after it is deleted.
        prefs().edit().clear().commit()
        context.deleteSharedPreferences(fileName)
    }

    @Test
    fun aRichLayoutSurvivesSavingAndLoadingWithAndroidsOwnJsonParser() {
        val layout = ControllerLayout(
            "c1", "My layout", listOf(
                ButtonConfig("A", "A", 0.25f, 0.5f, 0.1f),
                ButtonConfig("B_2", "Go", 0.5f, 0.5f, 0.2f, opacity = 0.5f, shape = ControlShape.PILL, behavior = ButtonBehavior.TURBO, turboHz = 20),
                ButtonConfig("MACRO", "M", 0.1f, 0.1f, 0.1f, macro = listOf("A", "B")),
                ButtonConfig("LSTICK", "L", 0.2f, 0.7f, 0.18f, floating = true),
                ButtonConfig("LB", "LB", 0.1f, 0.1f, 0.1f, behavior = ButtonBehavior.TOGGLE, gyroGate = true)
            )
        )
        LayoutRepository(prefs()).save(layout)
        assertEquals(layout, LayoutRepository(prefs()).load("c1"))
    }

    @Test
    fun theDefaultLayoutRoundTripsThroughImport() {
        val repo = LayoutRepository(prefs())
        val text = ControllerLayout.default().toJson().toString()
        val imported = repo.importJson(text)
        assertNotNull(imported)
        assertEquals(ControllerLayout.defaultButtons(), imported!!.buttons)
    }

    @Test
    fun brokenOrUnsafeImportsAreRejected() {
        val repo = LayoutRepository(prefs())
        val bad = listOf(
            "not json",
            "",
            "{}",
            layoutJson("N"),
            layoutJson("   ", btn("A")),
            layoutJson("N", btn("A"), btn("A")),
            layoutJson("N", btn("ZZ")),
            layoutJson("N", btn("a")),
            layoutJson("N", """{"id":"A","x":"left","y":0.5,"size":0.1}"""),
            layoutJson("N", """{"x":0.5,"y":0.5,"size":0.1}""")
        )
        for (text in bad) assertNull(text, repo.importJson(text))
    }

    @Test
    fun importedValuesAreClampedToSaneRanges() {
        val a = LayoutRepository(prefs()).importJson(
            layoutJson(
                "  Spaced name  ",
                """{"id":"A","label":"ABCDEFGHIJKLMNOP","x":5,"y":-3,"size":9,"opacity":0.01,"turboHz":7,"macro":["A","ZZ","A","B"],"shape":"SQUARE","behavior":"BOGUS"}"""
            )
        )!!
        val b = a.buttons.single()
        assertEquals("Spaced name", a.name)
        assertEquals("ABCDEFGHIJ", b.label)
        assertEquals(1f, b.xFrac, 0f)
        assertEquals(0f, b.yFrac, 0f)
        assertEquals(0.8f, b.sizeFrac, 0f)
        assertEquals(MIN_OPACITY, b.opacity, 0f)
        assertEquals(10, b.turboHz)
        assertEquals(listOf("A", "B"), b.macro)
        assertNull(b.shape)
        assertEquals(ButtonBehavior.NORMAL, b.behavior)
    }

    @Test
    fun aLeadingByteOrderMarkIsAccepted() {
        assertNotNull(LayoutRepository(prefs()).importJson("﻿" + layoutJson("Bom", btn("A"))))
    }

    @Test
    fun deletingAndListingWorkOnRealStorage() {
        val repo = LayoutRepository(prefs())
        repo.save(ControllerLayout("c1", "One", listOf(ButtonConfig("A", "A", 0.5f, 0.5f, 0.1f))))
        repo.save(ControllerLayout("c2", "Two", listOf(ButtonConfig("B", "B", 0.5f, 0.5f, 0.1f))))
        assertEquals(listOf("default", "c1", "c2"), repo.getAll().map { it.id })
        repo.delete("c1")
        assertEquals(listOf("default", "c2"), LayoutRepository(prefs()).getAll().map { it.id })
    }
}
