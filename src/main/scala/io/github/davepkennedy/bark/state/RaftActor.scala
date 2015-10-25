package io.github.davepkennedy.bark.state

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, FSM, ActorRef}
import io.github.davepkennedy.bark.ui.Displayable

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

sealed trait RaftState
object BootState extends RaftState
object FollowerState extends RaftState
object CandidateState extends RaftState
object LeaderState extends RaftState

sealed trait RaftData {
  val currentTerm: Int
  val votedFor: Option[Int]
  val commitIndex: Int
  val lastApplied: Int
}

final case class FollowerData (lastTick: Long,
                               currentTerm: Int = 0,
                               votedFor: Option[Int] = None,
                               peers: Seq[ActorRef] = Seq.empty,
                               commitIndex: Int = 0,
                               lastApplied: Int = 0) extends RaftData

final case class CandidateData (lastTick: Long,
                                currentTerm: Int,
                                votesGranted: Int = 0,
                                votedFor: Option[Int] = None,
                                peers: Seq[ActorRef],
                                commitIndex: Int,
                                lastApplied: Int) extends RaftData

final case class LeaderData (lastTick: Long,
                             currentTerm: Int,
                             peers: Seq[ActorRef],
                             commitIndex: Int,
                             lastApplied: Int,
                             nextIndex: Array[Int],
                             matchIndex: Array[Int]) extends RaftData {
  val votedFor = None
}

case object Tick
final case class RequestVote (term: Int,
                               candidateId: Int,
                               lastLogIndex: Int,
                               lastLogTerm: Int)
final case class Vote (term: Int,
                        granted: Boolean)

final case class LogEntry (term: Int, data: Array[Byte])
final case class AppendEntries (term: Int,
                                 leaderId: Int,
                                 prevLogIndex: Int,
                                 prevLogTerm: Int,
                                 entries: Array[LogEntry],
                                 leaderCommit: Int)
final case class EntriesAccepted (term: Int,
                                   success: Boolean)

object RaftActor {
  val StateTimeout: Long = 250
  val random = new Random
  def randomInterval: FiniteDuration = FiniteDuration (100 + random.nextInt(100), TimeUnit.MILLISECONDS)
}

trait RaftActor extends FSM[RaftState,RaftData] with ActorLogging {
  this: Displayable =>
  def id: Int
  def shouldRetire: Boolean
  def shouldAcceptVote (requestVote: RequestVote, raftData: RaftData): Boolean = {
    if (requestVote.term >= raftData.currentTerm &&
      raftData.votedFor.getOrElse(requestVote.candidateId) == requestVote.candidateId &&
      requestVote.lastLogIndex >= raftData.lastApplied
    ) true
    else false
  }
  def now = System.currentTimeMillis()

  def acceptVote (term: Int) = Vote(term, granted = true)
  def rejectVote (term: Int) = Vote(term, granted = false)

  def acceptEntries (term: Int) = EntriesAccepted (term, success = true)
  def rejectEntries (term: Int) = EntriesAccepted (term, success = false)
}
