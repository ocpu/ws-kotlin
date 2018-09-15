package io.opencubes.websocket

import java.io.*
import java.net.URI


/**A alias for a function that listens on open events */
typealias OpenListener = () -> Unit
/**
 * A alias for a function that listens on message, ping
 * and pong events with a data ByteArray.
 */
typealias DataListener = (data: ByteArray) -> Unit
/**
 * A alias for a function that listens on close events with
 * a code Int and data String.
 */
typealias CloseListener = (code: Int, data: String) -> Unit
/**A alias for a function that listens on connection events */
typealias WSListener = (WebSocket) -> Unit



private val codeToMessage = mapOf(
    101 to "Continue",
    101 to "Switching Protocols",
    200 to "OK",
    201 to "Created",
    202 to "Accepted",
    204 to "No Content",
    400 to "Bad Request",
    401 to "Unauthorized",
    403 to "Forbidden",
    404 to "Not Found"
)

/**Handles the sending of a HTTP request. */
fun sendRequest(stream: OutputStream, uri: URI, method: String, vararg headers: Pair<String, String>) =
    stream.write(buildString {
      val path = if (uri.path.isBlank()) "/" else uri.path
      val query = if (uri.query == null) "" else "?" + uri.query
      appendln("$method $path$query HTTP/1.1")
      appendln("Host: ${uri.host}")
      //appendln("Date: ${}")
      for ((name, value) in headers)
        appendln("$name: $value")
      appendln()
    }.toByteArray())

/**Handles the sending of a HTTP response. */
fun sendResponse(stream: OutputStream, code: Int, vararg headers: Pair<String, String>) =
    stream.write(buildString {
      appendln("HTTP/1.1 $code ${codeToMessage[code]}")
      //appendln("Date: ${}")
      for ((name, value) in headers)
        appendln("$name: $value")
      appendln()
    }.toByteArray())

private val RESPONSE_STATUS_LINE = Regex("HTTP/1.1\\s+(\\d{3})")
private val REQUEST_STATUS_LINE = Regex("(\\w+)\\s+([^\\s]+)\\s+HTTP/1.1")
private val HEADER_FIELD = Regex("([^:]+):\\s+(.+)")
/**Handles the receiving of a HTTP response. */
fun handleResponse(stream: InputStream, headers: MutableMap<String, String>): Int {
  val reader = stream.bufferedReader()
  val (sStatus) = RESPONSE_STATUS_LINE.find(reader.readLine())?.destructured ?: throw IOException("Invalid status line")
  var line = reader.readLine()
  while (!line.isNullOrBlank()) {
    val (name, value) = HEADER_FIELD.find(line)?.destructured ?: throw IOException("Invalid header field")
    val header = name.toLowerCase()
    if (header in headers) headers[header] += ", $value"
    else headers[header] = value
    line = reader.readLine()
  }
  return sStatus.toInt()
}
/**Handles the receiving of a HTTP request. */
fun handleRequest(stream: InputStream, headers: MutableMap<String, String>): Pair<String, String> {
  val reader = stream.bufferedReader()
  val (method, path) = REQUEST_STATUS_LINE.find(reader.readLine())?.destructured ?: throw IOException("Invalid status line")
  var line = reader.readLine()
  while (!line.isNullOrBlank()) {
    val (name, value) = HEADER_FIELD.find(line)?.destructured ?: throw IOException("Invalid header field")
    val header = name.toLowerCase()
    if (header in headers) headers[header] += ", $value"
    else headers[header] = value
    line = reader.readLine()
  }
  return Pair(method, path)
}
