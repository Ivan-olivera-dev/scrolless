package com.scrolless.app

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.scrolless.app.core.DayRecord
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.ShortFormTarget
import com.scrolless.app.data.LocalStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory SharedPreferences with Android's typed-getter semantics (ClassCastException on type mismatch). */
private class FakePrefs : SharedPreferences {
    val map = LinkedHashMap<String, Any?>()
    var commits = 0
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?): String? = if (map.containsKey(key)) map[key] as String? else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (map.containsKey(key)) map[key] as MutableSet<String>? else defValues
    override fun getInt(key: String?, defValue: Int): Int = if (map.containsKey(key)) map[key] as Int else defValue
    override fun getLong(key: String?, defValue: Long): Long = if (map.containsKey(key)) map[key] as Long else defValue
    override fun getFloat(key: String?, defValue: Float): Float = if (map.containsKey(key)) map[key] as Float else defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = if (map.containsKey(key)) map[key] as Boolean else defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Ed()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    private object Removed

    inner class Ed : SharedPreferences.Editor {
        private val mods = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(k: String, v: String?) = apply { mods[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { mods[k] = v }
        override fun putInt(k: String, v: Int) = apply { mods[k] = v }
        override fun putLong(k: String, v: Long) = apply { mods[k] = v }
        override fun putFloat(k: String, v: Float) = apply { mods[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { mods[k] = v }
        override fun remove(k: String) = apply { mods[k] = Removed }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) map.clear()
            mods.forEach { (k, v) -> if (v === Removed) map.remove(k) else map[k] = v }
            commits++
            mods.keys.forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this@FakePrefs, k) } }
            return true
        }
        override fun apply() { commit() }
    }
}

/** Requires `unitTests.isReturnDefaultValues = true` so the android.jar ContextWrapper constructor does not throw. */
private class FakeContext(private val prefs: FakePrefs) : ContextWrapper(null) {
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
}

class LocalStoreMigrationTest {
    private val obsolete = setOf(
        "is_currently_in_reel_tab", "current_target", "mindful_allowance_target",
        "mindful_until_epoch", "weekly_interceptions", "inspector_enabled", "last_inspected_ids"
    )

    private fun legacyCommon(): Map<String, Any?> = mapOf(
        "protection_mode" to "STRICT_BUDGET",
        "daily_limit_sec" to 420,
        "enabled_targets" to "INSTAGRAM_REELS,TIKTOK",
        "weekly_interceptions" to 14,
        "streak_days" to 4,
        "streak_shields" to 2,
        "active_reel_sec_today" to 300,
        "interceptions_today" to 3,
        "is_currently_in_reel_tab" to true,
        "mindful_until_epoch" to 123L,
        "last_date_iso" to "2026-11-14",
        "dark_theme" to true,
        "ui_lang" to "en",
        "inspector_enabled" to true,
        "last_inspected_ids" to "a|b"
    )

    @Test
    fun `v1 store resets mocked counters keeps config and ui and drops only obsolete keys`() {
        val p = FakePrefs().apply { map.putAll(legacyCommon()) }
        LocalStore(FakeContext(p))
        val expected = HashMap(legacyCommon().filterKeys { it !in obsolete }).apply {
            put("streak_days", 0); put("streak_shields", 0); put("active_reel_sec_today", 0); put("interceptions_today", 0)
            put("store_schema_version", 3)
        }
        assertEquals(expected, HashMap(p.map))
    }

    @Test
    fun `v2 store keeps config and runtime counters unchanged and drops only obsolete keys`() {
        val v2 = legacyCommon() + mapOf(
            "store_schema_version" to 2,
            "current_target" to "INSTAGRAM_REELS",
            "mindful_allowance_target" to "YOUTUBE_SHORTS"
        )
        val p = FakePrefs().apply { map.putAll(v2) }
        val store = LocalStore(FakeContext(p))
        val expected = HashMap(v2.filterKeys { it !in obsolete }).apply { put("store_schema_version", 3) }
        assertEquals(expected, HashMap(p.map))
        assertEquals(
            ProtectionConfig(ProtectionMode.STRICT_BUDGET, 420, setOf(ShortFormTarget.INSTAGRAM_REELS, ShortFormTarget.TIKTOK), emptySet()),
            store.loadConfig()
        )
        val r = store.loadRuntime()
        assertEquals(4, r.streakDays); assertEquals(2, r.streakShields)
        assertEquals(300, r.activeSecondsToday); assertEquals(3, r.interceptionsToday)
        assertEquals("2026-11-14", r.dayIso)
    }

    @Test
    fun `migration is idempotent across a second construction`() {
        val p = FakePrefs().apply { map.putAll(legacyCommon() + ("store_schema_version" to 2)) }
        LocalStore(FakeContext(p))
        val after1 = HashMap(p.map); val commits1 = p.commits
        LocalStore(FakeContext(p))
        assertEquals(after1, HashMap(p.map))
        assertEquals(commits1, p.commits)

        val q = FakePrefs().apply { map.putAll(legacyCommon()) }
        LocalStore(FakeContext(q)); val q1 = HashMap(q.map); val qc = q.commits
        LocalStore(FakeContext(q))
        assertEquals(q1, HashMap(q.map)); assertEquals(qc, q.commits)
    }

    @Test
    fun `garbage enum history target and date strings do not crash and loads never write`() {
        val p = FakePrefs().apply {
            map.putAll(mapOf(
                "store_schema_version" to 3,
                "protection_mode" to "BOGUS",
                "enabled_targets" to "FOO, ,INSTAGRAM_REELS",
                "full_app_targets" to "TIKTOK,YOUTUBE_SHORTS,xx",
                "history_7d" to "garbage;2026-13-01=1,2;2026-11-10=5;2026-11-11=7,1;=;;x=1,2",
                "pass_target" to "NOPE",
                "last_date_iso" to "notadate"
            ))
        }
        val store = LocalStore(FakeContext(p))
        val before = HashMap(p.map); val c = p.commits
        val cfg = store.loadConfig()
        assertEquals(ProtectionMode.MINDFUL_BUDGET, cfg.mode)
        assertEquals(ProtectionMode.MINDFUL_BUDGET.defaultDailyLimitSeconds, cfg.dailyLimitSeconds)
        assertEquals(setOf(ShortFormTarget.INSTAGRAM_REELS), cfg.enabledTargets)
        assertEquals(setOf(ShortFormTarget.YOUTUBE_SHORTS), cfg.fullAppTargets)
        val r = store.loadRuntime()
        assertEquals(listOf(DayRecord("2026-11-11", 7, 1)), r.history)
        assertNull(r.passTarget)
        val today = store.loadRuntimeForToday(cfg)
        assertEquals(LocalDate.now().toString(), today.dayIso)
        assertEquals(before, HashMap(p.map)); assertEquals(c, p.commits)
    }

    @Test
    fun `missing keys on fresh install give defaults`() {
        val p = FakePrefs()
        val store = LocalStore(FakeContext(p))
        assertEquals(ProtectionConfig(), store.loadConfig())
        assertEquals(0, store.loadRuntime().streakDays)
        assertEquals(3, p.map["store_schema_version"])
    }

    @Test
    fun `saveConfig touches only config keys and saveRuntime only runtime keys`() {
        val p = FakePrefs().apply { map.putAll(legacyCommon() + ("store_schema_version" to 2)) }
        val store = LocalStore(FakeContext(p))
        val before = HashMap(p.map)
        store.saveConfig(
            ProtectionConfig(
                ProtectionMode.MINDFUL_BUDGET, 600, setOf(ShortFormTarget.YOUTUBE_SHORTS),
                setOf(ShortFormTarget.INSTAGRAM_REELS, ShortFormTarget.TIKTOK)
            )
        )
        val changed = (before.keys + p.map.keys).filter { before[it] != p.map[it] }.toSet()
        assertTrue("changed=$changed", LocalStore.CONFIG_KEYS.containsAll(changed))
        assertEquals("INSTAGRAM_REELS", p.map["full_app_targets"])
        assertEquals(setOf(ShortFormTarget.INSTAGRAM_REELS), store.loadConfig().fullAppTargets)
        assertEquals(4, p.map["streak_days"])

        val mid = HashMap(p.map)
        store.saveRuntime(RuntimeState(dayIso = "2026-11-15", activeSecondsToday = 9, streakDays = 5))
        val changed2 = (mid.keys + p.map.keys).filter { mid[it] != p.map[it] }.toSet()
        assertTrue("changed2=$changed2", changed2.none { it in LocalStore.CONFIG_KEYS })
        assertEquals(LocalStore.CONFIG_KEYS.associateWith { mid[it] }, LocalStore.CONFIG_KEYS.associateWith { p.map[it] })
    }

    @Test
    fun `v2 store from yesterday rolls over without losing the streak`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val p = FakePrefs().apply {
            map.putAll(legacyCommon() + mapOf("store_schema_version" to 2, "last_date_iso" to yesterday, "daily_limit_sec" to 900))
        }
        val store = LocalStore(FakeContext(p))
        val r = store.loadRuntimeForToday()
        assertEquals(5, r.streakDays)
        assertEquals(0, r.activeSecondsToday)
        assertEquals(listOf(DayRecord(yesterday, 300, 3)), r.history)
    }
}
