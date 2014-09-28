package com.tmersov.wsclient

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.path
import org.specs2.mock.Mockito

import scala.concurrent.duration._


/**
 * Created with IntelliJ IDEA.
 * User: Tim
 * Date: 11/3/13
 * Time: 12:49 PM
 */
class WebSocketConnectorSpec extends path.FunSpec with ShouldMatchers with Mockito {

  describe("WebSocket") {
    //Setup
    val config = ConfigFactory.load("application-test.conf")
    implicit val system = ActorSystem("testsystem", config)
    val testProbe = TestProbe()

    describe("when server is running") {
      val server = EchoWebSocket.create()

      describe("and client connects") {
        val webSocketConnector = TestActorRef(WebSocketConnector.props())
        webSocketConnector ! Connect("ws://localhost:8080", testProbe.ref)
        val connectedMsg = testProbe.receiveOne(500 millis).asInstanceOf[Connected]

        it("Connection event is received") {
          connectedMsg should not be null
        }

        describe("When sending ping message") {
          val producer = connectedMsg.session
          producer ! TextMessage("ping")

          it("Receive pong message") {
            val pongMessage = testProbe.receiveOne(500 millis).asInstanceOf[TextMessage]
            pongMessage.text should equal ("pong")
          }
        }

        webSocketConnector ! Close
      }
      server.stop()
    }
  }

}
