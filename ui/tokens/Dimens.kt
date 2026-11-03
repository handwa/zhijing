package com.example.zhijing.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸、圆角、海拔、动效时长令牌。
 *
 * 为什么单独拆一个文件：说明书 7.2 要求「界面代码与参数解耦」，
 * 间距和动效是最容易被随手写死（`16.dp`、`300`）的东西，
 * 集中成对象后，改一版密度只动一个文件。
 */

object ZjSpace {
    /** 4 — 图标与文字的最小间隙 */
    val xs = 4.dp
    /** 8 — 同组元素之间 */
    val sm = 8.dp
    /** 12 — 卡片内边距（紧凑场景） */
    val md = 12.dp
    /** 16 — 卡片内边距 / 页面左右边距 */
    val lg = 16.dp
    /** 20 — 页面左右边距（大屏）、区块间距 */
    val xl = 20.dp
    /** 24 — 区块之间 */
    val xxl = 24.dp
    /** 32 — 页面顶部留白 */
    val xxxl = 32.dp
    /** 页面横向安全边距：手机 16，平板可覆盖为 32 */
    val pageHorizontal = 18.dp
    /** 底部导航占位（避免内容被遮住） */
    val bottomBarInset = 76.dp
}

object ZjRadius {
    /** 方角：判断面板里的条件块 */
    val xs = 6.dp
    /** 输入框、小标签容器 */
    val sm = 10.dp
    /** 卡片 */
    val md = 16.dp
    /** 大面板、抽屉、底部弹层 */
    val lg = 22.dp
    /** 胶囊按钮 / chip */
    val pill = 999.dp
}

object ZjElevation {
    /** 卡片默认：用 1dp 描边替代阴影，避免多层阴影糊成一片 */
    val card = 0.dp
    /** 悬浮按钮 / 提示条 */
    val raised = 3.dp
    /** 底部弹层 / 对话框 */
    val sheet = 10.dp
    /** 卡片描边宽度 */
    val hairline = 1.dp
}

object ZjMotion {
    /** 按压反馈：必须 ≤100ms（说明书 6.1.3） */
    const val pressMs = 90
    /** 轻交互：chip 选中、角标切换 */
    const val fastMs = 160
    /** 常规过渡：页面切换、面板展开 */
    const val baseMs = 240
    /** 场景过渡：庭院进入、判断面板升起 */
    const val slowMs = 320
    /** 「正在思考…」呼吸周期的一半 */
    const val breathMs = 1600

    /** 出场缓动：Material 3 emphasized-decelerate 的近似 */
    const val EASE_OUT = "cubic-bezier(0.16, 1, 0.3, 1)"
}

object ZjSize {
    /** 最小可点区域（无障碍下限 48dp） */
    val touchTarget = 48.dp
    /** 底部导航高度 */
    val navBar = 64.dp
    /** 顶栏高度 */
    val appBar = 56.dp
    /** 卡片左侧「叶脉书签」宽 */
    val spineWidth = 3.dp
    /** 状态墨点直径 */
    val statusDot = 7.dp
    /** 角标高度 */
    val badgeHeight = 22.dp
    /** 正文最大宽度（平板）：超过 68 字阅读会累 */
    val readingMaxWidth = 640.dp
}
