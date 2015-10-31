package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark._
import io.github.davepkennedy.bark.ui.Displayable

trait Leader extends RaftActor {
  this: Displayable with TimeSource =>

  private def displayMe(data: LeaderData): Unit = {
    display(id,
      "Leader",
      leader = true,
      data.currentTerm,
      data.log.lastCommitted,
      votedFor = data.votedFor,
      data.peers.size,
      data.lastTick)
  }

  when(LeaderState) {
    //case Event (Vote(term, granted), data: LeaderData) =>
    //  stay using data

    case Event (Tick, data: LeaderData) =>
      displayMe(data)
      if (shouldRetire) {
        goto (FollowerState) using FollowerData (lastTick = now,
          currentTerm = data.currentTerm,
          peers = data.peers,
          log = data.log)
      }
      else {
        data.peers foreach {
          case (peerId,peer) if peerId != id =>
            peer ! AppendEntries (data.currentTerm, id, 0, 0, Array.empty, data.log.lastApplied)
          case _ =>
        }
        stay using data
      }

    case Event (requestVote: RequestVote, data: LeaderData) =>
      if (shouldAcceptVote (requestVote, data)) {
        log.info("Leader {} is voting for {}", id, requestVote.candidateId)
        sender ! acceptVote(data.currentTerm)
        goto (FollowerState) using FollowerData (lastTick = now,
          currentTerm = data.currentTerm,
          votedFor = Some(requestVote.candidateId),
          peers = data.peers,
          log = data.log)
      } else {
        sender ! rejectVote(data.currentTerm)
        stay using data.copy(lastTick = now)
      }

    case Event (appendEntries: AppendEntries, data: LeaderData) =>
      sender ! rejectEntries(data.currentTerm)
      stay using data
  }

}
