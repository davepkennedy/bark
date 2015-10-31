package io.github.davepkennedy.bark.state

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import io.github.davepkennedy.bark.ui.Displayable
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

class CandidateStub (val id: Int, initData: CandidateData) extends Candidate with Displayable with TimeFixture {
  override def display(id: Int,
                       name: String,
                       leader: Boolean,
                       currentTerm: Int,
                       commitIndex: Int,
                       votedFor: Option[Int],
                       votes: Int,
                       heartbeat: Long): Unit = {}

  override def shouldRetire: Boolean = false

  when (FollowerState) {
    case Event(appendEntries: AppendEntries, stateData: RaftData) =>
      stay using stateData
  }

  startWith(CandidateState, initData)
}

class CandidateSpec  extends TestKit (ActorSystem("CandidateSpec")) with FreeSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  def actorProps(id: Int, initData: FollowerData) = Props(classOf[FollowerStub], id, initData)

  override def afterAll(): Unit = {
    system.terminate()
  }

  "A candidate" - {
    "when receiving RequestVote" - {
      "rejects vote if term is before current term" in {
        val term = 3
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(0, currentTerm = term, peers = Map.empty, log = log)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! RequestVote(term = 2, candidateId = 2, 0, 0)
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "rejects vote if voted for some other candidate" in {
        val term = 3
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(0, currentTerm = term, votedFor = Some(2), peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 3, candidateId = 3, 0, 0)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "rejects vote if candidates log is less up to date" in {
        val term = 3
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = false))
      }

      "accepts vote if voted for is null and candidates log is at least as up to date as receivers log" in {
        val term = 3
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))

        candidate.stateData.votedFor should be(Some(3))
      }

      "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
        val term = 3
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, votedFor = Some(3), peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))
      }

      "accepting a vote reverts to Follower" in {
        val term = 3
        val log = logUpTo(term, 4)
        log.commitTo(4)

        val candidateData = CandidateData(lastTick = 0, currentTerm = term, votedFor = Some(3), peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))
        candidate.underlyingActor.setTime(500)

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))

        candidate.stateName should be (FollowerState)
      }

      "accepting a vote resets the timeout" in {
        val term = 3
        val log = logUpTo(term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, votedFor = Some(3), peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))
        candidate.underlyingActor.setTime(500)

        candidate ! requestVote
        expectMsg(Vote(1, term = 3, granted = true))

        candidate.stateData.asInstanceOf[FollowerData].lastTick should be (500)
      }

      "rejecting a vote resets the timeout" in {
        val term = 4
        val log = logUpTo(term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val requestVote = RequestVote(term = 3, candidateId = 2, 0, 0)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))
        candidate.underlyingActor.setTime(500)

        candidate ! requestVote
        expectMsg(Vote(1, term = 4, granted = false))

        candidate.stateData.asInstanceOf[CandidateData].lastTick should be (500)
      }
    }

    "when receiving AcceptEntries" - {
      "entries are appended to the log" in {
        val term = 4
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val appendEntries = AppendEntries (term = 5, leaderId = 99, prevLogIndex = 4, prevLogTerm = term, entries = Array(LogEntry(5, 5, bytesFrom(5))), leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted (1, term, success = true))
      }

      "invalid entries are rejected" in {
        val term = 4
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        // Prev log index does not match any in the current log
        val appendEntries = AppendEntries (term = 5, leaderId = 99, prevLogIndex = 5, prevLogTerm = term, entries = Array(LogEntry(5, 5, bytesFrom(5))), leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))

        candidate ! appendEntries
        expectMsg(EntriesAccepted (1, term, success = false))
      }

      "reverts back to Follower" in {
        val term = 4
        val log = logUpTo(term = term, 4)
        log.commitTo(4)
        val candidateData = CandidateData(lastTick = 0, currentTerm = term, peers = Map.empty, log = log)
        val appendEntries = AppendEntries (term = 5, leaderId = 99, prevLogIndex = 6, prevLogTerm = 4, entries = Array.empty, leaderCommit = 10)
        val candidate = TestFSMRef(new CandidateStub(1, candidateData))
        candidate.underlyingActor.setTime(500)

        candidate ! appendEntries
        candidate.stateName should be (FollowerState)
      }
    }
  }
}
