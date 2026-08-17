package by.w6.my1drive.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import by.w6.my1drive.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsSection(
    modifier: Modifier = Modifier
) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguage = if (currentLocales.isEmpty) "" else currentLocales[0]?.language ?: ""

    val languageOptions = listOf(
        "" to R.string.settings_lang_system,
        "en" to R.string.settings_lang_en,
        "ru" to R.string.settings_lang_ru,
        "es" to R.string.settings_lang_es
    )

    val currentSelectedOption = languageOptions.find { it.first == currentLanguage } ?: languageOptions[0]
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(currentSelectedOption.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stringResource(currentSelectedOption.second),
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(option.second),
                                    fontWeight = if (option.first == currentLanguage) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                expanded = false
                                if (option.first != currentLanguage) {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (option.first.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                        else LocaleListCompat.forLanguageTags(option.first)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
