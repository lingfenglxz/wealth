package com.demo.wealth.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.tween

/**
 * 动画令牌 - 统一动画时长与缓动曲线
 *
 * 设计原则：
 * - 微交互 150-300ms（Material Motion 规范）
 * - 复杂过渡 ≤400ms
 * - 入场用减速曲线，出场用加速曲线
 * - 统一替代原代码中所有瞬间跳变的展开/折叠
 */

// ===== 动画时长（毫秒） =====
const val DurationFast = 150      // 快速反馈（按压、涟漪）
const val DurationNormal = 250    // 常规过渡（展开/折叠）
const val DurationSlow = 400      // 复杂过渡（页面切换）

// ===== 缓动曲线 =====
// Material3 Emphasized 缓动曲线
val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)  // 入场减速
val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)  // 出场加速
val EasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)               // 标准曲线

// ===== 语义动画规格工厂 =====
fun <T> enterTransitionSpec(): DurationBasedAnimationSpec<T> =
    tween(durationMillis = DurationNormal, easing = EasingEmphasizedDecelerate)

fun <T> exitTransitionSpec(): DurationBasedAnimationSpec<T> =
    tween(durationMillis = (DurationNormal * 0.7).toInt(), easing = EasingEmphasizedAccelerate)

fun <T> stateChangeSpec(): DurationBasedAnimationSpec<T> =
    tween(durationMillis = DurationFast, easing = EasingStandard)
