package io.opencubes.websocket

import java.io.IOException
import java.net.*
import kotlin.concurrent.thread

/**A web socket server */
class WebSocketServer(port: Int, private val path: Regex = Regex("/")) {
  private val ss = ServerSocket(port)
  private val connectionListeners = mutableSetOf<WSListener>()
  private val _connections = mutableSetOf<WebSocket>()

  /**
   * All connections that are currently connected.
   */
  val connections: Set<WebSocket> get() = _connections

  init {
    thread {
      while (true) {
        val s = try {
          ss.accept()
        } catch (_: IOException) {
          continue
        } ?: continue
        thread {
          val headers: MutableMap<String, String> = mutableMapOf<String, String>()
          val (method, path) = handleRequest(s.getInputStream(), headers)
          var p = URI(path).path
          p = if (p.isNotBlank()) p else "/"
          if (this.path.find(p) == null)
            sendResponse(s.getOutputStream(), 404, "Connection" to "close")
          else handleUpgrade(method, headers, s)
        }
      }
    }
  }

  /**
   * Have a this socket server handle a upgrade request.
   *
   * @param method The method from the request.
   * @param headers The headers from the request.
   * @param socket The socket connection to upgrade.
   */
  fun handleUpgrade(method: String, headers: Map<String, String>, socket: Socket) {
    val outStream = socket.getOutputStream()
    if (method.toUpperCase() != "GET") {
      sendResponse(outStream, 400, "Connection" to "close")
      socket.close()
      return
    }
    val v = headers.entries.find { it.key.toLowerCase() == "sec-websocket-version" }
    if (v == null || v.value != "13") {
      sendResponse(outStream, 400, "Connection" to "close")
      socket.close()
      return
    }
    val k = headers.entries.find { it.key.toLowerCase() == "sec-websocket-key" }
    if (k == null) {
      sendResponse(outStream, 400, "Connection" to "close")
      socket.close()
      return
    }
    val accept = Crypto.hash("sha1").update(k.value + APPENDIX).digestBase64()
    // TODO: Handle sub protocols
    sendResponse(outStream, 101, "Sec-WebSocket-Accept" to accept)

    thread {
      val ws = WebSocket.shallow(socket)
      connectionListeners.forEach { it(ws) }
      ws.openListeners.forEach(OpenListener::invoke)
      val inStream = socket.getInputStream()
      while (true) {
        try {
          val frame = WebSocketFrame.getStreamed(inStream)
          var message: ByteArray
          if (!frame.fin) {
            message = frame.decode()
            while (true) {
              val f = WebSocketFrame.getStreamed(inStream)
              if (f.opcode != WebSocket.CONTINUE) {
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
          if (frame.opcode == WebSocket.BINARY || frame.opcode == WebSocket.TEXT)
            ws.messageListeners.forEach { it(message) }
          if (frame.opcode == WebSocket.PING) ws.pingListeners.forEach { it(message) }
          if (frame.opcode == WebSocket.PONG) ws.pongListeners.forEach { it(message) }
          if (frame.opcode == WebSocket.CLOSE) {
            if (message.size >= 2) {
              val code = (message[0].toInt() shr 8) + message[1]
              if (message.size > 2) {
                val msg = String(ByteArray(message.size - 2) { message[2 + it] })
                ws.closeListeners.forEach { it(code, msg) }
              } else ws.closeListeners.forEach { it(code, "") }
            } else ws.closeListeners.forEach { it(1000, "") }
            socket.close()
          }
        } catch (_: IOException) {
          break
        }
      }
      try {
        ws.close()
      } catch (_: IOException) {

      }
    }
  }

  /**Listen on when a connection is made to the server. */
  fun onConnection(listener: WSListener) = connectionListeners.add(listener)

  companion object {
    /**The apendix the server should append to the key. From the specification. */
    const val APPENDIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  }
}