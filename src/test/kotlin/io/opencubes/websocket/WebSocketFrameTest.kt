package io.opencubes.websocket

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*

class WebSocketFrameTest {
  @Test
  fun notNull() {
    val frame1 = WebSocketFrame(1)
    assert(frame1.opcode == 1)
    assert(frame1.fin)
    assert(!frame1.rsv1)
    assert(!frame1.rsv2)
    assert(!frame1.rsv3)
    assert(frame1.mask != null)
    assert(frame1.message.isEmpty())
    val frame2 = WebSocketFrame(2, fin = false, mask = false)
    assert(frame2.opcode == 2)
    assert(!frame2.fin)
    assert(!frame2.rsv1)
    assert(!frame2.rsv2)
    assert(!frame2.rsv3)
    assert(frame2.mask == null)
    assert(frame2.message.isEmpty())
  }

  /**
   * https://tools.ietf.org/html/rfc6455#section-5.7
   */
  @Test
  fun hello() {
    val frame = WebSocketFrame(1, mask = false, message = "Hello".toByteArray())
    val encoded = byteArrayOf(0x81.toByte(), 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)
    testArrays(frame.encode(), encoded)
  }

  /**
   * https://tools.ietf.org/html/rfc6455#section-5.7
   */
  @Test
  fun helloMasked() {
    val mask = byteArrayOf(0x37, 0xfa.toByte(), 0x21, 0x3d)
    val frame = WebSocketFrame(1, mask = mask, message = "Hello".toByteArray())
    val encoded = byteArrayOf(0x81.toByte(), 0x85.toByte(), 0x37, 0xfa.toByte(), 0x21, 0x3d, 0x7f, 0x9f.toByte(), 0x4d, 0x51, 0x58)
    testArrays(frame.encode(), encoded)
  }

  /**
   * https://tools.ietf.org/html/rfc6455#section-5.7
   */
  @Test
  fun ping() {
    val frame = WebSocketFrame(9, mask = false, message = "Hello".toByteArray())
    val encoded = byteArrayOf(0x89.toByte(), 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)
    testArrays(frame.encode(), encoded)
  }

  /**
   * https://tools.ietf.org/html/rfc6455#section-5.7
   */
  @Test
  fun pongMasked() {
    val mask = byteArrayOf(0x37, 0xfa.toByte(), 0x21, 0x3d)
    val frame = WebSocketFrame(10, mask = mask, message = "Hello".toByteArray())
    val encoded = byteArrayOf(0x8a.toByte(), 0x85.toByte(), 0x37, 0xfa.toByte(), 0x21, 0x3d, 0x7f, 0x9f.toByte(), 0x4d, 0x51, 0x58)
    testArrays(frame.encode(), encoded)
  }

  /**
   * https://tools.ietf.org/html/rfc6455#section-5.7
   */
  @Test
  fun unmaskedFragmented() {
    val frame1 = WebSocketFrame(1, fin = false, mask = false, message = "Hel".toByteArray())
    val frame2 = WebSocketFrame(0, mask = false, message = "lo".toByteArray())
    val encoded1 = byteArrayOf(0x01, 0x03, 0x48, 0x65, 0x6c)
    val encoded2 = byteArrayOf(0x80.toByte(), 0x02, 0x6c, 0x6f)
    testArrays(frame1.encode(), encoded1)
    testArrays(frame2.encode(), encoded2)
  }

  @Test
  fun fromStream() {
    val message = "Hello"
    val frame = WebSocketFrame(1, message = message.toByteArray())
    val stream = ByteArrayInputStream(frame.encode())
    val incomingFrame = WebSocketFrame.getStreamed(stream)

    testArrays(incomingFrame.decode(), message.toByteArray())

    val frame2 = WebSocketFrame(1, mask = false, message = message.toByteArray())
    val stream2 = ByteArrayInputStream(frame2.encode())
    val incomingFrame2 = WebSocketFrame.getStreamed(stream2)

    testArrays(incomingFrame2.decode(), message.toByteArray())
  }

  @Test
  fun fromStreamThrowsOnInvalidFrames() {
    assertThrows<IOException> {
      WebSocketFrame.getStreamed(ByteArrayInputStream(byteArrayOf()))
    }
    assertThrows<IOException> {
      WebSocketFrame.getStreamed(
          ByteArrayInputStream(byteArrayOf(0x81.toByte()))
      )
    }
  }

  private fun testArrays(a1: ByteArray, a2: ByteArray) =
      assert(Arrays.equals(a1, a2)) {
        println(Arrays.toString(a1) + ":" + a1.size)
        println(Arrays.toString(a2) + ":" + a2.size)
      }
}