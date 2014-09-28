package com.tmersov.wsclient

import java.net.URI

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.Logging
import akka.routing.RoundRobinRouter
import com.tmersov.wsclient.WebSocketConnector._
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations._
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}


/**
 * Created with IntelliJ IDEA.
 * User: Tim
 * Date: 10/22/13
 * Time: 9:55 PM
 */

object WebSocketConnector {

  object WebSocketCodes {
    val CLOSED_BY_USER = 101
  }
  //Socket Reply messages
  private case class SocketConnected(session: Session)
  private case class SocketClosed(statusCode: Int, reason: String)
  private case class SocketError(session: Session, th: Throwable)

  def props(): Props = Props[WebSocketConnector]

  private class WebSocketSession(private val session: Session) extends Actor with akka.actor.ActorLogging {
    def receive: Receive = {
      case TextMessage(text) => {
        session.getRemote.sendString(text)
      }
      case msg: Close => {
        log.info("Closing...")
        session.close(WebSocketCodes.CLOSED_BY_USER, "Closed by User")
      }
    }
  }

  private class WebSocketConnection extends Actor with akka.actor.ActorLogging {

    protected var client: WebSocketClient = _
    protected var session: Session = _
    protected var subscriber: ActorRef = _

    protected val messageLog = Logging(context.system, this.getClass.getSimpleName+".messages")

    def receive = disconnected

    def disconnected: Receive = {
      case Connect(uri, requester) => {
        log.info("Connecting to {}", uri)
        this.subscriber = requester
        context.become(connecting)
        connect(uri)
      }
    }

    def connecting: Receive = {
      case msg: SocketConnected => {
        session = msg.session
        log.info("Connected")
        val producer = context.actorOf(Props(classOf[WebSocketSession], session).
          withRouter(RoundRobinRouter(nrOfInstances = 5)))
        context.become(connected)
        subscriber ! Connected(producer)
      }
      case msg: SocketError => {
        log.info("Error", msg.th)
        context.become(error)
        subscriber ! msg
      }
    }

    def connected: Receive = {
      case msg: SocketClosed => {
        log.info("Disconnected")
        context.become(disconnected)
        subscriber ! msg
      }
      case msg: DataMessage => {
        messageLog.info(msg.toString)
        subscriber ! msg
      }
      case msg: SocketError => {
        messageLog.info(msg.toString)
        context.become(error)
        subscriber ! msg
      }
    }

    def disconnecting: Receive = {
      case msg: SocketClosed => {
        subscriber ! msg
        context.become(disconnected)
      }
    }

    def error: Receive = {
      case _ =>
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
}

class WebSocketConnector extends Actor with ActorLogging {

  def receive = {
    case msg: Connect => {
      val actor = context.actorOf(Props[WebSocketConnection])
      log.info("Creating connection: {}", msg.uri)
      actor ! msg
    }
  }
}

