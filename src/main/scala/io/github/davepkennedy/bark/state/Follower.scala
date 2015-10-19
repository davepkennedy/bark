package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark._

object Follower extends ActorState {
  import ActorState._

  protected def isLeader = false
  protected def namePrefix = "Follw."

  def timerTick (serverState: ServerState): (Option[Request], ServerState, ActorState) = {
    if (now - serverState.serverInfo.lastHeartbeat > StateTimeout) {
      LOGGER.info(s"Follower ${serverState.serverInfo.id} progressing to Candidate")
      val newState = serverState.copy (
        currentTerm = serverState.currentTerm + 1
        , votedFor = Some(serverState.serverInfo.id)
        , serverInfo = serverState.serverInfo.copy(
          votesReceived = 1
        )
      )
      val request = RequestVote (newState.currentTerm,
        serverState.serverInfo.id,
        serverState.lastApplied,
        serverState.currentTerm)
      (Some(request), newState, Candidate)
    } else {
      (None, serverState, Follower)
    }
  }

  def vote (vote: Vote, serverState: ServerState): (ServerState, ActorState) = {
    LOGGER.info("Follower receiver a vote - ignoring")
    (serverState, this)
  }

  override def requestVote(voteRequest: RequestVote,
                           serverState: ServerState): (Response, ServerState, ActorState) = {
    val rejectingState = serverState.copy(
      serverInfo = serverState.serverInfo.copy(
        lastHeartbeat = now
      )
    )

    if (voteRequest.term < serverState.currentTerm) {
      LOGGER.info("Follower {} rejecting vote due to term match", serverState.serverInfo.id)
      (rejectVote(serverState.currentTerm), rejectingState, this)
    } else if (serverState.votedFor.getOrElse(voteRequest.candidateId) == voteRequest.candidateId &&
      voteRequest.lastLogIndex >= serverState.lastApplied
    ) {
      val newState = serverState.copy(
        votedFor = Some(voteRequest.candidateId)
        , serverInfo = serverState.serverInfo.copy(lastHeartbeat = now)
      )
      LOGGER.info(s"Follower ${serverState.serverInfo.id} has voted for Candidate ${voteRequest.candidateId}")
      (acceptVote(serverState.currentTerm), newState, this)
    } else {
      LOGGER.info(s"Follower ${serverState.serverInfo.id} r" +
        s"ejecting vote for ${voteRequest.candidateId} due id mismatch or log state. " +
        s"Voted for ${serverState.votedFor}")
      (rejectVote(serverState.currentTerm), rejectingState, this)
    }
  }

  override def appendEntries (appendEntries: AppendEntries,
                              serverState: ServerState): (Response, ServerState, ActorState) = {
    if (appendEntries.term < serverState.currentTerm) {
      LOGGER.info("Follower {} rejecting entries due to term match", serverState.serverInfo.id)
      (rejectEntries(serverState.currentTerm), serverState, this)
    } else if (appendEntries.prevLogIndex > serverState.log.length) {
      LOGGER.info("Follower {} rejecting entries due to log index match", serverState.serverInfo.id)
      (rejectEntries(serverState.currentTerm), serverState, this)
    }
    else if (serverState.log.length < appendEntries.prevLogIndex &&
      serverState.log(appendEntries.prevLogIndex).term != appendEntries.prevLogTerm)
    {
      LOGGER.info("Follower {} rejecting entries due to log term match", serverState.serverInfo.id)
      (rejectEntries(serverState.currentTerm), serverState, this)
    }
    else {
      /*
      serverState.log ++= appendEntries.entries map {
        e => LogEntry (appendEntries.term, e)
      }
      */
      //LOGGER.info("Follower {} accepting entries", serverState.serverInfo.id)
      (acceptEntries(appendEntries.term),
        serverState.copy(
          serverInfo = serverState.serverInfo.copy(lastHeartbeat = now),
          currentTerm = appendEntries.term,
          commitIndex = appendEntries.leaderCommit min (serverState.log.length-1),
          votedFor = Some(appendEntries.leaderId)
        ),
        this)
    }
  }
}
