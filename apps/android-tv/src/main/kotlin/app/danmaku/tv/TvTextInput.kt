package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

@Composable
internal fun TvTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TvCardColor)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .focusable(),
        decorationBox = { innerTextField ->
            if (value.isBlank()) {
                Text(placeholder, color = TvMutedText)
            }
            innerTextField()
        },
    )
}
