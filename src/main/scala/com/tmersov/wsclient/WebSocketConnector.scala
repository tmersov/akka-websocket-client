package com.tmersov.wsclient

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.event.Logging
import com.tmersov.wsclient.WebSocketState._
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations._
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}

//Exceptions
private class SocketUnhandledException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)
private class SocketClosingException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

//Socket Reply messages
private case class SocketConnected(session: Session)
private case class SocketClosed(statusCode: Int, reason: String)
private case class SocketError(session: Session, th: Throwable)

class WebSocketSession private[wsclient] {
  private val session: AtomicReference[Session] = new AtomicReference[Session](null)
  def send(msg: TextMessage) = session.get.getRemote.sendString(msg.text)
  private[wsclient] def update(newSession: Session) = session.set(newSession)
}

object WebSocketConnection {
  def props(uri: String, subscriber: ActorRef): Props = {
    Props(classOf[WebSocketConnection], uri, subscriber, new WebSocketSession)
  }
}

class WebSocketConnection private[wsclient](uri: String, subscriber: ActorRef, webSocketSession: WebSocketSession)
  extends Actor with FSM[WebSocketState, Null] with akka.actor.ActorLogging {

  protected var client: WebSocketClient = null
  protected var session: Session = null
  protected val messageLog = Logging(context.system, this.getClass.getSimpleName + ".messages")

  override def postRestart(reason: Throwable): Unit = {
    log.info("Restarting...")
    super.preStart()
    self ! Connect
  }

  startWith(UninitializedState, null)

  when(UninitializedState) {
    case Event(Connect, _) =>
      log.info("Connecting to {}", uri)
      connect(uri)
      goto(ConnectingState)
  }

  when(ConnectingState) {
    case Event(SocketConnected(newSession), _) =>
      log.info("Connected to {}", uri)
      this.session = newSession
      webSocketSession.update(session)
      subscriber ! Connected(webSocketSession)
      goto(ConnectedState)
  }

  when(ConnectedState) {
    case Event(TextMessage(text), _) =>
      messageLog.info (text)
      subscriber ! TextMessage(text)
      stay()
  }

  when(ClosingState) {
    case Event(SocketClosed(statusCode, reason), _) =>
      log.warning("Socket closed: statusCode: {}, reason {}", statusCode, reason)
      clearData()
      stop()
    case Event(SocketError(currentSession, th), _) =>
      log.warning("Failed to gracefully stop socket, shutting down", th)
      stop(FSM.Shutdown)
  }

  whenUnhandled {
    case Event(SocketError(currentSession, th), _) =>
      log.error("Socket error occured", th)
      try {
        stopClient()
      } catch {
        case ex: Exception => log.error("Failed to stop client", ex)
      }
      throw new SocketUnhandledException(cause = th)
    case Event(SocketClosed(statusCode, reason), _) =>
      val msg = s"Socket was unexpectedly closed: statusCode: $statusCode, $reason"
      log.warning(msg)
      clearData()
      throw new SocketUnhandledException(msg = msg)
    case Event(Close, _) =>
      log.warning("Closing socket...")
      try {
        stopClient()
      } catch {
        case ex: Exception => throw new SocketClosingException(cause = ex)
      }
      goto(ClosingState)
  }

  def clearData() = {
    this.session = null
    this.client = null
  }

  def stopClient() = {
    try {
      if (this.session != null) this.session.close()
      else if (this.client != null) client.stop()
    } finally {
      clearData()
    }
  }

  def connect(uri: String) {
    client = new WebSocketClient
    client.start()
    val uriObj = new URI(uri)
    val request = new ClientUpgradeRequest
    client.connect(new WebSocketListener(self), uriObj, request)
  }

  @WebSocket
  private class WebSocketListener(private val subscriber: ActorRef) {
    @OnWebSocketConnect
    def onConnect(session: Session) {
      subscriber ! SocketConnected(session)
    }

    @OnWebSocketClose
    def onClose(statusCode: Int, reason: String) {
      subscriber ! SocketClosed(statusCode, reason)
    }

    @OnWebSocketMessage
    def onMessage(msg: String) {
      subscriber ! TextMessage(msg)
    }

    @OnWebSocketError
    def onError(session: Session, error: Throwable) {
      subscriber ! SocketError(session, error)
    }
  }
}



