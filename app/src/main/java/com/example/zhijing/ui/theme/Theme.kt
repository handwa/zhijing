package com.example.zhijing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 「知境」主题入口。
 *
 * **刻意关闭动态取色（Material You）**：本应用的四种探测状态色
 * （未探测 / 已验证 / 已掌握 / 易混淆）是按「纸白底色」单独调过对比度的。
 * 动态取色会把主色换成壁纸色，与状态色直接打架，用户将无法从颜色区分状态。
 * 对「状态即信息」的产品来说，颜色稳定性比个性化更重要。
 *
 * `LocalStatusColors` 定义在 `Color.kt` 中（与色板同文件，便于一起维护）。
 */
@Composable
fun ZhiJingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
