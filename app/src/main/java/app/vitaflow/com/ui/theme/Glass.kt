package app.vitaflow.com.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The plain dark (or light) page behind everything. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(modifier.fillMaxSize().background(g.bgTop), content = content)
}

/** A card: slightly lighter than the page, with a thin edge. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(g.glassFill)
            .border(1.dp, Brush.verticalGradient(listOf(g.borderTop, g.borderBottom)), shape),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    val fill: Brush = when {
        !enabled -> Brush.horizontalGradient(listOf(g.glassFill, g.glassFill))
        danger -> Brush.horizontalGradient(listOf(g.danger.copy(alpha = 0.16f), g.danger.copy(alpha = 0.16f)))
        else -> Brush.horizontalGradient(listOf(g.accent, g.accent2))
    }
    val textColor = when {
        !enabled -> g.textSecondary
        danger -> g.danger
        else -> g.onAccent
    }
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (danger || !enabled) Modifier.border(1.dp, if (danger) g.danger.copy(alpha = 0.45f) else g.borderBottom, shape)
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small selectable pill. Selected = green edge and tint. */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) g.accent.copy(alpha = 0.16f) else g.glassFill)
            .border(1.dp, if (selected) g.accent else g.borderBottom, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) g.accent else g.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = g.glassFill,
            unfocusedContainerColor = g.glassFill,
            disabledContainerColor = g.glassFill,
            errorContainerColor = g.glassFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = g.textPrimary,
            unfocusedTextColor = g.textPrimary,
            focusedLabelColor = g.accent,
            unfocusedLabelColor = g.textSecondary,
            cursorColor = g.accent
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, g.borderBottom, shape)
    )
}
