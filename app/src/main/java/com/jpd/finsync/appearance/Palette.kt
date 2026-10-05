package com.jpd.finsync.appearance

// The palette the user set (spec "Palette"), and the single source of its values: ARGB literals
// (0xAARRGGBB), each colour with independent dark and light values. values/colors.xml (light)
// and values-night/colors.xml (dark) must match it, which PaletteDriftTest checks. Android code
// converts a value with toInt(). Plain Kotlin, no Android imports (spec "Portability").

/** One colour's dark-mode and light-mode values. */
data class ModeColours(val dark: Long, val light: Long)

/** The base colour roles (spec "Base colours"). [key] is the role's name in colors.xml. */
enum class BaseRole(val key: String, val colours: ModeColours) {
    BG_PRIMARY("bg_primary", ModeColours(dark = 0xFF030505, light = 0xFFF5FAFA)),
    SURFACE_1("surface_1", ModeColours(dark = 0xFF111416, light = 0xFFF0F5F5)),
    SURFACE_2("surface_2", ModeColours(dark = 0xFF1F262B, light = 0xFFE4ECEC)),
    TEXT_PRIMARY("text_primary", ModeColours(dark = 0xFFF0F7FC, light = 0xFF030505)),
    MUTED("muted", ModeColours(dark = 0xFF506575, light = 0xFF506575)),
    STATUS_GOOD("status_good", ModeColours(dark = 0xFF00FFAA, light = 0xFF00FFAA))
}

object Palette {

    /** "Black" in the icon rule: the page's near-black, not pure black. */
    const val ICON_BLACK: Long = 0xFF030505

    /** "White" in the icon rule. */
    const val ICON_WHITE: Long = 0xFFFFFFFF

    /** Each accent's values (spec "Accents"). colors.xml names them accent_<key>. */
    fun accent(accent: Accent): ModeColours = when (accent) {
        Accent.GREEN -> ModeColours(dark = 0xFF00FFAA, light = 0xFF05D18D)
        Accent.BLUE -> ModeColours(dark = 0xFF33BBFF, light = 0xFF0E8DCD)
        Accent.PURPLE -> ModeColours(dark = 0xFF9C49EE, light = 0xFF9429FF)
        Accent.PINK -> ModeColours(dark = 0xFFFF1F9E, light = 0xFFFF1F9E)
        Accent.RED -> ModeColours(dark = 0xFFFF0040, light = 0xFFFF0040)
    }
}
