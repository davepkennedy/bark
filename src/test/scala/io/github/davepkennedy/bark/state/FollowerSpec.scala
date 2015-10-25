package io.github.davepkennedy.bark.state

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import io.github.davepkennedy.bark.ui.Displayable
import org.scalatest._

class FollowerStub (val id: Int, initData: FollowerData) extends Follower with Displayable {
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
        expectMsg(Vote(term = 3, granted = false))
      }

      "rejects vote if voted for some other candidate" in {
        val followerData = FollowerData(0, currentTerm = 3, votedFor = Some(2))
        val requestVote = RequestVote(term = 3, candidateId = 3, 0, 0)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 3, granted = false))
      }

      "rejects vote if candidates log is less up to date" in {
        val followerData = FollowerData(lastTick = 0, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote(term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 3, granted = false))
      }

      "accepts vote if voted for is null and candidates log is at least as up to date as receivers log" in {
        val followerData = FollowerData(lastTick = 0, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 3, granted = true))

        follower.stateData.votedFor should be(Some(3))
      }

      "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
        val followerData = FollowerData(lastTick = 0, currentTerm = 3, lastApplied = 4, votedFor = Some(3))
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 3, granted = true))
      }

      "accepting a vote resets the timeout" in {
        val followerData = FollowerData (lastTick = 0, currentTerm = 3, lastApplied = 4, votedFor = Some(3))
        val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 3, granted = true))

        follower.stateData.asInstanceOf[FollowerData].lastTick should not be 0
      }

      "rejecting a vote resets the timeout" in {
        val followerData = FollowerData (lastTick = 0, currentTerm = 4)
        val requestVote = RequestVote(term = 3, candidateId = 2, 0, 0)
        val follower = TestFSMRef(new FollowerStub(1, followerData))

        follower ! requestVote
        expectMsg(Vote(term = 4, granted = false))

        follower.stateData.asInstanceOf[FollowerData].lastTick should not be 0
      }
    }
    /*
    "when receiving AppendEntries" - {
      "rejects when term is before current term" in {
        val serverState = new ServerState
        println("setup")
        val follower = new Follower (serverState)

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = -1,
            leaderId = 1,
            prevLogIndex = 1,
            prevLogTerm = 1,
            entries = Array(),
            leaderCommit = 1))
        result.success should be (right = false)
      }

      "Reply false if log doesn’t contain an entry at prevLogIndex" in {
        val serverState = new ServerState
        val follower = new Follower (serverState)

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = 2,
            leaderId = 1,
            prevLogIndex = 2,
            prevLogTerm = 1,
            entries = Array(),
            leaderCommit = 1))


        result.success should be (right = false)
      }

      "Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm" in {
        val serverState = new ServerState
        val follower = new Follower (serverState)

        serverState.log = Array(LogEntry(term = 1, Entry(Array())))

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = 2,
            leaderId = 1,
            prevLogIndex = 1,
            prevLogTerm = 2,
            entries = Array(),
            leaderCommit = 1))

        result.success should be (right = false)
      }

      "replaces an existing entry with a new one if the index is the same but has a different term" in {
        /*
        val serverState = new ServerState
        val follower = new Follower (serverState)
        val data = "foo".getBytes("utf-8")

        serverState.log = Array(LogEntry(term = 1, Entry(Array())))

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = 2,
            leaderId = 1,
            prevLogIndex = 1,
            prevLogTerm = 1,
            entries = Array(Entry(data)),
            leaderCommit = 1))

        result.success should be (right = true)
        result.term should be (2)

        serverState.log(0).entry.data should be (data)
        */
      }

      "appends any new entries not already in the log" in {
        val serverState = new ServerState
        val follower = new Follower (serverState)
        val data = "foo".getBytes("utf-8")

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = 2,
            leaderId = 1,
            prevLogIndex = 0,
            prevLogTerm = 0,
            entries = Array(Entry(data)),
            leaderCommit = 1))

        result.success should be (right = true)
        result.term should be (2)

        serverState.log(1).entry.data should be (data)
      }

      "If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)" in {
        val serverState = new ServerState
        val follower = new Follower (serverState)
        val data = "foo".getBytes("utf-8")

        val result = follower.appendEntries(
          AppendEntriesRequest(
            term = 2,
            leaderId = 1,
            prevLogIndex = 0,
            prevLogTerm = 0,
            entries = Array(Entry(data)),
            leaderCommit = 3))

        serverState.commitIndex should be (1)
      }
      */
  }
}
