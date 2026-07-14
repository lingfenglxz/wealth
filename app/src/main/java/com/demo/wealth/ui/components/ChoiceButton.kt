package com.demo.wealth.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.BorderDefault
import com.demo.wealth.ui.theme.ButtonShape
import com.demo.wealth.ui.theme.OnPrimary
import com.demo.wealth.ui.theme.Primary
import com.demo.wealth.ui.theme.TextPrimary
import com.demo.wealth.ui.theme.TouchTargetMin
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 选择按钮 - 切换态按钮
 *
 * 改进点：
 * - minHeight 40dp（原 ~36dp 默认 Button 高度）
 * - 引用语义令牌（原硬编码 0xFF0F6B55 / 0xFF26323A / 0xFFDDE4EA）
 * - 统一替代原 ChoiceButton composable
 *
 * @param text 按钮文字
 * @param selected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun ChoiceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary
            ),
            modifier = modifier.defaultMinSize(minHeight = TouchTargetMin)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            ),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderDefault),
            modifier = modifier.defaultMinSize(minHeight = TouchTargetMin)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}
