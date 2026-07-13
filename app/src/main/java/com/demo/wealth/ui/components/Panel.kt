package com.demo.wealth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.BorderSubtle
import com.demo.wealth.ui.theme.CardPadding
import com.demo.wealth.ui.theme.CardShape
import com.demo.wealth.ui.theme.Elevation1
import com.demo.wealth.ui.theme.Elevation2
import com.demo.wealth.ui.theme.Surface

/**
 * 卡片容器 - 统一替代原 Panel composable
 *
 * 改进点：
 * - 16dp 圆角（原 8dp）+ 微妙阴影（原 0 阴影）
 * - 引用语义令牌（原硬编码 Color.White / Color(0xFFE5EAF0)）
 * - 支持 raised 属性用于强调卡片（如 ActionHeader）
 *
 * @param raised 是否提升层级（悬浮卡片效果）
 * @param content 卡片内容
 */
@Composable
fun Panel(
    raised: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (raised) Elevation2.dp else Elevation1.dp
        ),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(CardPadding), content = content)
    }
}
