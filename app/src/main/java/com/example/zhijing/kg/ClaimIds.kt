package com.example.zhijing.kg

import java.security.MessageDigest

/**
 * 命题 ID 的生成规则，与 `kg/src/kg_model.py` 的 `claim_id` / `normalize` / `short_hash`
 * 逐字对齐。
 *
 * 为什么必须在端上复刻：命题 ID 是图谱的单一主键，而 `assets/kg/graph.json` 里的
 * 出厂命题 ID 由 Python 脚本生成。云端生成的命题要能和出厂命题「同一个 ID 空间」，
 * 端上算出的哈希就必须与脚本完全一致，否则同一条命题在两个来源里会有两个 ID，
 * 判断状态立刻分裂成两份。
 *
 * 规则（见 `kg/schema/id-spec.md`）：
 *
 * ```
 * hash = sha1(normalize(statement))[:4]
 * normalize = 去首尾空白 → 去所有空白 → 去标点 → 转小写
 * ```
 *
 * 于是「只改标点或空格」不会换 ID，「改了实词」才会换 ID。
 */
object ClaimIds {

    /**
     * 去标点用的字符集，必须与 Python 侧 `_PUNCT` 逐字相同。
     *
     * 两边的差异只会导致同一个 statement 算出不同哈希，这是最难排查的一类 bug，
     * 因此 `ClaimIdsTest` 里对每个字符都做了交叉验证。
     */
    private const val PUNCTUATION =
        "，。！？；：、（）【】《》「」『』“”‘’…—·,.;:!?()[]{}<>\"'"

    private val PUNCT_SET: Set<Char> = PUNCTUATION.toSet()

    /** 与 `kg_model.normalize` 一致：去首尾空白、去所有空白（含全角空格）、去标点、转小写。 */
    fun normalize(text: String): String {
        val stripped = text.trim().replace('\u3000', ' ')
        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            if (ch.isWhitespace() || ch in PUNCT_SET) continue
            sb.append(ch)
        }
        return sb.toString().lowercase()
    }

    /** 稳定短哈希：`sha1(normalize(text))` 的前 [length] 位小写十六进制。 */
    fun shortHash(text: String, length: Int = 4): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(normalize(text).toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) hex.append('0')
            hex.append(v.toString(16))
        }
        return hex.substring(0, minOf(length, hex.length))
    }

    /**
     * 命题 ID：`clm.<语义路径>.<hash4>`。
     *
     * @param path 语义路径，如 `arch.ffn.linear`。由调用方决定（通常来自卡片所属大纲），
     *        必须只含小写 ASCII / 数字 / `.` `_` `-`，否则 ID 不合规。
     */
    fun claimId(path: String, statement: String): String {
        val slug = slugify(path)
        return "clm.$slug.${shortHash(statement)}"
    }

    /** 与 `kg_model.slugify` 同构：保留 ASCII 词，非 ASCII 段整体丢弃，兜底补哈希。 */
    fun slugify(text: String, fallback: String = "claim"): String {
        var ascii = text.lowercase().replace(Regex("[^a-z0-9.]+"), "-").trim('-', '.')
        ascii = ascii.replace(Regex("\\.{2,}"), ".")
        if (ascii.isNotEmpty()) return ascii.take(40)
        return "$fallback-${shortHash(text, 6)}"
    }

    /**
     * 校验 ID 是否合法（与 `kg_model.is_valid_id` 一致）。
     *
     * `clm` 必须有 4–8 位哈希后缀 —— 这是「命题 ID 可稳定寻址」的底线。
     */
    fun isValidClaimId(id: String): Boolean =
        id.length in 8..200 &&
            CLAIM_ID_RE.matches(id)

    private val CLAIM_ID_RE = Regex("^clm\\.[a-z0-9][a-z0-9._-]*\\.[0-9a-f]{4,8}$")
}
