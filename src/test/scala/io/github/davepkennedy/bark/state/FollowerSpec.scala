package io.github.davepkennedy.bark.state

import akka.actor.ActorRef
import io.github.davepkennedy.bark.{Vote, RequestVote, ServerInfo, ServerState}
import org.apache.log4j.BasicConfigurator
import org.scalatest.{FreeSpec, Matchers}

class FollowerSpec extends FreeSpec with Matchers {
  import io.github.davepkennedy.bark._

  "A follower" - {
    "when receiving RequestVote" - {
      "rejects vote if term is before current term" in {
        val serverInfo = ServerInfo (1, Seq.empty[ActorRef])
        val serverState = ServerState (serverInfo, currentTerm = 4)
        val request = RequestVote (3, 2, 0, 0)
        val (response, _, _) =  Follower.requestVote(request, serverState)
        response match {
          case Vote (term, granted) => granted should be (right = false)
          case _ => fail("A vote was expected here")
        }
      }

      "rejects vote if voted for some other candidate" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState (serverInfo, currentTerm = 2, votedFor = Some(2))
        val requestVote = RequestVote (term = 3, candidateId = 3, 0, 0)
        val (response, _, _) =  Follower.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) => granted should be (right = false)
          case _ => fail("A vote was expected here")
        }
      }

      "rejects vote if candidates log is less up to date" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote (term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
        val (response, _, _) =  Follower.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) => granted should be (right = false)
          case _ => fail("A vote was expected here")
        }
      }

      "accepts vote if voted for is null and candidates log is at least as up to date as receivers log" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote (term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val (response, newState, _) =  Follower.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) =>
            granted should be (right = true)
            newState.votedFor match {
              case Some (votedFor) => votedFor should be (3)
              case None => fail("Voted for should be Some(3) on successful vote")
            }
          case _ => fail("A vote was expected here")
        }
      }

      "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4, votedFor = Some(3))
        val requestVote = RequestVote (term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val (response, newState, _) =  Follower.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) =>
            granted should be (right = true)
            newState.votedFor match {
              case Some (votedFor) => votedFor should be (3)
              case None => fail("Voted for should be Some(3) on successful vote")
            }
          case _ => fail("A vote was expected here")
        }
      }

      "accepting a vote resets the timeout" in {
        val serverInfo = ServerInfo (1, Seq.empty, lastHeartbeat = now - 1000)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4, votedFor = Some(3))
        val requestVote = RequestVote (term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val (_, newState, _) =  Follower.requestVote(requestVote, serverState)
        newState.serverInfo.lastHeartbeat should not be serverInfo.lastHeartbeat
      }

      "rejecting a vote resets the timeout" in {
        val serverInfo = ServerInfo (1, Seq.empty, lastHeartbeat = now - 1000)
        val serverState = ServerState (serverInfo, currentTerm = 4)
        val request = RequestVote (3, 2, 0, 0)
        val (_, newState, _) =  Follower.requestVote(request, serverState)
        newState.serverInfo.lastHeartbeat should not be serverInfo.lastHeartbeat
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
    }
    */
  }
}