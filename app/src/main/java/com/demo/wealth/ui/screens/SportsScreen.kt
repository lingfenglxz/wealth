package com.demo.wealth.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.demo.wealth.data.FootballMatchEntity
import com.demo.wealth.data.FootballRecommendationEntity
import com.demo.wealth.domain.sports.FootballDisplayNames
import com.demo.wealth.domain.sports.FootballPlayTypes
import com.demo.wealth.domain.sports.FootballScheduleUi
import com.demo.wealth.ui.components.BallSmall
import com.demo.wealth.ui.components.CollapsibleCard
import com.demo.wealth.ui.components.GroupLabel
import com.demo.wealth.ui.components.MetaPill
import com.demo.wealth.ui.components.MetricRow
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.percent
import com.demo.wealth.ui.signedPercent
import com.demo.wealth.ui.theme.ErrorContainer
import com.demo.wealth.ui.theme.InfoContainer
import com.demo.wealth.ui.theme.OnErrorContainer
import com.demo.wealth.ui.theme.OnInfoContainer
import com.demo.wealth.ui.theme.OnPrimaryContainer
import com.demo.wealth.ui.theme.Primary
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.PrimaryHover
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.Success
import com.demo.wealth.ui.theme.TextSecondary
import com.demo.wealth.ui.theme.SurfaceVariant

/**
 * 体彩页 - 足球彩票
 *
 * 布局优化：
 * - 移除单按钮 ChoiceButton 行（原空操作）
 * - 取消三层嵌套展开：赛事卡直接显示赔率三栏 + 推荐摘要
 * - FootballRecommendationInline 内容直接内联到赛事卡
 */
@Composable
fun SportsScreen(
    matches: List<FootballMatchEntity>,
    recommendations: List<FootballRecommendationEntity>,
    recommendationHistory: List<FootballRecommendationEntity>,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recommendationsByMatch = recommendations.groupBy { it.matchId }
    val recommendationHistoryByMatch = recommendationHistory.groupBy { it.matchId }
    val historicalMatches = matches.filter { FootballScheduleUi.isHistoricalKickoff(it.kickoffTime) }
    val currentMatches = matches.filterNot { FootballScheduleUi.isHistoricalKickoff(it.kickoffTime) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // ActionHeader
        item {
            Panel(raised = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        MetaPill("研究台", PrimaryContainer, PrimaryHover)
                        Spacer(Modifier.height(SpacingSm))
                        Text(
                            "足球彩票",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "当前赛事 ${currentMatches.size} 场 · 历史 ${historicalMatches.size} 场",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Button(onClick = onGenerate) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(SpacingSm))
                        Text("生成推荐")
                    }
                }
            }
        }

        // 支持玩法
        item { FootballPlayTypeCard() }

        // 当前赛事分组标题
        item { GroupLabel("当前赛事") }

        if (currentMatches.isEmpty()) {
            item {
                Panel {
                    Text("暂无足球赛事", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "到“数据”页配置服务端地址后更新；已结束场次会进入历史比赛。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = SpacingSm)
                    )
                }
            }
        } else {
            items(currentMatches.take(40), key = { it.matchId }) { match ->
                FootballMatchCardFlat(match, recommendationsByMatch[match.matchId].orEmpty())
            }
        }

        // 历史比赛
        if (historicalMatches.isNotEmpty()) {
            item {
                CollapsibleCard(
                    title = "历史比赛",
                    subtitle = "${historicalMatches.size} 场"
                ) {
                    historicalMatches.take(30).forEach { match ->
                        val matchRecs = recommendationHistoryByMatch[match.matchId].orEmpty()
                        Text(
                            "${FootballDisplayNames.team(match.homeTeam)} vs ${FootballDisplayNames.team(match.awayTeam)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "开赛 ${match.kickoffTime} · 推荐 ${matchRecs.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        matchRecs.take(5).forEach { item ->
                            Text(
                                "${item.playName} ${FootballPlayTypes.selectionName(item.playType, item.selection)} · 置信分 ${percent(item.confidence)} · 理论价值 ${signedPercent(item.edge)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.height(SpacingMd))
                    }
                }
            }
        }
    }
}

/**
 * 赛事卡 - 扁平化
 * 直接显示赔率三栏 + 推荐摘要，取消原 FootballRecommendationInline 嵌套展开
 */
@Composable
private fun FootballMatchCardFlat(
    match: FootballMatchEntity,
    recommendations: List<FootballRecommendationEntity>
) {
    Panel {
        // 赛事标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${FootballDisplayNames.team(match.homeTeam)} vs ${FootballDisplayNames.team(match.awayTeam)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(SpacingSm))
                Text(
                    "${FootballDisplayNames.venue(match.leagueName)} · ${FootballDisplayNames.phase(match.phase)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    "开赛 ${match.kickoffTime} · 让球 ${match.handicap}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (match.stadium.isNotBlank() || match.city.isNotBlank()) {
                    Text(
                        listOf(match.stadium, match.city).filter { it.isNotBlank() }
                            .joinToString(" · ") { FootballDisplayNames.venue(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            if (recommendations.isNotEmpty()) {
                MetaPill("推荐 ${recommendations.size}", ErrorContainer, OnErrorContainer)
            }
        }

        // 玩法标签
        Spacer(Modifier.height(SpacingMd))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            val availablePools = FootballPlayTypes.allCodes.filter { match.poolsJson.contains("\"$it\"") }
            if (availablePools.isEmpty()) {
                MetaPill("暂无赔率", SurfaceVariant, TextSecondary)
            }
            availablePools.forEach { code ->
                MetaPill(FootballPlayTypes.displayName(code), PrimaryContainer, PrimaryHover)
            }
        }

        // 推荐摘要（扁平化，直接展示，不再嵌套展开）
        if (recommendations.isNotEmpty()) {
            Spacer(Modifier.height(SpacingMd))
            val sorted = recommendations.sortedWith(
                compareBy<FootballRecommendationEntity> { it.playType }.thenByDescending { it.confidence }
            )
            sorted.take(3).forEach { rec ->
                RecommendationSummary(rec)
                Spacer(Modifier.height(SpacingSm))
            }
        }
    }
}

/**
 * 推荐摘要 - 扁平展示，取消原 FootballRecommendationInline 的嵌套展开
 */
@Composable
private fun RecommendationSummary(item: FootballRecommendationEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SpacingMd)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            MetaPill(item.playName, InfoContainer, OnInfoContainer)
            MetaPill(
                FootballPlayTypes.selectionName(item.playType, item.selection),
                PrimaryContainer,
                OnPrimaryContainer
            )
            if (item.modelName.isNotBlank()) {
                MetaPill(item.modelName, ErrorContainer, OnErrorContainer)
            }
        }
        Spacer(Modifier.height(SpacingSm))
        MetricRow(
            "置信分", percent(item.confidence),
            "参考赔率", if (item.odds > 0.0) "%.2f".format(item.odds) else "-"
        )
        Spacer(Modifier.height(SpacingSm))
        MetricRow(
            "模型概率", percent(item.modelProbability),
            "理论价值", signedPercent(item.edge),
            rightTrend = if (item.edge >= 0) Success else null
        )
    }
}

@Composable
private fun FootballPlayTypeCard() {
    Panel {
        Text("支持玩法", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            FootballPlayTypes.allCodes.forEach { code ->
                MetaPill(
                    FootballPlayTypes.displayName(code),
                    InfoContainer,
                    OnInfoContainer
                )
            }
        }
        Spacer(Modifier.height(SpacingSm))
        Text(
            "首版只做赛程、赔率字段和实验性推荐，不做投注单、串关计算或购彩功能。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
