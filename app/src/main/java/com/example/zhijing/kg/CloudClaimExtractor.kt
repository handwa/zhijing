package com.example.zhijing.kg

import com.example.zhijing.cloud.CloudMessage
import com.example.zhijing.cloud.CloudProvider
import com.example.zhijing.cloud.RemoteChatClient
import kotlinx.coroutines.flow.toList

/**
 * 云端命题抽取：把一段文本交给用户自己配置的大模型，取回候选命题。
 *
 * 这是「图谱由云端生成」这条链路的**第一半**：prompt → 调用 → 原始文本。
 * 解析由 [ClaimOutputParser] 负责，两者分开是为了让解析逻辑能脱离网络单测。
 *
 * prompt 与 `kg/prompts/extract_claims.md` 保持一致（含 few-shot）。
 * 为什么把 prompt 硬编码进代码而不是每次从 `assets` 读：
 * 端上运行时不依赖 kg 目录，prompt 版本随 APK 走，便于与解析器同步升级。
 */
class CloudClaimExtractor(
    private val client: RemoteChatClient = RemoteChatClient()
) {

    /**
     * 抽取一段文本里的候选命题。
     *
     * @param provider 用户自己配置的大模型（含 baseUrl / model / apiKey）
     * @param content 待抽取的原文，建议 ≤900 字（超过请切段，见 `extract_claims.md` 第 5 节）
     * @param topicHint 主题提示，用于让模型把语义路径写对
     * @param knownClaimIds 同主题下已有的命题 ID，供模型连接边；建议 3–5 条
     * @param maxTokens 单次生成的 token 上限，默认 512
     * @return 解析结果；调用失败时抛 [com.example.zhijing.cloud.CloudException]
     */
    suspend fun extract(
        provider: CloudProvider,
        content: String,
        topicHint: String = "",
        knownClaimIds: List<String> = emptyList(),
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): ClaimOutputParser.Result {
        provider.readyFailureReason()?.let { reason ->
            throw IllegalArgumentException("云端配置不完整：$reason")
        }
        require(content.isNotBlank()) { "待抽取文本为空" }

        val text = client.streamChat(
            provider = provider,
            messages = buildMessages(content, topicHint, knownClaimIds),
            maxTokens = maxTokens
        ).toList().joinToString("")

        return ClaimOutputParser.parse(text)
    }

    /** 组装投喂给模型的消息。system 原样取自 `extract_claims.md` 第 2 节。 */
    internal fun buildMessages(
        content: String,
        topicHint: String,
        knownClaimIds: List<String>
    ): List<CloudMessage> = listOf(
        CloudMessage("system", SYSTEM_PROMPT),
        CloudMessage("user", userPrompt(content, topicHint, knownClaimIds))
    )

    companion object {
        /** 与 `extract_claims.md` 第 5 节一致：两条命题的上限。 */
        const val DEFAULT_MAX_TOKENS = 512

        /** 单段输入的建议上限，超过就切段。 */
        const val MAX_CONTENT_CHARS = 900

        /** 同一主题下喂给模型的已有命题条数上限（小模型面对长清单会乱指）。 */
        const val MAX_KNOWN_IDS = 5

        /** system prompt，与 `kg/prompts/extract_claims.md` 第 2 节逐字一致。 */
        val SYSTEM_PROMPT: String = """
            你是知识图谱的命题抽取器，只输出 JSON，不解释、不使用 Markdown。
            任务：把文本里的「可判真假的断言」抽成命题，并给出判断它所需的边界。

            判断命题是否合格的三问：
            1. 能不能判对错？描述、建议、步骤都不合格。
            2. 是不是一句话？多个论点必须拆开，一次只留一条。
            3. 边界能不能说清？说不清就说明它还不是一条命题。

            conditions 写「在什么前提下成立」与「什么情况下不再适用」，不要复述 statement。
            evidence 必须逐字摘自原文，找不到原文依据就整条不要。
            errorKinds.kind 只能取：shape（形状依赖）/ range（值域依赖）/ statistics（统计量依赖）/ gradient（梯度路径依赖）/ semantic（语义依赖）。

            edges 只允许以下类型：
            depends_on 依赖 / affects 影响 / contains 组成 / contrasts 对比 / refutes 反例 / prerequisite 前置。
            每条边必须写 premise（前提）与 boundary（边界），写不出边界的边不要输出。

            输出格式：
            {"claims":[{"statement":"...","expected":true,"conditions":["..."],
             "evidence":"...","errorKinds":[{"kind":"semantic","why":"..."}],
             "misconception":"...","edges":[{"to":"<命题ID或空>","type":"depends_on",
             "kind":"shape","premise":"...","boundary":"...","counterexample":"..."}]}]}

            没有任何合格命题时输出 {"claims":[]}。
        """.trimIndent()

        internal fun userPrompt(
            content: String,
            topicHint: String,
            knownClaimIds: List<String>
        ): String = buildString {
            append("主题：").append(topicHint.ifBlank { "（未指定）" }).append('\n')
            append("已知命题（可用于 edges.to，没有就留空）：")
            append(knownClaimIds.take(MAX_KNOWN_IDS).joinToString(", "))
            append('\n')
            append("文本：\n\"\"\"\n")
            append(content.trim())
            append("\n\"\"\"")
        }
    }
}
