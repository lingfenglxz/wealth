package com.demo.wealth.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.demo.wealth.ui.components.CollapsibleCard
import com.demo.wealth.ui.components.ErrorState
import com.demo.wealth.ui.components.GroupLabel
import com.demo.wealth.ui.components.MetaPill
import com.demo.wealth.ui.components.MetricCard
import com.demo.wealth.ui.components.MetricRow
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.percent
import com.demo.wealth.ui.latestRecommendationRun
import com.demo.wealth.ui.nextVisibleCount
import com.demo.wealth.ui.parseHadOdds
import com.demo.wealth.ui.signedPercent
import com.demo.wealth.ui.theme.ErrorContainer
import com.demo.wealth.ui.theme.ButtonHeightLarge
import com.demo.wealth.ui.theme.InfoContainer
import com.demo.wealth.ui.theme.OnErrorContainer
import com.demo.wealth.ui.theme.OnInfoContainer
import com.demo.wealth.ui.theme.OnPrimaryContainer
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
    isGenerating: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val recommendationsByMatch = recommendations.groupBy { it.matchId }
    val recommendationHistoryByMatch = recommendationHistory.groupBy { it.matchId }
    val historicalMatches = matches.filter { FootballScheduleUi.isHistoricalKickoff(it.kickoffTime) }
    val currentMatches = matches.filterNot { FootballScheduleUi.isHistoricalKickoff(it.kickoffTime) }
    var visibleCurrentCount by rememberSaveable { mutableIntStateOf(40) }
    var visibleHistoricalCount by rememberSaveable { mutableIntStateOf(30) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // ActionHeader
        item {
            Panel(raised = true) {
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
                Spacer(Modifier.height(SpacingMd))
                Button(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth().height(ButtonHeightLarge)
                ) {
                    if (isGenerating) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(SpacingSm))
                    Text(if (isGenerating) "生成中..." else "生成推荐")
                }
            }
        }

        errorMessage?.let { message -> item { ErrorState(message, onRetry = onGenerate) } }

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
            items(currentMatches.take(visibleCurrentCount), key = { it.matchId }) { match ->
                FootballMatchCardFlat(match, recommendationsByMatch[match.matchId].orEmpty())
            }
            if (currentMatches.size > visibleCurrentCount) {
                item {
                    TextButton(
                        onClick = { visibleCurrentCount = nextVisibleCount(visibleCurrentCount, currentMatches.size, 40) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更多（${currentMatches.size - visibleCurrentCount} 场）") }
                }
            }
        }

        // 历史比赛
        if (historicalMatches.isNotEmpty()) {
            item {
                CollapsibleCard(
                    title = "历史比赛",
                    subtitle = "${historicalMatches.size} 场"
                ) {
                    historicalMatches.take(visibleHistoricalCount).forEach { match ->
                        val matchRecs = latestRecommendationRun(
                            recommendationHistoryByMatch[match.matchId].orEmpty()
                        )
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
                        matchRecs.forEach { item ->
                            Text(
                                "${item.playName} ${FootballPlayTypes.selectionName(item.playType, item.selection)} · 置信分 ${percent(item.confidence)} · 理论价值 ${signedPercent(item.edge)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.height(SpacingMd))
                    }
                    if (historicalMatches.size > visibleHistoricalCount) {
                        TextButton(
                            onClick = {
                                visibleHistoricalCount = nextVisibleCount(
                                    visibleHistoricalCount,
                                    historicalMatches.size,
                                    30
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多（${historicalMatches.size - visibleHistoricalCount} 场）") }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            val availablePools = FootballPlayTypes.allCodes.filter { match.poolsJson.contains("\"$it\"") }
            if (availablePools.isEmpty()) {
                MetaPill("暂无赔率", SurfaceVariant, TextSecondary)
            }
            availablePools.forEach { code ->
                MetaPill(FootballPlayTypes.displayName(code), PrimaryContainer, PrimaryHover)
            }
        }

        val hadOdds = remember(match.poolsJson) { parseHadOdds(match.poolsJson) }
        if (hadOdds.isNotEmpty()) {
            Spacer(Modifier.height(SpacingMd))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingSm)
            ) {
                hadOdds.forEach { (label, value) ->
                    MetricCard(label, "%.2f".format(value), modifier = Modifier.weight(1f))
                }
            }
        }

        // 推荐摘要（扁平化，直接展示，不再嵌套展开）
        if (recommendations.isNotEmpty()) {
            Spacer(Modifier.height(SpacingMd))
            val sorted = recommendations.sortedWith(
                compareBy<FootballRecommendationEntity> { it.playType }.thenByDescending { it.confidence }
            )
            sorted.forEach { rec ->
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
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
