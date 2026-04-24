package com.bluetoothpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetoothpad.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    appTheme: AppTheme,
    appVersion: String,
    isWindowsMode: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onWindowsModeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val cs = MaterialTheme.colorScheme
    val autoReconnect = remember { mutableStateOf(true) }

    Scaffold(containerColor = cs.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // Inline header
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

            // APPEARANCE section
            SectionLabel("APPEARANCE")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerLow)
                    .padding(14.dp)
            ) {
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
                    ThemeButton(
                        modifier = Modifier.weight(1f),
                        label = "Light",
                        icon = Icons.Default.LightMode,
                        selected = appTheme == AppTheme.LIGHT,
                        cs = cs,
                        onClick = { onThemeChange(AppTheme.LIGHT) }
                    )
                    ThemeButton(
                        modifier = Modifier.weight(1f),
                        label = "Dark",
                        icon = Icons.Default.DarkMode,
                        selected = appTheme == AppTheme.DARK,
                        cs = cs,
                        onClick = { onThemeChange(AppTheme.DARK) }
                    )
                    ThemeButton(
                        modifier = Modifier.weight(1f),
                        label = "AMOLED",
                        icon = Icons.Default.SmartDisplay,
                        selected = appTheme == AppTheme.AMOLED,
                        cs = cs,
                        onClick = { onThemeChange(AppTheme.AMOLED) }
                    )
                    ThemeButton(
                        modifier = Modifier.weight(1f),
                        label = "System",
                        icon = Icons.Default.SettingsBrightness,
                        selected = appTheme == AppTheme.SYSTEM,
                        cs = cs,
                        onClick = { onThemeChange(AppTheme.SYSTEM) }
                    )
                }
                if (appTheme == AppTheme.AMOLED) {
                    Text(
                        "True Black saves battery on OLED screens.",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // GAMEPLAY section
            SectionLabel("GAMEPLAY")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerLow)
            ) {
                SettingsRowToggle(
                    icon = Icons.Default.Games,
                    iconBg = cs.secondaryContainer,
                    iconFg = cs.onSecondaryContainer,
                    title = "Windows DInput mode",
                    sub = "Legacy controller compatibility",
                    checked = isWindowsMode,
                    onCheckedChange = onWindowsModeToggle
                )
                Divider(cs.outlineVariant)
                SettingsRowToggle(
                    icon = Icons.Default.Bluetooth,
                    iconBg = cs.primaryContainer,
                    iconFg = cs.onPrimaryContainer,
                    title = "Auto-reconnect",
                    sub = "Resume last device on launch",
                    checked = autoReconnect.value,
                    onCheckedChange = { autoReconnect.value = it }
                )
            }

            Spacer(Modifier.height(24.dp))

            // CONNECTION section
            SectionLabel("CONNECTION")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerLow)
            ) {
                SettingsRowInfo(
                    icon = Icons.Default.Phonelink,
                    iconBg = cs.primaryContainer,
                    iconFg = cs.onPrimaryContainer,
                    title = "HID profile",
                    sub = "Keyboard + gamepad over BT",
                    trailingText = "Active",
                    trailingColor = cs.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            // ABOUT section
            SectionLabel("ABOUT")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerLow)
            ) {
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    cs: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = if (selected)
            androidx.compose.foundation.BorderStroke(2.dp, cs.primary)
        else
            androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant)
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
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            if (sub != null) {
                Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            if (sub != null) {
                Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (trailingText != null) {
            Text(trailingText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = trailingColor)
        }
    }
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
