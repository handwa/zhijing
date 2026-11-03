package com.example.zhijing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 知境主题入口。用法与 Compose 模板一致：
 *
 * ```
 * ZhiJingTheme { AppRoot() }
 * ```
 *
 * ⚠️ 与当前模板的一处关键差异：**默认关闭动态取色**（`dynamicColor = false`）。
 *
 * 模板默认 `dynamicColor = true`（Android 12+ 跟随壁纸取色）。对本产品不成立：
 *   1. 说明书 6.1.1 要求「浅色为主」——跟随壁纸会给出深色紫、粉色系的界面，直接违背；
 *   2. 状态四色（未探测/已验证/已掌握/易混淆）必须与主色保持稳定对比，
 *      动态取色会让「易混淆」在某些壁纸下和主色撞色，识别失效；
 *   3. 答辩截图与《研究报告》里的界面图会因人而异，无法作为统一交付物。
 *
 * 需要给用户选择权时，在设置页开一个「跟随壁纸取色（实验）」开关，把 dynamicColor 传 true，
 * 但**默认必须是 false**。
 */
@Composable
fun ZhiJingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ZjShapes,
            content = content
        )
    }
}

/** 形状：卡片 16、面板 22、输入 10，胶囊一律 pill。 */
val ZjShapes = Shapes(
    extraSmall = RoundedCornerShape(ZjRadius.xs),
    small = RoundedCornerShape(ZjRadius.sm),
    medium = RoundedCornerShape(ZjRadius.md),
    large = RoundedCornerShape(ZjRadius.lg),
    extraLarge = RoundedCornerShape(ZjRadius.lg)
)
