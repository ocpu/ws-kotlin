package io.opencubes.websocket

import java.io.*
import java.nio.ByteBuffer
import kotlin.experimental.or
import kotlin.experimental.xor

/**A helper class that encapsulates what a web socket frame is. */
class WebSocketFrame(
    /**The opcode to send */
    val opcode: Int,
    /**Is this the last frame in a sequence. */
    val fin: Boolean = true,
    /**The optional negotiated option. */
    val rsv1: Boolean = false,
    /**The optional negotiated option. */
    val rsv2: Boolean = false,
    /**The optional negotiated option. */
    val rsv3: Boolean = false,
    /**The the mask for this frame. */
    val mask: ByteArray? = null,
    /**The message this frame holds. */
    val message: ByteArray = byteArrayOf()
) {
  /**A constructor shorthand for the primary constructor. */
  constructor(opcode: Int, fin: Boolean = true, mask: Boolean = true, message: ByteArray = byteArrayOf()) :
      this(opcode, fin, false, false, false, if (mask) Crypto.nextBytes(4) else null, message)

  /**Encode this frame to a ByteArray. */
  fun encode(): ByteArray {
    val msgOffset = (if (mask == null) 2 else 6) + if (message.size > 0xffff) 8 else if (message.size > 0x7d) 2 else 0
    val msgLen: Byte = if (message.size > 0xffff) 0x7f else if (message.size > 0x7d) 0x7e else message.size.toByte()
    val buffer = ByteBuffer.allocate(msgOffset + message.size)
    buffer.put(0, opcode.toByte())
    if (fin) buffer.put(0, buffer.get(0) or 0x80.toByte())
    if (rsv1) buffer.put(0, buffer.get(0) or 0x40.toByte())
    if (rsv2) buffer.put(0, buffer.get(0) or 0x20.toByte())
    if (rsv3) buffer.put(0, buffer.get(0) or 0x10.toByte())
    buffer.put(1, msgLen)

    when (msgLen) {
      0x7e.toByte() -> buffer.putShort(2, message.size.toShort())
      0x7f.toByte() -> buffer.putInt(6, message.size)
    }

    if (mask != null) {
      buffer.put(1, buffer.get(1) or 0x80.toByte())
      buffer.put(msgOffset - 4, mask[0])
      buffer.put(msgOffset - 3, mask[1])
      buffer.put(msgOffset - 2, mask[2])
      buffer.put(msgOffset - 1, mask[3])

      for (i in message.indices) buffer.put(msgOffset + i, message[i] xor mask[i % 4])
    } else for (i in message.indices) buffer.put(msgOffset + i, message[i])

    return buffer.array()
  }

  /**Decodes this frame to a ByteArray. */
  fun decode() = if (mask == null) message else ByteArray(message.size) { message[it] xor mask[it % 4] }

  companion object {

    /**
     * Gets a [WebSocketFrame] from a [InputStream].
     *
     * @return The produced frame
     * @throws IOException If the stream starts with any invalid byte.
     */
    @Throws(IOException::class)
    fun getStreamed(stream: InputStream): WebSocketFrame {
      var b = stream.read()
      if (b == -1) throw IOException("Invalid byte")
      val opcode = b and 0xf
      val fin = b and 0x80 == 0x80
      val rsv1 = b and 0x40 == 0x40
      val rsv2 = b and 0x20 == 0x20
      val rsv3 = b and 0x10 == 0x10
      b = stream.read()
      if (b == -1) throw IOException("Invalid byte")
      val masked = b and 0x80 == 0x80
      val msgLen = b and 0x7f

      val size = when (msgLen) {
        0x7f -> {
          stream.read();stream.read();stream.read();stream.read() // skip 4
          (stream.read() shl 24) + (stream.read() shl 16) + (stream.read() shl 8) + stream.read()
        }
        0x7e -> {
          (stream.read() shl 8) + stream.read()
        }
        else -> msgLen
      }

      val mask = if (masked) stream.read(4) else null
      val message = stream.read(size)

      return WebSocketFrame(opcode, fin, rsv1, rsv2, rsv3, mask, message)
    }

    /**Reads [size] amounts och bytes from the [InputStream] */
    fun InputStream.read(size: Int) = ByteArray(size) { read().toByte() }
  }
}
