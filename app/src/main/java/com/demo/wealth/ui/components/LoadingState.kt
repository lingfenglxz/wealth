package com.demo.wealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 加载状态 - 居中 spinner
 *
 * 改进点：
 * - 新增组件（原无加载状态）
 * - 用于网络请求、数据生成等异步操作
 *
 * @param message 加载提示文字
 */
@Composable
fun LoadingState(
    message: String = "加载中...",
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(SpacingLg)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = SpacingMd)
        )
    }
}

/**
 * 错误状态 - 图标 + 消息 + 重试按钮
 *
 * 改进点：
 * - 新增组件（原无错误状态，仅更新字符串）
 * - 含重试按钮，符合 error-recovery 规则
 *
 * @param message 错误消息
 * @param onRetry 重试回调
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Refresh
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(SpacingLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = SpacingMd)
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = SpacingSm)) {
            Text("重试", fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 空状态 - 图标 + 标题 + 描述
 *
 * 改进点：
 * - 统一替代原各处散落的空状态 Panel
 * - 含图标 + 引导文字，符合 empty-states 规则
 *
 * @param title 空状态标题
 * @param description 引导描述
 * @param icon 图标
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Refresh
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(SpacingLg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = SpacingMd)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = SpacingSm)
        )
    }
}
