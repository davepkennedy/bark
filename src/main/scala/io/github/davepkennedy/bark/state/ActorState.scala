package io.github.davepkennedy.bark.state

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import io.github.davepkennedy.bark._
import io.github.davepkennedy.bark.ui.Display
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object ActorState {
  val LOGGER = LoggerFactory.getLogger(classOf[ActorState])
  val StateTimeout: Long = 250
}

trait ActorState {
  import ActorState._
  import io.github.davepkennedy.bark._

  protected def namePrefix: String
  protected def isLeader: Boolean

  def showOn (display: ActorRef, serverState: ServerState): Unit = {
    display ! Display.PeerState (
      id = serverState.serverInfo.id,
      name = s"$namePrefix ${serverState.serverInfo.id}",
      leader = isLeader,
      currentTerm = serverState.currentTerm, 0,
      votedFor = serverState.votedFor,
      votes = serverState.serverInfo.votesReceived,
      heartbeat = serverState.serverInfo.lastHeartbeat)
  }

  def timerTick (serverState: ServerState): (Option[Request], ServerState, ActorState)
  def vote (vote: Vote, serverState: ServerState): (ServerState, ActorState)
  def requestVote (voteRequest: RequestVote, serverState: ServerState): (Response, ServerState, ActorState)
  def appendEntries (appendEntries: AppendEntries, serverState: ServerState): (Response, ServerState, ActorState)

  def acceptVote(term: Int) = Vote (term, voteGranted = true)
  def rejectVote(term: Int) = Vote (term, voteGranted = false)

  def acceptEntries(term: Int) = EntriesAppended(term, success = true)
  def rejectEntries(term: Int) = EntriesAppended(term, success = false)

  def nextTick = randomInterval
}

object NullState extends ActorState {
  override protected def namePrefix: String = "NullState"

  override def timerTick(serverState: ServerState): (Option[Request], ServerState, ActorState) = {
    (None, serverState, NullState)
  }

  override def vote(vote: Vote, serverState: ServerState): (ServerState, ActorState) = {
    (serverState, NullState)
  }

  override protected def isLeader: Boolean = false

  override def appendEntries(appendEntries: AppendEntries, serverState: ServerState): (Response, ServerState, ActorState) = {
    (rejectEntries(0), serverState, null)
  }

  override def requestVote(voteRequest: RequestVote, serverState: ServerState): (Response, ServerState, ActorState) = {
    (rejectVote(0), serverState, NullState)
  }
}
