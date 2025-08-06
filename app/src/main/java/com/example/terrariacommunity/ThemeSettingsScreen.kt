package com.example.terrariacommunity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.terrariacommunity.ui.theme.NotoSansKR
import com.example.terrariacommunity.ui.theme.DefaultFontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    currentThemeMode: ThemeMode,
    currentPrimaryColor: Color,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPrimaryColorChange: (Color) -> Unit,
    onFontFamilyChange: (FontFamily) -> Unit, // 추가
    currentFontFamily: FontFamily, // 추가
    onBack: () -> Unit
) {
    val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val colorPresets = listOf(
        Color(0xFF6650a4), // Purple
        Color(0xFF00796B), // Teal
        Color(0xFF1976D2), // Blue
        Color(0xFFD32F2F), // Red
        Color(0xFFFBC02D), // Yellow
        Color(0xFF388E3C), // Green
        Color(0xFF7D5260)  // Pink
    )
    val fontOptions = listOf(
        "기본 폰트" to DefaultFontFamily,
        "NotoSansKR" to NotoSansKR
    )
    var fontDropdownExpanded by remember { mutableStateOf(false) }
    var selectedFontName by remember { mutableStateOf(fontOptions.find { it.second == currentFontFamily }?.first ?: "기본 폰트") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("테마 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<-") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("모드 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                themeModes.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { onThemeModeChange(mode) }
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { onThemeModeChange(mode) }
                        )
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "시스템"
                                ThemeMode.LIGHT -> "라이트"
                                ThemeMode.DARK -> "다크"
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("주요 색상 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                colorPresets.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color)
                            .clickable { onPrimaryColorChange(color) }
                            .then(
                                if (currentPrimaryColor == color) Modifier.border(2.dp, Color.Black) else Modifier
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("폰트 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { fontDropdownExpanded = true }) {
                    Text(selectedFontName)
                }
                DropdownMenu(
                    expanded = fontDropdownExpanded,
                    onDismissRequest = { fontDropdownExpanded = false }
                ) {
                    fontOptions.forEach { (name, family) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedFontName = name
                                fontDropdownExpanded = false
                                onFontFamilyChange(family)
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK } 