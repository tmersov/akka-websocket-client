package com.tmersov.wsclient

object WebSocketState {
  case object UninitializedState extends WebSocketState
  case object ConnectingState extends WebSocketState
  case object ConnectedState extends WebSocketState
  case object ClosingState extends WebSocketState
  case object ClosedState extends WebSocketState
}

sealed trait WebSocketState

