package io.opencubes.websocket

import org.junit.jupiter.api.Test
import java.util.*

internal class CryptoTest {
  @Test
  fun actualRandom() {
    arraysNotEqual(Crypto.nextBytes(1), Crypto.nextBytes(1))
  }

  private fun arraysNotEqual(a1: ByteArray, a2: ByteArray) =
      assert(!Arrays.equals(a1, a2)) {
        println(Arrays.toString(a1) + ":" + a1.size)
        println(Arrays.toString(a2) + ":" + a2.size)
      }
}