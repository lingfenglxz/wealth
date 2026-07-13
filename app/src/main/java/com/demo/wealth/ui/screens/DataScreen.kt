package com.demo.wealth.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.components.StatusIndicator
import com.demo.wealth.ui.theme.ButtonShape
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 数据页 - 服务端配置与备份
 *
 * 布局优化：
 * - 移除 StatusCard，合并为连接状态指示器
 * - 合并"双色球数据"+"体彩足球数据"为单个"数据同步"卡，内部分 section
 * - 更新按钮改为全宽 btn-lg(52dp)
 * - 移除多余 Row+horizontalScroll 包裹
 */
@Composable
fun DataScreen(
    lotteryServerUrl: String,
    onLotteryServerUrlChange: (String) -> Unit,
    onLotteryServerUpdate: () -> Unit,
    onFootballServerUpdate: () -> Unit,
    onBackupExport: (android.net.Uri?) -> Unit,
    onBackupRestore: (android.net.Uri?) -> Unit,
    lotteryDrawsCount: Int,
    footballMatchesCount: Int,
    isLotteryUpdating: Boolean,
    isFootballUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { onBackupExport(it) }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { onBackupRestore(it) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // 合并的数据同步卡
        item {
            DataSyncCard(
                lotteryServerUrl = lotteryServerUrl,
                onLotteryServerUrlChange = onLotteryServerUrlChange,
                onLotteryServerUpdate = onLotteryServerUpdate,
                onFootballServerUpdate = onFootballServerUpdate,
                lotteryDrawsCount = lotteryDrawsCount,
                footballMatchesCount = footballMatchesCount,
                isLotteryUpdating = isLotteryUpdating,
                isFootballUpdating = isFootballUpdating
            )
        }

        // 备份与恢复
        item {
            Panel {
                Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(SpacingSm))
                Text(
                    "导出当前所有数据到 JSON 文件，或从备份恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(SpacingLg))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingSm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { backupExportLauncher.launch("wealthlab-backup.json") },
                        shape = ButtonShape,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = "导出备份")
                        Spacer(Modifier.width(SpacingSm))
                        Text("导出")
                    }
                    OutlinedButton(
                        onClick = {
                            backupRestoreLauncher.launch(
                                arrayOf("application/json", "text/*", "application/octet-stream")
                            )
                        },
                        shape = ButtonShape,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "恢复备份")
                        Spacer(Modifier.width(SpacingSm))
                        Text("恢复")
                    }
                }
            }
        }
    }
}

/**
 * 数据同步卡 - 合并原"双色球数据"和"体彩足球数据"
 */
@Composable
private fun DataSyncCard(
    lotteryServerUrl: String,
    onLotteryServerUrlChange: (String) -> Unit,
    onLotteryServerUpdate: () -> Unit,
    onFootballServerUpdate: () -> Unit,
    lotteryDrawsCount: Int,
    footballMatchesCount: Int,
    isLotteryUpdating: Boolean,
    isFootballUpdating: Boolean
) {
    Panel {
        Text("数据同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingMd))

        // 连接状态指示器
        StatusIndicator(
            status = com.demo.wealth.ui.components.ConnectionStatus.CONNECTED,
            detail = "双色球 $lotteryDrawsCount 期 · 足球 $footballMatchesCount 场"
        )
        Spacer(Modifier.height(SpacingLg))

        // 服务端地址
        Text(
            "服务端地址",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        Spacer(Modifier.height(SpacingSm))
        OutlinedTextField(
            value = lotteryServerUrl,
            onValueChange = onLotteryServerUrlChange,
            label = { Text("如 http://192.168.1.8:8000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 双色球 section
        Spacer(Modifier.height(SpacingLg))
        DataSectionDivider()
        Spacer(Modifier.height(SpacingMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("双色球", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "$lotteryDrawsCount 期",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(SpacingSm))
        // 全宽 btn-lg 更新按钮
        Button(
            onClick = onLotteryServerUpdate,
            shape = ButtonShape,
            enabled = !isLotteryUpdating,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isLotteryUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Spacer(Modifier.width(SpacingSm))
                Text("更新中...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "更新双色球数据")
                Spacer(Modifier.width(SpacingSm))
                Text("更新双色球数据")
            }
        }

        // 体彩 section
        Spacer(Modifier.height(SpacingLg))
        DataSectionDivider()
        Spacer(Modifier.height(SpacingMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("体彩足球", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "$footballMatchesCount 场",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(SpacingSm))
        Button(
            onClick = onFootballServerUpdate,
            shape = ButtonShape,
            enabled = !isFootballUpdating,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isFootballUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Spacer(Modifier.width(SpacingSm))
                Text("更新中...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "更新体彩足球数据")
                Spacer(Modifier.width(SpacingSm))
                Text("更新体彩足球数据")
            }
        }
    }
}

@Composable
private fun DataSectionDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outline
    )
}
