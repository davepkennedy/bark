package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark._
import io.github.davepkennedy.bark.ui.Displayable

trait Candidate extends RaftActor {
  this: Displayable with TimeSource =>

  private def displayMe (data: CandidateData): Unit = {
    display(id,
      "Cand.",
      leader = false,
      data.currentTerm,
      data.log.lastApplied,
      votedFor = data.votedFor,
      data.votesGranted,
      data.lastTick)
  }

  when (CandidateState) {
    case Event (Tick, data: CandidateData) =>
      displayMe(data)
      if (now - data.lastTick > RaftActor.StateTimeout) {
        data.peers foreach {
          case (peerId,peer) if peerId != id =>
            peer ! RequestVote (
              data.currentTerm + 1,
              id,
              data.log.lastApplied,
              data.log.lastTerm)
          case _ =>
        }
        stay using data.copy(
          lastTick = now,
          votesGranted = 1,
          votedFor = Some(id),
          currentTerm = data.currentTerm + 1)
      } else {
        stay using data
      }
    case Event (Vote(voter, term, voteGranted), data: CandidateData) =>
      displayMe(data)
      val votesGranted = data.votesGranted + (if (voteGranted) {1} else {0})
      if (votesGranted >= ((data.peers.size / 2) + 1)) {
        goto (LeaderState) using LeaderData (
          lastTick = now,
          currentTerm = data.currentTerm,
          peers = data.peers,
          log = data.log,
          nextIndex = Array.fill (data.peers.size) {data.log.lastApplied},
          matchIndex = Array.fill (data.peers.size) {0})
      } else {
        stay using data.copy(
          lastTick = now, votesGranted = votesGranted)
      }
    case Event(appendEntries: AppendEntries, data: CandidateData) =>
      if (shouldAcceptEntries(appendEntries, data)) {
        appendEntriesToLog (appendEntries, data)
        sender ! acceptEntries(data.currentTerm)
      } else {
        sender ! rejectEntries(data.currentTerm)
      }
      goto (FollowerState) using FollowerData (
        lastTick = now,
        currentTerm = appendEntries.term,
        peers = data.peers,
        log = data.log)

    case Event(requestVote: RequestVote, data: CandidateData) =>
      displayMe(data)
      if (shouldAcceptVote (requestVote, data)) {
        if (requestVote.candidateId == id) {
          stay using data
        } else {
          sender ! acceptVote(data.currentTerm)
          goto(FollowerState) using FollowerData(
            lastTick = now,
            currentTerm = requestVote.term,
            peers = data.peers,
            log = data.log,
            votedFor = Some(requestVote.candidateId))
        }
      } else {
        sender ! rejectVote(data.currentTerm)
        stay using data.copy(lastTick = now)
      }
  }
}
