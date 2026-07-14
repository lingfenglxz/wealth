package com.demo.wealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.demo.wealth.ui.WealthApp
import com.demo.wealth.ui.theme.WealthTheme

/**
 * WealthLab 主 Activity
 *
 * 职责：仅作为 Activity 入口；导航壳位于 ui/WealthApp.kt。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WealthTheme {
                WealthApp()
            }
        }
    }
}
