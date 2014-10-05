package com.tmersov.wsclient

import akka.actor.{Terminated, ActorSystem}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.path
import org.specs2.mock.Mockito

import scala.concurrent.duration._


/**
 * Created with IntelliJ IDEA.
 * User: Tim     k
 * Date: 11/3/13
 * Time: 12:49 PM
 */
@RunWith(classOf[JUnitRunner])
class WebSocketConnectorSpec extends path.FunSpec with ShouldMatchers with Mockito {

  describe("WebSocket") {
    //Setup
    val config = ConfigFactory.load("application-test.conf")
    implicit val system = ActorSystem("testsystem", config)
    val testProbe = TestProbe()

    describe("when server is running") {
      val server = EchoWebSocket.create()

      describe("and client connects") {
        val webSocketConnection = system.actorOf(WebSocketConnection.props("ws://localhost:8080", testProbe.ref))
        webSocketConnection ! Connect
        val connectedMsg = testProbe.receiveOne(500 millis).asInstanceOf[Connected]
        val session = connectedMsg.session

        it("Connection event is received") {
          connectedMsg should not be null
        }

        describe("When sending ping message") {
          session.send(TextMessage("ping"))
          it("Receive pong message") {
            val pongMessage = testProbe.receiveOne(500 millis).asInstanceOf[TextMessage]
            pongMessage.text should equal ("pong")
          }
        }

        describe("When close") {
          testProbe watch webSocketConnection
          webSocketConnection ! Close
          it("Session is closed") {
            testProbe.receiveOne(500 millis).asInstanceOf[Terminated]
          }
        }

        describe("When server is restarted") {
          server.stop()
          server.start()
          it("Web Socket Client reconnects") {
            //We have reconnected
            testProbe.receiveOne(500 millis).asInstanceOf[Connected]
            session.send(TextMessage("ping"))
            //Session also reconnected
            val pongMessage = testProbe.receiveOne(500 millis).asInstanceOf[TextMessage]
            pongMessage.text should equal ("pong")
          }
        }

        webSocketConnection ! Close
        testProbe.receiveOne(500 millis).asInstanceOf[Terminated]
      }
      server.stop()
    }
  }

}
