package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark._

object Candidate extends ActorState {
  import ActorState._

  import io.github.davepkennedy.bark._

  protected def isLeader = false
  protected def namePrefix = "Cand. "

  def timerTick (serverState: ServerState): (Option[Request], ServerState, ActorState) = {
    if (now - serverState.serverInfo.lastHeartbeat > StateTimeout) {
      LOGGER.info(s"Candidate ${serverState.serverInfo.id} only received ${serverState.serverInfo.votesReceived} - reverting to follower")
      val newState = serverState.copy(
        serverInfo = serverState.serverInfo.copy(
          votesReceived = 0
        ),
        votedFor = None,
        currentTerm = serverState.currentTerm - 1
      )
      (None, newState, Follower)
    } else {
      (None, serverState, Candidate)
    }
  }

  def vote (vote: Vote, serverState: ServerState): (ServerState, ActorState) = {
    val newState = serverState.copy(
      serverInfo = serverState.serverInfo.copy(
        votesReceived = serverState.serverInfo.votesReceived + (if (vote.voteGranted) 1 else 0)
      ))

    //LOGGER.info(s"Candidate ${newState.serverInfo.id} now has ${newState.serverInfo.votesReceived} votes")
    if (newState.serverInfo.votesReceived >= (serverState.serverInfo.peers.length / 2) + 1) {
      LOGGER.info(s"Candidate {} progressing to Leader with {} votes",
        newState.serverInfo.id, newState.serverInfo.votesReceived)
      (newState, Leader)
    } else {
      (newState, Candidate)
    }
  }

  override def requestVote(voteRequest: RequestVote,
                           serverState: ServerState): (Response, ServerState, ActorState) = {
    /*
    This candidate should have already voted for itself and should never vote for another
     */
    val newState = serverState.copy(serverInfo = serverState.serverInfo.copy(lastHeartbeat = now))
    (rejectVote(serverState.currentTerm), newState, this)
    /*
    val rejectingState = serverState.copy(
      serverInfo = serverState.serverInfo.copy(
        lastHeartbeat = now
      )
    )

    if (voteRequest.term < serverState.currentTerm) {
      LOGGER.info("Candidate {} rejecting vote due to term match", serverState.serverInfo.id)
      (rejectVote(serverState.currentTerm), rejectingState, this)
    } else if (serverState.votedFor.getOrElse(voteRequest.candidateId) == voteRequest.candidateId &&
      voteRequest.lastLogIndex >= serverState.lastApplied
    ) {
      val newState = serverState.copy(
        votedFor = Some(voteRequest.candidateId)
        , serverInfo = serverState.serverInfo.copy(lastHeartbeat = now)
      )
      LOGGER.info(s"Candidate ${serverState.serverInfo.id} has voted for Candidate ${voteRequest.candidateId}")
      (acceptVote(serverState.currentTerm), newState, this)
    } else {
      LOGGER.info(s"Candidate ${serverState.serverInfo.id} r" +
        s"ejecting vote for ${voteRequest.candidateId} due id mismatch or log state. " +
        s"Voted for ${serverState.votedFor}")
      (rejectVote(serverState.currentTerm), rejectingState, this)
    }
    */
  }

  override def appendEntries(appendEntries: AppendEntries,
                             serverState: ServerState): (Response, ServerState, ActorState) = {
    //LOGGER.info("Candidate {} received AppendEntries - reverting to follower", serverState.serverInfo.id)
    val newState = serverState.copy(serverInfo = serverState.serverInfo.copy(lastHeartbeat = now))
    (acceptEntries(appendEntries.term), newState, Follower)
  }
}
