package com.tmersov.wsclient

import akka.actor.ActorRef

/**
 * Created with IntelliJ IDEA.
 * User: Tim
 * Date: 9/27/2014
 * Time: 11:21 PM
 */
trait Message
sealed trait Reply extends Message
sealed trait Request extends Message

//Request messages
case class Connect(uri: String, subscriber: ActorRef) extends Request
case class Close() extends Request
case class Connected(session: ActorRef) extends Reply

trait DataMessage extends Request with Reply
case class TextMessage(text: String) extends DataMessage
