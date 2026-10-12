package com.lovelydays.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lovelydays.R
import com.lovelydays.domain.ForecastBuilder
import com.lovelydays.domain.Russian
import java.time.LocalDate

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_moon),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun InsetScreen(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ScreenColumn(Modifier.align(Alignment.TopCenter), content)
    }
}

@Composable
fun EmbeddedScreen(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ScreenColumn(Modifier.align(Alignment.TopCenter), content)
    }
}

@Composable
fun LoadingPane() {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandMark()
            Text(
                "Открываем ${stringResource(R.string.app_name)}…",
                style = MaterialTheme.typography.titleMedium,
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun NoteCard(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
    )
}

@Composable
fun Disclaimer() {
    Text(
        text = ForecastBuilder.DISCLAIMER,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun InsightCard(title: String, body: String, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
        ),
    ) {
        Row {
            Box(
                Modifier
                    .padding(vertical = 16.dp)
                    .padding(start = 14.dp)
                    .size(width = 4.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
            Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    placeholder: String,
    error: String?,
    initial: LocalDate,
    yearRange: IntRange,
    onChange: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value?.let(Russian::full).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true },
        )
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: initial).toEpochDay() * 86_400_000L,
            yearRange = yearRange,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            onChange(LocalDate.ofEpochDay(millis / 86_400_000L))
                        }
                        open = false
                    },
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun QuietTitle(text: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
