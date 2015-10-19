package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark._

object Leader extends ActorState {
  import ActorState._

  protected def isLeader = true
  protected def namePrefix = "Leader"

  def timerTick (serverState: ServerState): (Option[Request], ServerState, ActorState) = {
    //LOGGER.info("Leader {} must heartbeat to followers", serverState.serverInfo.id)

    if (chance(1)) {
      val newState = serverState.copy(
        serverInfo = serverState.serverInfo.copy(votesReceived = 0)
      )
      (None, serverState, Follower)
    } else {
      val request = AppendEntries(serverState.currentTerm, serverState.serverInfo.id,
        0, 0, Array.empty[Entry], 0)
      (Some(request), serverState, this)
    }
  }

  def vote (vote: Vote, serverState: ServerState): (ServerState, ActorState) = {
    LOGGER.info("Leader receiver a vote - ignoring")
    (serverState, this)
  }

  override def requestVote(voteRequest: RequestVote,
                           serverState: ServerState): (Response, ServerState, ActorState) =
    (rejectVote(serverState.currentTerm), serverState, this)

  override def appendEntries(appendEntries: AppendEntries,
                             serverState: ServerState): (Response, ServerState, ActorState) =
    (rejectEntries(serverState.currentTerm), serverState, this)
}
