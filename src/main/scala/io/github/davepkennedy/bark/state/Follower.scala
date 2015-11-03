package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark.TimeSource
import io.github.davepkennedy.bark.ui.Displayable


trait Follower extends RaftActor {
  this: Displayable with TimeSource =>

  when(FollowerState) {
    case Event (requestVote: RequestVote, data: FollowerData) =>
      if (shouldAcceptVote (requestVote, data)) {
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
      if (shouldAcceptEntries(appendEntries, data)) {
        appendEntriesToLog(appendEntries, data)
        sender ! acceptEntries(data.currentTerm, data.log.lastApplied)
      } else {
        sender ! rejectEntries(data.currentTerm, data.log.lastApplied)
      }
      stay using data.copy(lastTick = now)
    case Event (Tick, data: FollowerData) =>
      display(id,
        "Follw.",
        leader = false,
        data.currentTerm,
        data.log.lastApplied,
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
          log = data.log)
      } else {
        stay using data
      }
  }
}
