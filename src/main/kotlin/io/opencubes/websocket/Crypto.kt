package io.opencubes.websocket

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**A central for the crypto thing needed. */
object Crypto {
  private val random = SecureRandom()
  private val base64Encoder = Base64.getEncoder()
  /**Get next [amount] of random bytes. */
  fun nextBytes(amount: Number) = ByteArray(amount.toInt()).apply(random::nextBytes)
  /**Encodes [bytes] to base64. */
  fun base64(bytes: ByteArray) = base64Encoder.encodeToString(bytes)
  /**Encodes [string] to base64. */
  fun base64(string: String) = base64Encoder.encodeToString(string.toByteArray())
  /**Creates a [Hash] instance with the specified [algorithm]. */
  fun hash(algorithm: String) = Hash(algorithm)

  /**A hash instance created from a algorithm. */
  class Hash(algorithm: String) {
    private val messageDigest = MessageDigest.getInstance(algorithm)

    /**Update the hash with the specified [input]. */
    fun update(input: ByteArray): Hash {
      messageDigest.update(input)
      return this
    }
    /**Update the hash with the specified [input]. */
    fun update(input: String) = update(input.toByteArray())

    /**Create a base64 digest of the hash. */
    fun digestBase64() = base64(messageDigest.digest())
  }
}