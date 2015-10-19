package io.github.davepkennedy.bark

import akka.actor.ActorRef

case class Entry (data: Array[Byte] = Array.empty)

case class LogEntry (term: Int, entry: Entry)

sealed trait Response
sealed trait Request

case class EntriesAppended
(
  term: Int,
  success: Boolean) extends Response

case class Vote
(
  term: Int,
  voteGranted: Boolean) extends Response

case class AppendEntries
(
  term: Int,
  leaderId: Int,
  prevLogIndex: Int,
  prevLogTerm: Int,
  entries: Array[Entry],
  leaderCommit: Int) extends Request

case class RequestVote
(
  term: Int,
  candidateId: Int,
  lastLogIndex: Int,
  lastLogTerm: Int) extends Request

case object Ticker
case object Ping
case object Pong

case class ServerInfo
(
  id: Int,
  peers: Seq[ActorRef],
  votesReceived: Int = 0,
  lastHeartbeat: Long = 0)

case class ServerState
(
  serverInfo: ServerInfo,
  currentTerm: Int = 0,
  votedFor: Option[Int] = None,
  log: Array[LogEntry] = Array.empty[LogEntry],
  commitIndex: Int = 0,
  lastApplied: Int = 0)