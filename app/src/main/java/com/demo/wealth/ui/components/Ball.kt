package com.demo.wealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.LotteryBlue
import com.demo.wealth.ui.theme.LotteryBlueLight
import com.demo.wealth.ui.theme.LotteryRed
import com.demo.wealth.ui.theme.LotteryRedLight

/**
 * 彩球 - 立体渐变球体
 *
 * 改进点：
 * - 40dp（原 34dp）+ 径向渐变高光（原扁平色）
 * - 内阴影 + 投影营造立体感
 * - contentDescription 无障碍标签（原无）
 * - 引用语义令牌（原硬编码 0xFFC94444 / 0xFF2868B8）
 *
 * @param text 球面数字（如 "03"）
 * @param isRed true=红球，false=蓝球
 * @param size 球体尺寸，默认 40dp；小尺寸用 BallSmall
 */
@Composable
fun Ball(
    text: String,
    isRed: Boolean = true,
    size: Int = 40,
    modifier: Modifier = Modifier
) {
    val baseColor = if (isRed) LotteryRed else LotteryBlue
    val lightColor = if (isRed) LotteryRedLight else LotteryBlueLight

    // 径向渐变：左上角高光 -> 基色
    val gradient = Brush.radialGradient(
        colors = listOf(lightColor, baseColor),
        center = androidx.compose.ui.geometry.Offset(size * 0.35f, size * 0.30f),
        radius = size.toFloat()
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .shadow(elevation = 2.dp, shape = CircleShape)
            .background(gradient, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .semantics {
                contentDescription = if (isRed) "红球 $text" else "蓝球 $text"
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            style = if (size >= 36) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 小号彩球（用于历史开奖列表、热号展示）
 * 28dp，无立体阴影，扁平
 */
@Composable
fun BallSmall(
    text: String,
    isRed: Boolean = true,
    modifier: Modifier = Modifier
) {
    val color = if (isRed) LotteryRed else LotteryBlue
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .background(color, CircleShape)
            .semantics {
                contentDescription = if (isRed) "红球 $text" else "蓝球 $text"
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
