package io.github.davepkennedy.bark.state

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import io.github.davepkennedy.bark.ui.Displayable
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

class LeaderStub (val id: Int, initData: LeaderData) extends Leader with Displayable with TimeFixture {
  override def display(id: Int,
                       name: String,
                       leader: Boolean,
                       currentTerm: Int,
                       commitIndex: Int,
                       votedFor: Option[Int],
                       votes: Int,
                       heartbeat: Long): Unit = {}

  var retire = false

  def shouldRetire(retire: Boolean): Unit = this.retire = retire
  override def shouldRetire: Boolean = false

  startWith(LeaderState, initData)

  when (FollowerState) {
    case Event(appendEntries: AppendEntries, raftData: RaftData) =>
      stay using raftData
  }
}

class LeaderSpec extends TestKit (ActorSystem("FollowerSpec")) with FreeSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate()
  }

  "when receiving RequestVote" - {
    "rejects vote if term is before current term" in {
      val leaderData = LeaderData(0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! RequestVote(term = 2, candidateId = 2, 0, 0)
      expectMsg(Vote(term = 3, granted = false))
    }

    "rejects vote if voted for some other candidate" in {
      val leaderData = LeaderData(0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 3, candidateId = 3, 0, 0)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = false))
    }

    "rejects vote if candidates log is less up to date" in {
      val leaderData = LeaderData(lastTick = 0, currentTerm = 3, lastApplied = 4, peers = Seq.empty, commitIndex = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = false))
    }

    "accepts vote if voted for is null and candidates log is at least as up to date as receivers log" in {
      val leaderData = LeaderData(lastTick = 0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = true))

      leader.stateData.votedFor should be(Some(3))
    }

    "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
      val leaderData = LeaderData(lastTick = 0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = true))
    }

    "accepting a vote reverts to follower" in {
      val leaderData = LeaderData (lastTick = 0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = true))

      leader.stateName should be (FollowerState)
    }

    "accepting a vote resets the timeout" in {
      val leaderData = LeaderData (lastTick = 0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))
      leader.underlyingActor.setTime(500)

      leader ! requestVote
      expectMsg(Vote(term = 3, granted = true))

      leader.stateData.asInstanceOf[FollowerData].lastTick should be (500)
    }

    /* The leader does not have to care about timeouts */
    "rejecting a vote doesn't affect the timeout" in {
      val leaderData = LeaderData(0, currentTerm = 3, peers = Seq.empty, commitIndex = 4, lastApplied = 4, nextIndex = Array.empty, matchIndex = Array.empty)
      val leader = TestFSMRef(new LeaderStub(1, leaderData))

      leader ! RequestVote(term = 2, candidateId = 2, 0, 0)
      expectMsg(Vote(term = 3, granted = false))

      leader.stateData.asInstanceOf[LeaderData].lastTick should be (0)
    }
  }
  /*
  "when replicating entries" - {

  }
  */
}
