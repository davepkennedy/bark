package io.github.davepkennedy.bark.state

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, FSM, ActorRef}
import io.github.davepkennedy.bark.TimeSource
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
  val log: Log
}

final case class FollowerData (lastTick: Long,
                               currentTerm: Int = 0,
                               votedFor: Option[Int] = None,
                               peers: Map[Int,ActorRef] = Map.empty,
                               log: Log = new Log) extends RaftData

final case class CandidateData (lastTick: Long,
                                currentTerm: Int,
                                votesGranted: Int = 0,
                                votedFor: Option[Int] = None,
                                peers: Map[Int,ActorRef],
                                log: Log = new Log) extends RaftData

final case class LeaderData (lastTick: Long,
                             currentTerm: Int,
                             peers: Map[Int,ActorRef],
                             log: Log = new Log,
                             nextIndex: Map[Int,Int],
                             matchIndex: Map[Int,Int]) extends RaftData {
  val votedFor = None
}

case object Tick
final case class RequestVote (term: Int,
                               candidateId: Int,
                               lastLogIndex: Int,
                               lastLogTerm: Int)
final case class Vote (id: Int,
                       term: Int,
                       granted: Boolean)

final case class AppendEntries (term: Int,
                                 leaderId: Int,
                                 prevLogIndex: Int,
                                 prevLogTerm: Int,
                                 entries: Seq[LogEntry],
                                 leaderCommit: Int)
final case class EntriesAccepted (id: Int,
                                  term: Int,
                                  lastEntry: Int,
                                  success: Boolean)

object RaftActor {
  val StateTimeout: Long = 250
  val random = new Random
  def randomInterval: FiniteDuration = FiniteDuration (100 + random.nextInt(100), TimeUnit.MILLISECONDS)
}

trait RaftActor extends FSM[RaftState,RaftData] with ActorLogging {
  this: Displayable with TimeSource =>
  def id: Int
  def shouldRetire: Boolean
  def shouldAcceptEntries (appendEntries: AppendEntries, raftData: RaftData): Boolean = {
    if (appendEntries.term >= raftData.currentTerm &&
      raftData.log.hasEntryAt(appendEntries.prevLogIndex, appendEntries.prevLogTerm)) {
      true
    } else {
      false
    }
  }

  def appendEntriesToLog (appendEntries: AppendEntries, raftData: RaftData): Unit = {
    raftData.log.append(appendEntries.entries)
    if (appendEntries.leaderCommit > raftData.log.lastCommitted) {
      raftData.log.commitTo(math.min(appendEntries.leaderCommit, raftData.log.lastApplied))
    }
  }

  def shouldAcceptVote (requestVote: RequestVote, raftData: RaftData): Boolean = {
    if (requestVote.term >= raftData.currentTerm &&
      requestVote.lastLogIndex >= raftData.log.lastApplied &&
      raftData.votedFor.getOrElse(requestVote.candidateId) == requestVote.candidateId
    ) true
    else false
  }

  def acceptVote (term: Int) = Vote(id, term, granted = true)
  def rejectVote (term: Int) = Vote(id, term, granted = false)

  def acceptEntries (term: Int, lastEntry: Int) = EntriesAccepted (id, term, lastEntry = lastEntry, success = true)
  def rejectEntries (term: Int, lastEntry: Int) = EntriesAccepted (id, term, lastEntry = lastEntry, success = false)
}
