@file:Suppress("unused")

package io.opencubes.websocket

import java.io.IOException
import java.net.Socket
import java.net.URI
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

// TODO: Handle custom protocols
/**
 * A web socket implementaion from the [RFC 6455](https://tools.ietf.org/html/rfc6455).
 */
class WebSocket private constructor(private val socket: Socket, private var uri: URI?) {
  /**Create a connection with the specified [uri]. */
  constructor(uri: URI) : this(getSocket(uri), uri)
  /**Create a connection with the specified [uri] as a String. */
  constructor(uri: String) : this(URI(uri))

  private val inStream = socket.getInputStream()
  private val outStream = socket.getOutputStream()

  internal val openListeners = mutableSetOf<OpenListener>()
  internal val messageListeners = mutableSetOf<DataListener>()
  internal val pingListeners = mutableSetOf<DataListener>()
  internal val pongListeners = mutableSetOf<DataListener>()
  internal val closeListeners = mutableSetOf<CloseListener>()

  init {
    thread {
      if (uri != null) {
        val key = Crypto.base64(Crypto.nextBytes(16))
        sendRequest(outStream, uri!!, "GET",
            "Sec-WebSocket-Key" to key,
            "Sec-WebSocket-Version" to "13"
        )
        val headers = mutableMapOf<String, String>()
        do {
          val code = handleResponse(inStream, headers)
          if (code == 101) break
          else if (code in 300 until 400) {
            uri = URI(headers["location"])
            sendRequest(outStream, uri!!, "GET",
                "Sec-WebSocket-Key" to key,
                "Sec-WebSocket-Version" to "13"
            )
          } else if (code >= 400) {
            throw Exception("The server did not accept your request (code: $code)")
          }
          headers.clear()
        } while (true)
        if ("Sec-WebSocket-Accept" !in headers ||
            Crypto.hash("sha1").update(key + SERVER_APPENDIX).digestBase64() != headers["Sec-WebSocket-Accept"])
          throw IOException("Server did not handle the request correctly")

        openListeners.forEach(OpenListener::invoke)
      }
      while (true) {
        try {
          val frame = WebSocketFrame.getStreamed(inStream)
          var message: ByteArray
          if (!frame.fin) {
            message = frame.decode()
            while (true) {
              val f = WebSocketFrame.getStreamed(inStream)
              if (f.opcode != CONTINUE) {
                TODO()
                //close()
                //throw IOException("Send new frame while not finishing the last one")
              } else {
                message += f.decode()
                if (f.fin)
                  break
              }
            }
          } else message = frame.decode()
          if (frame.opcode == BINARY || frame.opcode == TEXT) messageListeners.forEach { it(message) }
          if (frame.opcode == PING) pingListeners.forEach { it(message) }
          if (frame.opcode == PONG) pongListeners.forEach { it(message) }
          if (frame.opcode == CLOSE) {
            if (message.size >= 2) {
              val code = (message[0].toInt() shr 8) + message[1]
              if (message.size > 2) {
                val msg = String(ByteArray(message.size - 2) { message[2 + it] })
                closeListeners.forEach { it(code, msg) }
              } else closeListeners.forEach { it(code, "") }
            } else closeListeners.forEach { it(1000, "") }
            socket.close()
          }
        } catch (_: IOException) {
          break
        }
      }
      try {
        close()
      } catch (_: IOException) {

      }
    }
  }

  /**Listen on when the socket is open. */
  fun onOpen(listener: OpenListener) = openListeners.add(listener)
  /**Listen on when the socket receives a message. */
  fun onMessage(listener: DataListener) = messageListeners.add(listener)
  /**Listen on when the socket receives a ping message. */
  fun onPing(listener: DataListener) = pingListeners.add(listener)
  /**Listen on when the socket receives a pong message. */
  fun onPong(listener: DataListener) = pongListeners.add(listener)
  /**Listen on when the socket receives a close message. */
  fun onClose(listener: CloseListener) = closeListeners.add(listener)

  /**Sends a [WebSocketFrame]. */
  private fun send(wsFrame: WebSocketFrame) = outStream.write(wsFrame.encode())
  /**
   * Send either a text or binary frame to the client/server.
   *
   * @param data The data to send.
   * @param fin If this is the final message in a sequence. Default: true
   * @param binary If the message should be a binary frame. Default: true
   * @param mask If the message should be masked. Default: true
   */
  fun send(data: ByteArray, fin: Boolean = true, binary: Boolean = true, mask: Boolean = true) =
      send(WebSocketFrame(if (binary) BINARY else TEXT, fin, mask, data))

  /**
   * Send a text frame to the client/server.
   *
   * @param data The data to send.
   * @param fin If this is the final message in a sequence. Default: true
   * @param mask If the message should be masked. Default: true
   */
  fun send(data: String, fin: Boolean = true, mask: Boolean = true) =
      send(data.toByteArray(), fin, false, mask)

  /**
   * Ping the client/server.
   *
   * @param data Optional data to send with the ping.
   * @param mask If the message should be masked. Default: true
   */
  fun ping(data: String? = null, mask: Boolean = true) =
      send(WebSocketFrame(PING, true, if (data != null) mask else false, data?.toByteArray()
          ?: byteArrayOf()))

  /**
   * Pong the client/server.
   *
   * @param data Optional data to send with the pong.
   * @param mask If the message should be masked. Default: true
   */
  fun pong(data: String? = null, mask: Boolean = true) =
      send(WebSocketFrame(PONG, true, if (data != null) mask else false, data?.toByteArray()
          ?: byteArrayOf()))

  /**
   * Send a close frame to the client/server.
   *
   * @param code Optional close code.
   * @param reason Optional reason for the close.
   */
  fun close(code: Number = 1000, reason: String = "") =
      send(WebSocketFrame(CLOSE, message = byteArrayOf((code.toInt() shr 8).toByte(), code.toByte(), *reason.toByteArray())))

  companion object {
    /**The apendix the server should append to the key. From the specification. */
    const val SERVER_APPENDIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    /**The value of the continue frame opcode */
    const val CONTINUE = 0x00
    /**The value of the text frame opcode */
    const val TEXT = 0x01
    /**The value of the binary frame opcode */
    const val BINARY = 0x02
    /**The value of the close frame opcode */
    const val CLOSE = 0x08
    /**The value of the ping frame opcode */
    const val PING = 0x09
    /**The value of the pong frame opcode */
    const val PONG = 0x0a

    private fun getSocket(uri: URI): Socket {
      val port = if (uri.port != -1) uri.port else when (uri.scheme) {
        "wss" -> 433
        else -> 80
      }
      return when (uri.scheme) {
        "wss" -> SSLSocketFactory.getDefault().createSocket(uri.host, port)
        "ws" -> SocketFactory.getDefault().createSocket(uri.host, port)
        else -> throw Error("Missing ws scheme part (ws:// or wss://)")
      }
    }

    /**Create a connection from a already existing [socket]. */
    fun shallow(socket: Socket) = WebSocket(socket, null)
  }
}