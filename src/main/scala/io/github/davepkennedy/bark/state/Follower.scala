package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark.ui.Displayable


trait Follower extends RaftActor {
  this: Displayable =>

  when(FollowerState) {
    case Event (requestVote: RequestVote, data: FollowerData) =>
      if (shouldAcceptVote (requestVote, data)) {
        log.info("Follower {} is voting for {}", id, requestVote.candidateId)
        sender ! acceptVote(data.currentTerm)
        stay using data.copy (
          lastTick = now,
          votedFor = Some(requestVote.candidateId)
        )
      } else {
        sender ! rejectVote(data.currentTerm)
        stay using data.copy(lastTick = now)
      }
    //case Event (vote: Vote, data: FollowerData) =>
    //  stay using data.copy(lastTick = now)
    case Event (appendEntries: AppendEntries, data: FollowerData) =>
      // Not actually appending entries yet…
      if (appendEntries.term >= data.currentTerm) {
        sender ! acceptEntries(data.currentTerm)
        stay using data.copy(lastTick = now)
      } else {
        sender ! rejectEntries(data.currentTerm)
        stay using data.copy(lastTick = now)
      }
    case Event (Tick, data: FollowerData) =>
      display(id,
        "Follw.",
        leader = false,
        data.currentTerm,
        data.commitIndex,
        votedFor = data.votedFor,
        0,
        data.lastTick)

      if ((now - data.lastTick) > RaftActor.StateTimeout) {
        // Trigger a vote when converting to Candidate
        self ! Tick
        goto (CandidateState) using CandidateData (
          lastTick = now,
          currentTerm = data.currentTerm,
          votesGranted = 0,
          votedFor = None,
          peers = data.peers,
          commitIndex = data.commitIndex,
          lastApplied = data.lastApplied)
      } else {
        stay using data
      }
  }
}
