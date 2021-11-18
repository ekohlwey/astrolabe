@file:OptIn(ExperimentalUnsignedTypes::class)
@file:Suppress("NOTHING_TO_INLINE")
package com.github.ekohlwey.astrolabe.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.InputStream
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

internal inline infix fun UByte.shr(bits: Int): UByte = this.toInt().shr(bits).toUByte()

internal inline infix fun UByte.shl(count: Int): UByte = (this.toInt() shl count).toUByte()

internal inline fun ByteBuffer.readFloat(): Float = this.float

internal inline fun ByteBuffer.readShort(): Short = this.short

internal inline fun ByteBuffer.readUByte(): UByte = this.get().toUByte()

internal inline fun ByteBuffer.putUByte(byte: UByte) = this.put(byte.toByte())

internal inline fun ByteBuffer.putUBytes(bytes: UByteArray) = this.put(bytes.toByteArray(), 0, bytes.size)

internal suspend inline fun InputStream.readUBytes(len: Int): UByteArray? {
    val read = withContext(Dispatchers.IO) { kotlin.runCatching { readNBytes(len)  }}.getOrNull()
    return if (read == null) {
        logger.info { "Input stream closed" }
        null
    } else {
        read.toUByteArray()
    }
}

internal suspend fun InputStream.readUByte(): UByte? {
    val read = withContext(Dispatchers.IO) { runCatching { read() } }.getOrNull()
    if (read == null) {
        logger.info { "Input stream closed" }
        return null
    }
    return if (read < 0) {
        logger.info { "End of stream detected" }
        null
    } else {
        read.toUByte()
    }
}


internal inline fun ByteBuffer.putUShort(value: UShort) = this.putShort(value.toShort())