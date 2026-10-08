package com.zhijing.data.vector

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** [VectorCodec] 的纯 JVM 测试，不依赖 Android。 */
class VectorCodecTest {

    @Test
    fun byteSizeMatches512Float32() {
        assertEquals(2048, VectorCodec.BYTE_SIZE)
        assertEquals(512, VectorCodec.DIM)
    }

    @Test
    fun encodeDecodeRoundTripKeepsValues() {
        val vector = FloatArray(VectorCodec.DIM) { it * 0.5f - 3f }

        val decoded = VectorCodec.decode(VectorCodec.encode(vector))

        assertNotNull(decoded)
        assertArrayEquals(vector, decoded!!, 0f)
    }

    @Test
    fun encodeUsesLittleEndianByteOrder() {
        val vector = FloatArray(VectorCodec.DIM)
        vector[0] = 1.0f // IEEE754 小端序为 00 00 80 3F

        val bytes = VectorCodec.encode(vector)

        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x00, bytes[1].toInt() and 0xFF)
        assertEquals(0x80, bytes[2].toInt() and 0xFF)
        assertEquals(0x3F, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun encodeRejectsWrongDimension() {
        assertThrows(IllegalArgumentException::class.java) {
            VectorCodec.encode(FloatArray(VectorCodec.DIM - 1))
        }
    }

    @Test
    fun decodeReturnsNullForNullInput() {
        assertNull(VectorCodec.decode(null))
    }

    @Test
    fun decodeReturnsNullForCorruptedLength() {
        assertNull(VectorCodec.decode(ByteArray(VectorCodec.BYTE_SIZE - 1)))
        assertNull(VectorCodec.decode(ByteArray(0)))
    }
}
