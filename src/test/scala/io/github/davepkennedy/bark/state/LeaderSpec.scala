package io.github.davepkennedy.bark.state

import akka.actor.ActorRef
import io.github.davepkennedy.bark.{Vote, RequestVote, ServerState, ServerInfo}
import org.scalatest.{FreeSpec, Matchers}

class LeaderSpec extends FreeSpec with Matchers {
  "when receiving RequestVote" - {
    /* Basically every scenario from Follower, but all should be rejected */

    "rejects vote if term is before current term" in {
      val serverInfo = ServerInfo(1, Seq.empty[ActorRef])
      val serverState = ServerState(serverInfo, currentTerm = 4)
      val request = RequestVote(3, 2, 0, 0)
      val (response, _, _) = Leader.requestVote(request, serverState)
      response match {
        case Vote(term, granted) => granted should be(right = false)
        case _ => fail("A vote was expected here")
      }
    }

    "rejects vote if voted for some other candidate" in {
      val serverInfo = ServerInfo(1, Seq.empty)
      val serverState = ServerState(serverInfo, currentTerm = 2, votedFor = Some(2))
      val requestVote = RequestVote(term = 3, candidateId = 3, 0, 0)
      val (response, _, _) = Leader.requestVote(requestVote, serverState)
      response match {
        case Vote(term, granted) => granted should be(right = false)
        case _ => fail("A vote was expected here")
      }
    }

    "rejects vote if candidates log is less up to date" in {
      val serverInfo = ServerInfo(1, Seq.empty)
      val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
      val requestVote = RequestVote(term = 3, candidateId = 3, lastLogIndex = 3, lastLogTerm = 2)
      val (response, _, _) = Leader.requestVote(requestVote, serverState)
      response match {
        case Vote(term, granted) => granted should be(right = false)
        case _ => fail("A vote was expected here")
      }
    }

    "reject vote if voted for is null and candidates log is at least as up to date as receivers log" in {
      val serverInfo = ServerInfo(1, Seq.empty)
      val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val (response, _, _) = Leader.requestVote(requestVote, serverState)
      response match {
        case Vote(term, granted) =>
          granted should be(right = false)
        case _ => fail("A vote was expected here")
      }
    }

    "accepts vote if voted for is candidate id and candidates log is at least as up to date as receivers log" in {
      val serverInfo = ServerInfo(1, Seq.empty)
      val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4, votedFor = Some(3))
      val requestVote = RequestVote(term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
      val (response, _, _) = Leader.requestVote(requestVote, serverState)
      response match {
        case Vote(term, granted) =>
          granted should be(right = false)
        case _ => fail("A vote was expected here")
      }
    }
  }

  "when replicating entries" - {

  }
}
