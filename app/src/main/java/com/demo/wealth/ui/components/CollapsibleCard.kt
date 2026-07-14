package com.demo.wealth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.CardPadding
import com.demo.wealth.ui.theme.CardShape
import com.demo.wealth.ui.theme.BorderSubtle
import com.demo.wealth.ui.theme.SpacingXs
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.Surface
import com.demo.wealth.ui.theme.TextPrimary
import com.demo.wealth.ui.theme.TextSecondary
import com.demo.wealth.ui.theme.TouchTargetMin
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.demo.wealth.ui.theme.enterTransitionSpec
import com.demo.wealth.ui.theme.exitTransitionSpec

/**
 * 可折叠卡片 - 标题行 ▾ 图标 + 动画展开
 *
 * 改进点：
 * - 标题行右侧 ▾ 图标替代独立"展开/收起"按钮（原用 OutlinedButton，视觉噪音大）
 * - 整行可点击展开
 * - AnimatedVisibility 250ms 动画（原瞬间跳变）
 * - rememberSaveable 保持展开状态（原 remember，LazyColumn 回收后丢失）
 * - 引用语义令牌
 *
 * @param title 卡片标题
 * @param subtitle 右侧摘要文字（如 "+12.40% ROI"）
 * @param defaultExpanded 默认是否展开
 * @param content 展开后的内容
 */
@Composable
fun CollapsibleCard(
    title: String,
    subtitle: String? = null,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(CardPadding)) {
            // 标题行 - 整行可点击
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .defaultMinSize(minHeight = TouchTargetMin)
                    .padding(vertical = SpacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingSm)
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                        tint = TextSecondary
                    )
                }
            }
            // 展开内容 - 动画过渡
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = enterTransitionSpec()) + fadeIn(),
                exit = shrinkVertically(animationSpec = exitTransitionSpec()) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = SpacingMd),
                    content = content
                )
            }
        }
    }
}
