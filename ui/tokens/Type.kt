package com.example.zhijing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 「庭院纸感」字号阶梯。
 *
 * 字体配对：**衬线做展示，黑体做正文**。
 *   - 展示字用 `FontFamily.Serif`：Android 上即 Noto Serif CJK，零体积成本，
 *     与「庭院 / 纸感」气质一致，也比默认黑体更有辨识度；
 *   - 正文用 `FontFamily.SansSerif`：系统黑体，小字号可读性最好。
 * 不去内嵌第三方展示字体：中文展示字体子集化后仍有 2–3MB，收益不抵包体成本。
 *
 * 字号依据说明书 6.1.3「正文不小于 14 号字，支持系统字体缩放」：
 *   正文 15sp（不是 14sp 卡边缘），辅助信息 13sp，最小 11sp 仅用于角标。
 * 全部使用 sp，系统字体放大时自动跟随；不使用固定 px。
 */

/** 中文正文行高偏松：1.65–1.7，避免密排。 */
private val BodyLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val Typography = Typography(
    // 页面大标题（首页问候、庭院标题）：衬线，字距收紧
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 卡片 / 区块标题
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 页面标题（AppBar 主标题）
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 列表项标题
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 正文：15sp / 25sp，对应 6.1.3「正文不小于 14 号」
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 次要正文（回答引用、卡片摘要）
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 辅助信息（时间、来源、说明）：不承载正文内容
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = BodyLineHeightStyle
    ),
    // 按钮文字
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    // 标签、chip
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    // 角标、小标题（全大写感：加大字距）
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
)
