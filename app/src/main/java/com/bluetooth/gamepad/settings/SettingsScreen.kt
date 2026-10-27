package com.bluetooth.gamepad.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.motion.MotionMode
import com.bluetooth.gamepad.motion.MotionSensorManager
import com.bluetooth.gamepad.ui.theme.AppTheme
import com.bluetooth.gamepad.util.HapticIntensity
import com.bluetooth.gamepad.util.obtainVibrator

private val themeOptions = listOf(
    Triple(AppTheme.LIGHT, "Light", Icons.Default.LightMode),
    Triple(AppTheme.DARK, "Dark", Icons.Default.DarkMode),
    Triple(AppTheme.AMOLED, "AMOLED", Icons.Default.SmartDisplay),
    Triple(AppTheme.SYSTEM, "System", Icons.Default.SettingsBrightness)
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    appVersion: String,
    onWindowsModeChange: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val cs = MaterialTheme.colorScheme

    Scaffold(containerColor = cs.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTitle()

            AppearanceSection(settings)

            GameplaySection(settings, onWindowsModeChange)

            PlayScreenSection(settings)

            MotionSection(settings)

            ConnectionSection()

            AboutSection(appVersion)
        }
    }
}

@Composable
private fun SettingsTitle() {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            color = cs.onSurface
        )
    }
}

@Composable
private fun AppearanceSection(settings: AppSettings) {
    val cs = MaterialTheme.colorScheme

    SettingsSection("APPEARANCE", innerPadding = 14.dp) {
        Text(
            "Theme",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themeOptions.forEach { (theme, label, icon) ->
                ThemeButton(
                    modifier = Modifier.weight(1f),
                    label = label,
                    icon = icon,
                    selected = settings.appTheme == theme,
                    cs = cs,
                    onClick = { settings.appTheme = theme }
                )
            }
        }
    }
}

@Composable
private fun GameplaySection(settings: AppSettings, onWindowsModeChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme

    SettingsSection("GAMEPLAY") {
        SettingsRowToggle(
            icon = Icons.Default.Games,
            iconBg = cs.secondaryContainer,
            iconFg = cs.onSecondaryContainer,
            title = "Windows DInput mode",
            sub = "Legacy controller compatibility",
            checked = settings.isWindowsMode,
            onCheckedChange = onWindowsModeChange
        )
        Divider(cs.outlineVariant)
        HapticIntensityRow(
            intensity = settings.hapticIntensity,
            onChange = { settings.hapticIntensity = it }
        )
        Divider(cs.outlineVariant)
        SettingsRowToggle(
            icon = Icons.Default.Bluetooth,
            iconBg = cs.primaryContainer,
            iconFg = cs.onPrimaryContainer,
            title = "Auto-reconnect",
            sub = "Resume last device on launch",
            checked = settings.autoReconnect,
            onCheckedChange = { settings.autoReconnect = it }
        )
    }
}

@Composable
private fun PlayScreenSection(settings: AppSettings) {
    val cs = MaterialTheme.colorScheme

    SettingsSection("PLAY SCREEN") {
        SettingsRowToggle(
            icon = Icons.Default.DarkMode,
            iconBg = cs.secondaryContainer,
            iconFg = cs.onSecondaryContainer,
            title = "OLED mode",
            sub = "Black background with dim outline controls",
            checked = settings.oledMode,
            onCheckedChange = { settings.oledMode = it }
        )
        Divider(cs.outlineVariant)
        SettingsRowToggle(
            icon = Icons.Default.Swipe,
            iconBg = cs.tertiaryContainer,
            iconFg = cs.onTertiaryContainer,
            title = "Block edge swipes",
            sub = "Side swipes and Back do not leave the play screen; use the … menu",
            checked = settings.blockEdgeGestures,
            onCheckedChange = { settings.blockEdgeGestures = it }
        )
        Divider(cs.outlineVariant)
        SettingsRowToggle(
            icon = Icons.Default.PushPin,
            iconBg = cs.primaryContainer,
            iconFg = cs.onPrimaryContainer,
            title = "Pin screen while playing",
            sub = "Blocks Home and Recents with Android app pinning",
            checked = settings.pinScreen,
            onCheckedChange = { settings.pinScreen = it }
        )
    }
}

@Composable
private fun MotionSection(settings: AppSettings) {
    val cs = MaterialTheme.colorScheme

    SettingsSection("MOTION") {
        SettingsRowToggle(
            icon = Icons.Default.Sensors,
            iconBg = cs.tertiaryContainer,
            iconFg = cs.onTertiaryContainer,
            title = "Motion controls",
            sub = "Gyroscope maps to right stick",
            checked = settings.motionEnabled,
            onCheckedChange = { settings.motionEnabled = it }
        )
        if (settings.motionEnabled) {
            Divider(cs.outlineVariant)
            MotionModeRow(
                mode = settings.motionMode,
                onChange = { settings.motionMode = it }
            )
            Divider(cs.outlineVariant)
            MotionSensitivityRow(
                mode = settings.motionMode,
                sensitivity = settings.motionSensitivity,
                onChange = { settings.motionSensitivity = it }
            )
            Divider(cs.outlineVariant)
            SettingsRowToggle(
                icon = Icons.Default.SwapHoriz,
                iconBg = cs.tertiaryContainer,
                iconFg = cs.onTertiaryContainer,
                title = "Invert X",
                sub = "Flip horizontal motion",
                checked = settings.motionInvertX,
                onCheckedChange = { settings.motionInvertX = it }
            )
            // Vertical inversion is meaningless for steering, which only drives the X axis.
            if (settings.motionMode == MotionMode.AIM) {
                Divider(cs.outlineVariant)
                SettingsRowToggle(
                    icon = Icons.Default.SwapVert,
                    iconBg = cs.tertiaryContainer,
                    iconFg = cs.onTertiaryContainer,
                    title = "Invert Y",
                    sub = "Flip vertical motion",
                    checked = settings.motionInvertY,
                    onCheckedChange = { settings.motionInvertY = it }
                )
            }
        }
    }
}

@Composable
private fun ConnectionSection() {
    val cs = MaterialTheme.colorScheme

    SettingsSection("CONNECTION") {
        SettingsRowInfo(
            icon = Icons.Default.Phonelink,
            iconBg = cs.primaryContainer,
            iconFg = cs.onPrimaryContainer,
            title = "HID profile",
            sub = "Gamepad over Bluetooth",
            trailingText = "Active",
            trailingColor = cs.primary
        )
    }
}

@Composable
private fun AboutSection(appVersion: String) {
    val cs = MaterialTheme.colorScheme

    SettingsSection("ABOUT", bottomGap = 16.dp) {
        SettingsRowInfo(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            iconBg = cs.surfaceContainerHigh,
            iconFg = cs.onSurface,
            title = "Help & feedback",
            sub = "Docs, diagnostics, report an issue"
        )
        Divider(cs.outlineVariant)
        SettingsRowInfo(
            icon = Icons.Default.Info,
            iconBg = cs.surfaceContainerHigh,
            iconFg = cs.onSurface,
            title = "Version",
            sub = null,
            trailingText = appVersion,
            trailingColor = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    bottomGap: Dp = 24.dp,
    innerPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionLabel(title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(innerPadding),
        content = content
    )
    Spacer(Modifier.height(bottomGap))
}

private fun Modifier.settingsRowPadding(): Modifier = padding(horizontal = 16.dp, vertical = 14.dp)

@Composable
private fun IconBadge(icon: ImageVector, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(bg, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRowHeader(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    sub: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon, iconBg, iconFg)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) {
                Text(sub, fontSize = 12.sp, color = subColor, modifier = Modifier.padding(top = 1.dp))
            }
        }
        trailing?.invoke()
    }
}

private class ChipOption<T>(val value: T, val label: String, val enabled: Boolean = true)

@Composable
private fun <T> ChoiceChips(options: List<ChipOption<T>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val active = option.value == selected && option.enabled
            Surface(
                onClick = { if (option.enabled) onSelect(option.value) },
                enabled = option.enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(999.dp),
                color = if (active) cs.primary else cs.surfaceContainerHigh,
                border = if (active) null else BorderStroke(1.dp, cs.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) cs.onPrimary else cs.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun HapticIntensityRow(
    intensity: HapticIntensity,
    onChange: (HapticIntensity) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val hasVibrator = remember { obtainVibrator(context).hasVibrator() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (hasVibrator) 1f else 0.4f)
            .settingsRowPadding()
    ) {
        SettingsRowHeader(
            icon = Icons.Default.Vibration,
            iconBg = cs.tertiaryContainer,
            iconFg = cs.onTertiaryContainer,
            title = "Haptic feedback",
            sub = if (hasVibrator) "Button press vibration" else "Not supported on this device",
            subColor = if (hasVibrator) cs.onSurfaceVariant else cs.error
        )
        ChoiceChips(
            options = listOf(
                ChipOption(HapticIntensity.OFF, "Off", hasVibrator),
                ChipOption(HapticIntensity.LIGHT, "Light", hasVibrator),
                ChipOption(HapticIntensity.MEDIUM, "Medium", hasVibrator),
                ChipOption(HapticIntensity.STRONG, "Strong", hasVibrator)
            ),
            selected = intensity,
            onSelect = onChange
        )
    }
}

@Composable
private fun ThemeButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    cs: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = if (selected)
            BorderStroke(2.dp, cs.primary)
        else
            BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) cs.onPrimaryContainer else cs.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) cs.onPrimaryContainer else cs.onSurface
            )
        }
    }
}

@Composable
private fun SettingsRowToggle(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    sub: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    SettingsRowHeader(
        icon = icon,
        iconBg = iconBg,
        iconFg = iconFg,
        title = title,
        sub = sub,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .settingsRowPadding(),
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
    )
}

@Composable
private fun SettingsRowInfo(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    sub: String?,
    trailingText: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    SettingsRowHeader(
        icon = icon,
        iconBg = iconBg,
        iconFg = iconFg,
        title = title,
        sub = sub,
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowPadding(),
        trailing = if (trailingText != null) {
            { Text(trailingText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = trailingColor) }
        } else null
    )
}

@Composable
private fun Divider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(color)
    )
}

@Composable
private fun MotionSensitivityRow(
    mode: MotionMode,
    sensitivity: Float,
    onChange: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // Sensitivity is deg/s for full stick deflection (30..180, lower = faster).
    // Slider is inverted so dragging right feels faster.
    val min = MotionSensorManager.SENS_MIN_DPS
    val max = MotionSensorManager.SENS_MAX_DPS
    val sliderValue = 1f - (sensitivity.coerceIn(min, max) - min) / (max - min)

    Column(modifier = Modifier.fillMaxWidth().settingsRowPadding()) {
        SettingsRowHeader(
            icon = Icons.Default.Sensors,
            iconBg = cs.tertiaryContainer,
            iconFg = cs.onTertiaryContainer,
            title = "Sensitivity",
            sub = when {
                mode == MotionMode.STEERING -> "Full lock at ${(32f * sensitivity / 90f).toInt()} deg tilt"
                else -> "Full deflection at ${sensitivity.toInt()} deg/s"
            }
        )
        Slider(
            value = sliderValue,
            onValueChange = { v -> onChange(max - v * (max - min)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Low", fontSize = 10.sp, color = cs.onSurfaceVariant)
            Text("High", fontSize = 10.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun MotionModeRow(
    mode: MotionMode,
    onChange: (MotionMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val manager = remember { MotionSensorManager(context) }

    // Each mode rides a different sensor, so one can be missing while the other works.
    val options = listOf(MotionMode.AIM to "Aim", MotionMode.STEERING to "Steering").map { (value, label) ->
        val available = manager.isModeAvailable(value)
        ChipOption(value, if (available) label else "$label (n/a)", available)
    }

    Column(modifier = Modifier.fillMaxWidth().settingsRowPadding()) {
        SettingsRowHeader(
            icon = Icons.Default.ScreenRotation,
            iconBg = cs.tertiaryContainer,
            iconFg = cs.onTertiaryContainer,
            title = "Motion mode",
            sub = if (mode == MotionMode.STEERING) "Tilt the phone like a wheel"
            else "Aim follows how fast you turn; stops when you stop"
        )
        ChoiceChips(options = options, selected = mode, onSelect = onChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
    )
}
