package com.zhijing.data.converter

import androidx.room.TypeConverter

/**
 * 列表字段的类型转换器。
 *
 * 说明书中 `source_content_ids`、`side_a_content_ids`、`side_b_content_ids` 均为 TEXT，
 * 内容是一串内容 ID，因此用英文逗号拼接存储。ID 是纯数字，不存在分隔符歧义。
 */
internal class ListConverters {

    @TypeConverter
    fun longListToCsv(values: List<Long>?): String? =
        values?.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

    @TypeConverter
    fun csvToLongList(raw: String?): List<Long> =
        raw?.split(SEPARATOR)
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()

    private companion object {
        const val SEPARATOR = ","
    }
}
