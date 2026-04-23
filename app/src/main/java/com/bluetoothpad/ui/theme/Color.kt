package com.bluetoothpad.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light scheme (seed #0061A4) ──────────────────────────────────────────────
val md_light_primary                = Color(0xFF0061A4)
val md_light_onPrimary              = Color(0xFFFFFFFF)
val md_light_primaryContainer       = Color(0xFFD1E4FF)
val md_light_onPrimaryContainer     = Color(0xFF001D36)
val md_light_secondary              = Color(0xFF535F70)
val md_light_onSecondary            = Color(0xFFFFFFFF)
val md_light_secondaryContainer     = Color(0xFFD7E3F7)
val md_light_onSecondaryContainer   = Color(0xFF101C2B)
val md_light_tertiary               = Color(0xFF6B5778)
val md_light_onTertiary             = Color(0xFFFFFFFF)
val md_light_tertiaryContainer      = Color(0xFFF2DAFF)
val md_light_onTertiaryContainer    = Color(0xFF251431)
val md_light_error                  = Color(0xFFBA1A1A)
val md_light_onError                = Color(0xFFFFFFFF)
val md_light_errorContainer         = Color(0xFFFFDAD6)
val md_light_onErrorContainer       = Color(0xFF410002)
val md_light_background             = Color(0xFFFAFCFF)
val md_light_onBackground           = Color(0xFF1A1C1E)
val md_light_surface                = Color(0xFFFAFCFF)
val md_light_onSurface              = Color(0xFF1A1C1E)
val md_light_onSurfaceVariant       = Color(0xFF43474E)
val md_light_outline                = Color(0xFF73777F)
val md_light_outlineVariant         = Color(0xFFC3C7CF)
val md_light_surfaceContainer       = Color(0xFFEAEEF6)
val md_light_surfaceContainerHigh   = Color(0xFFE4E8F1)
val md_light_surfaceContainerLow    = Color(0xFFF0F4FC)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_inverseSurface         = Color(0xFF2F3033)
val md_light_inverseOnSurface       = Color(0xFFF1F0F4)
val md_light_inversePrimary         = Color(0xFF9ECAFF)

// ── Dark scheme (seed #0061A4) ───────────────────────────────────────────────
val md_dark_primary                 = Color(0xFF9ECAFF)
val md_dark_onPrimary               = Color(0xFF003258)
val md_dark_primaryContainer        = Color(0xFF00497D)
val md_dark_onPrimaryContainer      = Color(0xFFD1E4FF)
val md_dark_secondary               = Color(0xFFBBC7DB)
val md_dark_onSecondary             = Color(0xFF253140)
val md_dark_secondaryContainer      = Color(0xFF3B4858)
val md_dark_onSecondaryContainer    = Color(0xFFD7E3F7)
val md_dark_tertiary                = Color(0xFFD6BEE4)
val md_dark_onTertiary              = Color(0xFF3B2948)
val md_dark_tertiaryContainer       = Color(0xFF523F5F)
val md_dark_onTertiaryContainer     = Color(0xFFF2DAFF)
val md_dark_error                   = Color(0xFFFFB4AB)
val md_dark_onError                 = Color(0xFF690005)
val md_dark_errorContainer          = Color(0xFF93000A)
val md_dark_onErrorContainer        = Color(0xFFFFDAD6)
val md_dark_background              = Color(0xFF111418)
val md_dark_onBackground            = Color(0xFFE2E2E6)
val md_dark_surface                 = Color(0xFF111418)
val md_dark_onSurface               = Color(0xFFE2E2E6)
val md_dark_onSurfaceVariant        = Color(0xFFC3C7CF)
val md_dark_outline                 = Color(0xFF8D9199)
val md_dark_outlineVariant          = Color(0xFF43474E)
val md_dark_surfaceContainer        = Color(0xFF1E2227)
val md_dark_surfaceContainerHigh    = Color(0xFF282C31)
val md_dark_surfaceContainerLow     = Color(0xFF191C20)
val md_dark_surfaceContainerLowest  = Color(0xFF0C0F13)
val md_dark_inverseSurface          = Color(0xFFE2E2E6)
val md_dark_inverseOnSurface        = Color(0xFF2F3033)
val md_dark_inversePrimary          = Color(0xFF0061A4)

// ── App-specific non-themeable colors ────────────────────────────────────────
// Top bar always uses brand blue
val TopBarBg        = Color(0xFF0061A4)
val TopBarBgDark    = Color(0xFF00497D)
val OnPrimary       = Color(0xFFFFFFFF)

// DInput card tint (themed separately since it needs a special bg)
val CardBgDInput     = Color(0xFFD1E4FF)   // same as primaryContainer light
val CardBgDInputDark = Color(0xFF002644)

// Status indicators (same in both themes)
val StatusConnected  = Color(0xFF2E7D32)
val StatusConnecting = Color(0xFFF57F17)
val StatusError      = Color(0xFFB71C1C)

// Controller screen (always dark regardless of theme)
val ControllerBg  = Color(0xFF111318)
val BtnPrimary    = Color(0xFF1E2A3A)
val BtnSecondary  = Color(0xFF162032)
val BtnDisconnect = Color(0xFF8B0000)
val StickBase     = Color(0xFF162032)
val StickKnob     = Color(0xFF3D5A80)
val DpadNormal    = Color(0xFF3D5A80)
val DpadPressed   = Color(0xFF9ECAFF)

val BtnA = Color(0xFF1B5E20)
val BtnB = Color(0xFFB71C1C)
val BtnX = Color(0xFF0D47A1)
val BtnY = Color(0xFFF57F17)

// Overlay pill background (controller + editor floating bars)
val OverlayPill       = Color(0x8C000000)  // Black 55%
val OverlayPillLight  = Color(0x73000000)  // Black 45%

// Editor UI accents
val EditorSelected    = Color(0xFFFFEB3B)  // Yellow — selected button border + info text
val EditorSave        = Color(0xFF69F0AE)  // Green — save checkmark
val EditorDelete      = Color(0xFFFF6B6B)  // Red — delete icon

// On-controller text
val ControllerOnBtn   = Color(0xFFFFFFFF)  // White — button labels
val StickLabel        = Color(0x66FFFFFF)  // White 40% — faint L/R label inside stick base
