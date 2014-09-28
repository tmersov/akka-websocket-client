package com.tmersov.wsclient

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.{OnWebSocketMessage, WebSocket}
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory

/**
 * Created with IntelliJ IDEA.
 * User: Tim
 * Date: 11/5/13
 * Time: 11:20 PM
 */
object EchoWebSocket  {
  def create():Server = {
    val server = new Server(8080)
    val wsHandler = new WebSocketHandler() {
      override def configure(factory: WebSocketServletFactory) {
        factory.register(classOf[EchoSocket])
      }
    }
    server.setHandler(wsHandler)
    server.start()
    server
  }

  @WebSocket
  private class EchoSocket  {
    @OnWebSocketMessage
    def onText(session: Session, message: String) {
      val remote = session.getRemote
      if (message == "ping")
      remote.sendString("pong")
    }
  }
}
