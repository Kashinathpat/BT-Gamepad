package com.bluetooth.gamepad.layout

import com.bluetooth.gamepad.settings.FakePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRepositoryTest {

    private fun btn(id: String, extra: String = "") = """{"id":"$id","x":0.5,"y":0.5,"size":0.1$extra}"""

    private fun layoutJson(name: String, vararg buttons: String) =
        """{"name":"$name","buttons":[${buttons.joinToString(",")}]}"""

    private fun custom(id: String) = ControllerLayout(id, "Layout $id", listOf(ButtonConfig("A", "A", 0.25f, 0.5f, 0.1f)))

    @Test
    fun aSavedLayoutLoadsBackExactly() {
        val repo = LayoutRepository(FakePrefs())
        val layout = ControllerLayout(
            "c1", "My layout", listOf(
                ButtonConfig("A", "A", 0.25f, 0.5f, 0.1f),
                ButtonConfig("B_2", "Go", 0.5f, 0.5f, 0.2f, opacity = 0.5f, shape = ControlShape.PILL, behavior = ButtonBehavior.TURBO, turboHz = 20),
                ButtonConfig("MACRO", "M", 0.1f, 0.1f, 0.1f, macro = listOf("A", "B")),
                ButtonConfig("LSTICK", "L", 0.2f, 0.7f, 0.18f, floating = true),
                ButtonConfig("LB", "LB", 0.1f, 0.1f, 0.1f, behavior = ButtonBehavior.TOGGLE, gyroGate = true)
            )
        )
        repo.save(layout)
        assertEquals(layout, repo.load("c1"))
    }

    @Test
    fun theBuiltInLayoutIsAlwaysAvailable() {
        val prefs = FakePrefs()
        val repo = LayoutRepository(prefs)
        assertEquals(listOf(ControllerLayout.default()), repo.getAll())
        assertEquals(ControllerLayout.default(), repo.load(ControllerLayout.DEFAULT_ID))
        repo.save(ControllerLayout.default())
        assertTrue(prefs.data.containsKey("layout_default"))
        assertFalse(prefs.data.containsKey("layout_ids"))
    }

    @Test
    fun aDamagedBuiltInLayoutFallsBackToTheOriginal() {
        val repo = LayoutRepository(FakePrefs(mapOf("layout_default" to "not json")))
        assertEquals(ControllerLayout.default(), repo.load(ControllerLayout.DEFAULT_ID))
    }

    @Test
    fun customLayoutsKeepTheirOrderAndAreListedOnce() {
        val repo = LayoutRepository(FakePrefs())
        repo.save(custom("c1"))
        repo.save(custom("c2"))
        repo.save(custom("c3"))
        repo.save(custom("c2"))
        assertEquals(listOf("default", "c1", "c2", "c3"), repo.getAll().map { it.id })
    }

    @Test
    fun deletingRemovesTheLayoutAndItsEntry() {
        val prefs = FakePrefs()
        val repo = LayoutRepository(prefs)
        repo.save(custom("c1"))
        repo.save(custom("c2"))
        repo.delete("c1")
        assertEquals(listOf("default", "c2"), repo.getAll().map { it.id })
        assertNull(repo.load("c1"))
        assertFalse(prefs.data.containsKey("layout_c1"))
    }

    @Test
    fun theBuiltInLayoutCannotBeDeleted() {
        val repo = LayoutRepository(FakePrefs())
        repo.save(ControllerLayout.default().copy(name = "Renamed"))
        repo.delete(ControllerLayout.DEFAULT_ID)
        assertEquals("Renamed", repo.load(ControllerLayout.DEFAULT_ID)?.name)
    }

    @Test
    fun aBrokenStoredLayoutIsSkipped() {
        val source = FakePrefs()
        LayoutRepository(source).save(custom("good"))
        val prefs = FakePrefs(
            mapOf(
                "layout_ids" to "bad,good",
                "layout_bad" to "{oops",
                "layout_good" to source.data["layout_good"]!!
            )
        )
        assertEquals(listOf("default", "good"), LayoutRepository(prefs).getAll().map { it.id })
    }

    @Test
    fun savedJsonOmitsDefaultValues() {
        val json = ControllerLayout("c", "N", listOf(ButtonConfig("A", "A", 0.5f, 0.5f, 0.1f))).toJson()
        val b = json.getJSONArray("buttons").getJSONObject(0)
        for (key in listOf("opacity", "shape", "behavior", "turboHz", "macro", "floating", "gyroGate")) {
            assertFalse(key, b.has(key))
        }
        assertEquals("N", json.getString("name"))
    }

    @Test
    fun turboRateIsOnlySavedForTurboButtons() {
        val json = ControllerLayout("c", "N", listOf(
            ButtonConfig("A", "A", 0.5f, 0.5f, 0.1f, behavior = ButtonBehavior.TURBO, turboHz = 5),
            ButtonConfig("B", "B", 0.5f, 0.5f, 0.1f, behavior = ButtonBehavior.TOGGLE, turboHz = 5)
        )).toJson().getJSONArray("buttons")
        assertEquals(5, json.getJSONObject(0).getInt("turboHz"))
        assertFalse(json.getJSONObject(1).has("turboHz"))
    }

    @Test
    fun aValidImportIsSavedAndReturned() {
        val repo = LayoutRepository(FakePrefs())
        val imported = repo.importJson(layoutJson("Imported", btn("A"), btn("B_2")))
        assertNotNull(imported)
        assertEquals("Imported", imported!!.name)
        assertEquals(listOf("A", "B_2"), imported.buttons.map { it.id })
        assertEquals(imported, repo.load(imported.id))
        assertTrue(repo.getAll().any { it.id == imported.id })
    }

    @Test
    fun anImportGetsAFreshIdEachTime() {
        val repo = LayoutRepository(FakePrefs())
        val text = layoutJson("Same", btn("A"))
        assertNotEquals(repo.importJson(text)!!.id, repo.importJson(text)!!.id)
    }

    @Test
    fun aLeadingByteOrderMarkIsAccepted() {
        assertNotNull(LayoutRepository(FakePrefs()).importJson("﻿" + layoutJson("Bom", btn("A"))))
    }

    @Test
    fun brokenOrUnsafeImportsAreRejected() {
        val repo = LayoutRepository(FakePrefs())
        val bad = listOf(
            "not json",
            "",
            "{}",
            """{"name":"N"}""",
            layoutJson("N"),
            layoutJson("   ", btn("A")),
            layoutJson("N", btn("A"), btn("A")),
            layoutJson("N", btn("ZZ")),
            layoutJson("N", btn("a")),
            layoutJson("N", btn("A_")),
            layoutJson("N", btn("A_2_3")),
            layoutJson("N", """{"id":"A","y":0.5,"size":0.1}"""),
            layoutJson("N", """{"id":"A","x":"left","y":0.5,"size":0.1}"""),
            layoutJson("N", """{"x":0.5,"y":0.5,"size":0.1}""")
        )
        for (text in bad) assertNull(text, repo.importJson(text))
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun aLayoutWithTooManyControlsIsRejected() {
        val repo = LayoutRepository(FakePrefs())
        fun many(n: Int) = layoutJson("Big", *Array(n) { i -> btn(if (i == 0) "A" else "A_${i + 1}") })
        assertNotNull(repo.importJson(many(MAX_CONTROLS)))
        assertNull(repo.importJson(many(MAX_CONTROLS + 1)))
    }

    @Test
    fun importedValuesAreClampedToSaneRanges() {
        val repo = LayoutRepository(FakePrefs())
        val imported = repo.importJson(
            layoutJson(
                "  Spaced name  ",
                """{"id":"A","label":"ABCDEFGHIJKLMNOP","x":5,"y":-3,"size":9,"opacity":0.01,"turboHz":7,"macro":["A","ZZ","A","B"],"shape":"SQUARE","behavior":"BOGUS"}"""
            )
        )!!
        val a = imported.buttons.single()
        assertEquals("Spaced name", imported.name)
        assertEquals("ABCDEFGHIJ", a.label)
        assertEquals(1f, a.xFrac, 0f)
        assertEquals(0f, a.yFrac, 0f)
        assertEquals(0.8f, a.sizeFrac, 0f)
        assertEquals(MIN_OPACITY, a.opacity, 0f)
        assertEquals(10, a.turboHz)
        assertEquals(listOf("A", "B"), a.macro)
        assertNull(a.shape)
        assertEquals(ButtonBehavior.NORMAL, a.behavior)
    }

    @Test
    fun aTinyImportedSizeIsRaisedToTheMinimum() {
        val a = LayoutRepository(FakePrefs()).importJson(layoutJson("N", """{"id":"A","x":0.5,"y":0.5,"size":0}"""))!!.buttons.single()
        assertEquals(0.03f, a.sizeFrac, 0f)
    }

    @Test
    fun aLongNameIsCutTo40Characters() {
        val imported = LayoutRepository(FakePrefs()).importJson(layoutJson("x".repeat(60), btn("A")))!!
        assertEquals(40, imported.name.length)
    }

    @Test
    fun validTurboRatesAreKept() {
        for (hz in TURBO_RATES) {
            val a = LayoutRepository(FakePrefs()).importJson(layoutJson("N", btn("A", ""","behavior":"TURBO","turboHz":$hz""")))!!.buttons.single()
            assertEquals(hz, a.turboHz)
            assertEquals(ButtonBehavior.TURBO, a.behavior)
        }
    }

    @Test
    fun duplicatingSavesACopyWithANewIdAndName() {
        val repo = LayoutRepository(FakePrefs())
        val original = custom("c1").copy(name = "Mine")
        val copy = repo.duplicate(original)
        assertNotEquals(original.id, copy.id)
        assertEquals("Mine copy", copy.name)
        assertEquals(original.buttons, copy.buttons)
        assertEquals(copy, repo.load(copy.id))
    }

    @Test
    fun aNewCustomLayoutStartsFromTheDefaultButtons() {
        val created = LayoutRepository(FakePrefs()).newCustom("Fresh")
        assertEquals("Fresh", created.name)
        assertEquals(ControllerLayout.defaultButtons(), created.buttons)
        assertFalse(created.isDefault)
    }

    @Test
    fun theDefaultButtonsSurviveSavingAndLoading() {
        val repo = LayoutRepository(FakePrefs())
        val saved = ControllerLayout("c1", "Std", ControllerLayout.defaultButtons())
        repo.save(saved)
        assertEquals(saved, repo.load("c1"))
    }
}
