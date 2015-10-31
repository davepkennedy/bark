package io.github.davepkennedy.bark.state

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import io.github.davepkennedy.bark.ui.Displayable
import org.scalatest._

class FollowerStub (val id: Int, initData: FollowerData) extends Follower with Displayable with TimeFixture {
  override def display(id: Int,
                       name: String,
                       leader: Boolean,
                       currentTerm: Int,
                       commitIndex: Int,
                       votedFor: Option[Int],
                       votes: Int,
                       heartbeat: Long): Unit = {}

  override def shouldRetire: Boolean = false

  startWith(FollowerState, initData)
}

class FollowerSpec extends TestKit (ActorSystem("FollowerSpec")) with FreeSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  def actorProps(id: Int, initData: FollowerData) = Props(classOf[FollowerStub], id, initData)

  override def afterAll(): Unit = {
    system.terminate()
  }

  "A follower" - {
    "when receiving RequestVote" - {
      "rejects vote if term is before current term" in {
        val followerData = FollowerData(0, currentTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! RequestVote(term = 2, candidateId = 2, 0, 0)
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "rejects vote if voted for some other candidate" in {
        val followerData = FollowerData(0, currentTerm = 3, votedFor = Some(2))
        val requestVote = RequestVote(term = 3, candidateId = 3, 0, 0)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "rejects vote if candidates log is less up to date" in {
        val term = 3
        val log = logUpTo(term = term, maxEntry = 4)
        val followerData = FollowerData(lastTick = 0, currentTerm = term, log = log)
        val requestVote = RequestVote(term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "accepts vote if voted for is null and candidates log is at least as up to date as receivers log" in {
        val term = 3
        val log = logUpTo(term = term, maxEntry = 4)

        val followerData = FollowerData(lastTick = 0, currentTerm = term, log = log)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))

        follower.stateData.votedFor should be(Some(3))
      }

      "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
        val term = 3
        val log = logUpTo(term = term, maxEntry = 4)
        val followerData = FollowerData(lastTick = 0, currentTerm = term, log = log, votedFor = Some(3))
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))
      }

      "accepting a vote resets the timeout" in {
        val term = 3
        val log = logUpTo(term = term, maxEntry = 4)

        val followerData = FollowerData (lastTick = 0, currentTerm = term, log = log, votedFor = Some(3))
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))
        follower.underlyingActor.setTime(500)

        follower ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))

        follower.stateData.asInstanceOf[FollowerData].lastTick should be (500)
      }

      "rejecting a vote resets the timeout" in {
        val followerData = FollowerData (lastTick = 0, currentTerm = 4)
        val requestVote = RequestVote(term = 3, candidateId = 2, 0, 0)
        val follower = TestFSMRef(new FollowerStub(1, followerData))
        follower.underlyingActor.setTime(500)

        follower ! requestVote
        expectMsg(Vote(1, term = 4, granted = false))

        follower.stateData.asInstanceOf[FollowerData].lastTick should be (500)
      }
    }

    "when receiving AppendEntries" - {
      "rejects when term is before current term" in {
        val term = 4
        val maxLogEntry = 4
        val leaderTerm = term - 1
        val log = logUpTo(term = term, maxLogEntry)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val appendEntries = AppendEntries(term = leaderTerm, leaderId = 99, prevLogIndex = 4, prevLogTerm = term, entries = Array(LogEntry(5, 5, bytesFrom(5))), leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted(1, term, success = false))
      }

      "Reply false if log doesn’t contain an entry at prevLogIndex" in {
        val term = 4
        val maxLogEntry = 4
        val leaderTerm = term + 1
        val log = logUpTo(term = term, maxLogEntry)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val appendEntries = AppendEntries(term = leaderTerm, leaderId = 99, prevLogIndex = maxLogEntry + 1, prevLogTerm = term, entries = Array(LogEntry(5, 5, bytesFrom(5))), leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted(1, term, success = false))
      }

      "Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm" in {
        val term = 4
        val maxLogEntry = 4
        val leaderTerm = term + 1
        val log = logUpTo(term = term, maxLogEntry)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val appendEntries = AppendEntries(term = leaderTerm, leaderId = 99, prevLogIndex = maxLogEntry, prevLogTerm = leaderTerm, entries = Array(LogEntry(5, 5, bytesFrom(5))), leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted(1, term, success = false))
      }

      "If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)" in {
        val term = 4
        val maxLogEntry = 4
        val leaderTerm = term + 1

        val log = logUpTo(term = term, maxLogEntry)
        log.commitTo(4)

        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)

        val appendEntries = AppendEntries(term = leaderTerm,
          leaderId = 99,
          prevLogIndex = maxLogEntry,
          prevLogTerm = term,
          entries = Array(LogEntry(5, 5, bytesFrom(5))),
          leaderCommit = 6)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted(1, term, success = true))
        candidate.stateData.log.lastCommitted should be (5)
      }
    }
  }
}
