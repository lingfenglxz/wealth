package com.demo.wealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.BorderSubtle
import com.demo.wealth.ui.theme.Error
import com.demo.wealth.ui.theme.OnSuccessContainer
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.ShapeLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.Success
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 连接状态指示器 - 替代首页 StatusCard
 *
 * 改进点：
 * - 从占满卡片的状态提示 -> 紧凑的行内指示器
 * - 三态：已连接 / 同步中 / 离线
 * - 引用语义令牌
 *
 * @param status 连接状态
 * @param detail 详情文字（如 "3120 期"）
 */
@Composable
fun StatusIndicator(
    status: ConnectionStatus,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, icon, textColor) = when (status) {
        ConnectionStatus.CONNECTED -> Quad(PrimaryContainer, Success, Icons.Default.Check, OnSuccessContainer)
        ConnectionStatus.SYNCING -> Quad(PrimaryContainer, Success, Icons.Default.Sync, OnSuccessContainer)
        ConnectionStatus.OFFLINE -> Quad(Error.copy(alpha = 0.08f), Error, Icons.Default.CloudOff, Error)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingSm),
        modifier = modifier
            .background(bgColor, ShapeLg)
            .border(1.dp, BorderSubtle, ShapeLg)
            .padding(horizontal = SpacingMd, vertical = SpacingSm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        if (detail != null) {
            Text(
                text = "· $detail",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

enum class ConnectionStatus(val label: String) {
    CONNECTED("已同步"),
    SYNCING("同步中"),
    OFFLINE("未连接")
}

private data class Quad(
    val bgColor: Color,
    val iconColor: Color,
    val icon: ImageVector,
    val textColor: Color
)
