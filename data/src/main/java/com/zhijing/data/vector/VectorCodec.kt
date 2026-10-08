package com.zhijing.data.vector

import com.zhijing.data.DbConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 512 维向量的二进制编解码。
 *
 * 约定：小端序 float32 × 512 = 2048 字节，与 `vector_table.vector` 和
 * `content_table.embedding_vector` 的 BLOB 表示一致。
 *
 * 解码失败不抛异常而是返回 null（维度不符/空值），由调用方跳过脏数据，
 * 避免一条坏向量导致整个检索崩溃。
 */
internal object VectorCodec {

    /** 向量维度，对齐说明书内置参数 vector_dim。 */
    const val DIM = DbConstants.VECTOR_DIM

    /** 序列化后的字节数：512 × 4 = 2048。 */
    const val BYTE_SIZE = DIM * Float.SIZE_BYTES

    /** 把 512 维向量序列化为小端字节数组。维度不符直接抛错，属于调用方编程错误。 */
    fun encode(vector: FloatArray): ByteArray {
        require(vector.size == DIM) { "Expected $DIM dimensions but got ${vector.size}" }
        val buffer = ByteBuffer.allocate(BYTE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /** 反序列化；字节数不等于 [BYTE_SIZE] 或入参为 null 时返回 null。 */
    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.size != BYTE_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(DIM) { buffer.float }
    }
}
