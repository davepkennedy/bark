package io.github.davepkennedy.bark.state

import akka.actor.ActorRef
import io.github.davepkennedy.bark.{Vote, RequestVote, ServerState, ServerInfo}
import org.scalatest.{FreeSpec, Matchers}

class CandidateSpec extends FreeSpec with Matchers {
  "A candidate" - {
    "when receiving RequestVote" - {
      "rejects all votes when candidate id is not its own" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote (term = 4, candidateId = 3, lastLogIndex = 5, lastLogTerm = 3)
        val (response, _, _) = Candidate.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) => granted should be (right = false)
        }
      }

      /*
      A candidate should have already counted its vote when changing from Follower to Candidate
      Voting for itself this way would give an extra vote.
       */
      "rejects all votes when candidate id is its own" in {
        val serverInfo = ServerInfo (1, Seq.empty)
        val serverState = ServerState(serverInfo = serverInfo, currentTerm = 3, lastApplied = 4)
        val requestVote = RequestVote (term = 4, candidateId = 1, lastLogIndex = 5, lastLogTerm = 3)
        val (response, _, _) = Candidate.requestVote(requestVote, serverState)
        response match {
          case Vote (term, granted) => granted should be (right = false)
        }
      }
    }
  }
}
