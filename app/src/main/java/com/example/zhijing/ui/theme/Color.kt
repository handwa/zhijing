package com.example.zhijing.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 「庭院纸感」色板 —— UI 方案选定后的唯一色源。
 *
 * 方向：自然有机 + 克制（对应说明书 6.1.1「浅色为主，跟随系统深浅色模式」）。
 * 三条纪律：
 *   1. 不用纯白 #FFF / 纯黑 #000：底用暖白纸色，字用暖近黑，长时间阅读不刺眼；
 *   2. 主色只做一件事（关键动作与选中态），强调色（赭石）全页最多用一次；
 *   3. 状态色不与主色混用 —— 四种探测状态各有一色，且深浅色分别调过对比度。
 *
 * 若要与现有 `app/src/main/java/com/example/zhijing/ui/theme/Color.kt` 合并，
 * 直接以本文件替换同名文件即可（包名与文件名保持一致）。
 */

// ── 纸与墨：中性色 ────────────────────────────────────────────────
internal val Paper = Color(0xFFF6F5F0)      // 页面底：暖白纸
internal val Surface = Color(0xFFFFFEFA)    // 卡片面：比底更亮一点
internal val Ink = Color(0xFF22261F)        // 正文墨色：暖近黑
internal val InkSoft = Color(0xFF5C6659)    // 次要文字：灰绿
internal val Line = Color(0xFFD5DACF)       // 分隔线 / 卡片描边
internal val LineSoft = Color(0xFFE8EBE2)

// ── 主色：植物绿 ─────────────────────────────────────────────────
internal val Leaf = Color(0xFF2E6B4F)
internal val LeafContainer = Color(0xFFDCEBE0)
internal val LeafOnContainer = Color(0xFF123322)
internal val Moss = Color(0xFF6E7F63)       // 苔藓：次级信息、标签
internal val MossContainer = Color(0xFFE4EADA)

// ── 强调色：赭石（全页最多一处） ──────────────────────────────────
internal val Ochre = Color(0xFFA66A2E)
internal val OchreContainer = Color(0xFFF6E7D6)

// ── 危险色 ───────────────────────────────────────────────────────
internal val Clay = Color(0xFFA63D2F)
internal val ClayContainer = Color(0xFFF7DDD8)

// ── 深色：林地夜色 ───────────────────────────────────────────────
internal val NightPaper = Color(0xFF121712)
internal val NightSurface = Color(0xFF1A201A)
internal val NightSurfaceVariant = Color(0xFF232B23)
internal val NightInk = Color(0xFFE8EDE4)
internal val NightInkSoft = Color(0xFFA8B3A5)
internal val NightLine = Color(0xFF323C32)
internal val NightLeaf = Color(0xFF7CC39A)
internal val NightLeafContainer = Color(0xFF1E3627)
internal val NightOchre = Color(0xFFD9A06A)

/**
 * 四种探测状态色。刻意不复用 Material 的 error/warning：
 * 「易混淆」不是错误，是「已探测但易错未解除」，颜色要够醒目但不能像报错。
 *
 * 用法：卡片角标、判断面板状态、庭院节点外环。
 */
@Immutable
data class StatusColors(
    val unprobed: Color = Color(0xFF8A9791),
    val verified: Color = Color(0xFF2E6B9B),
    val mastered: Color = Color(0xFF3C8F5F),
    val confused: Color = Color(0xFFC0741E),
)

val LightStatusColors = StatusColors()

val DarkStatusColors = StatusColors(
    unprobed = Color(0xFF7E8B84),
    verified = Color(0xFF6FA8D6),
    mastered = Color(0xFF6FC48E),
    confused = Color(0xFFE0A45C)
)

val LightColors = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = LeafContainer,
    onPrimaryContainer = LeafOnContainer,
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = MossContainer,
    onSecondaryContainer = Color(0xFF25301F),
    tertiary = Ochre,
    onTertiary = Color.White,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = Color(0xFF3B2410),
    error = Clay,
    onError = Color.White,
    errorContainer = ClayContainer,
    onErrorContainer = Color(0xFF3D0E08),
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFE8),
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Color(0xFFFAF9F4),
    surfaceContainer = Color(0xFFF3F4ED),
    surfaceContainerHigh = Color(0xFFEEF0E8),
    surfaceContainerHighest = Color(0xFFE8EBE2),
    outline = Line,
    outlineVariant = LineSoft,
    inverseSurface = Color(0xFF2B302A),
    inverseOnSurface = Color(0xFFF1F2EA),
    scrim = Color(0xFF0B1410)
)

val DarkColors = darkColorScheme(
    primary = NightLeaf,
    onPrimary = Color(0xFF0C1F14),
    primaryContainer = NightLeafContainer,
    onPrimaryContainer = Color(0xFFC9E9D6),
    secondary = Color(0xFFA9BC9B),
    onSecondary = Color(0xFF17200F),
    secondaryContainer = Color(0xFF2A3324),
    onSecondaryContainer = Color(0xFFDCE7CF),
    tertiary = NightOchre,
    onTertiary = Color(0xFF2A1706),
    tertiaryContainer = Color(0xFF3C2A17),
    onTertiaryContainer = Color(0xFFF3DCC3),
    error = Color(0xFFE59A8C),
    onError = Color(0xFF3D0E08),
    errorContainer = Color(0xFF4A1A12),
    onErrorContainer = Color(0xFFF8DAD4),
    background = NightPaper,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightInkSoft,
    surfaceContainerLowest = Color(0xFF0E130E),
    surfaceContainerLow = Color(0xFF161C16),
    surfaceContainer = NightSurfaceVariant,
    surfaceContainerHigh = Color(0xFF273027),
    surfaceContainerHighest = Color(0xFF2E382E),
    outline = NightLine,
    outlineVariant = Color(0xFF2C362C),
    inverseSurface = Color(0xFFE8EDE4),
    inverseOnSurface = Color(0xFF1A201A),
    scrim = Color(0xFF000000)
)

/** 让状态色也能通过 `MaterialTheme` 之外的通道取到（不污染 Material 语义槽）。 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
