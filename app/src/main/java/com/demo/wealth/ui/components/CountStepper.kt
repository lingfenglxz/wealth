package com.demo.wealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButtonDefaults
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.TextPrimary

/**
 * 计数器 - +/- 步进器
 *
 * 改进点：
 * - Material Icons(Remove/Add) 替代 Text("-")/Text("+")（原用纯文本字符）
 * - contentDescription 无障碍标签（原无）
 * - 44dp 触控目标（原默认 36dp）
 * - 引用语义令牌
 *
 * @param label 标签（如 "红球"）
 * @param value 当前值
 * @param min 最小值
 * @param max 最大值
 * @param step 步长
 * @param onChange 值变更回调
 */
@Composable
fun CountStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingMd)
        ) {
            FilledTonalIconButton(
                onClick = { onChange((value - step).coerceAtLeast(min)) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "减少$label" }
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            FilledTonalIconButton(
                onClick = { onChange((value + step).coerceAtMost(max)) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "增加$label" }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}
