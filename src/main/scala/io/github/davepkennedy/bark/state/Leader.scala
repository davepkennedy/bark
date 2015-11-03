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
        /**/
        if (chance (10)) {
          log.info("Leader {} is appending entry to log", id)
          data.log.add(data.currentTerm, bytesFrom(data.log.lastApplied + 1))
        }
        /**/

        data.peers foreach {
          case (peerId,peer) if peerId != id =>
            val nextIndex = data.nextIndex(peerId)
            val entries = data.log.between(nextIndex, data.log.lastApplied)
            peer ! AppendEntries (data.currentTerm, id, 0, 0, entries, data.log.lastApplied)
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

    case Event (vote: Vote, data: LeaderData) =>
      stay using data

    case Event (appendEntries: AppendEntries, data: LeaderData) =>
      sender ! rejectEntries(data.currentTerm, data.log.lastApplied)
      stay using data

    case Event (EntriesAccepted (peerId, term, peerApplied, success), data: LeaderData) =>
      if (success) {
        val newMatchIndex = data.matchIndex.updated(peerId, peerApplied)
        val newNextIndex = data.nextIndex.updated(peerId, data.log.lastApplied + 1)
        val commonIndex = highestCommonIndex(data.log.lastCommitted, newMatchIndex.values.toSeq)
        data.log.commitTo(commonIndex)

        stay using data.copy(matchIndex = newMatchIndex, nextIndex = newNextIndex)
      } else {
        stay using data
      }
  }

  def highestCommonIndex (currentCommit: Int, matchIndices: Seq[Int]): Int = {
    val quorum = (matchIndices.size / 2) + 1
    val commits = matchIndices.map {
      limit => for (i <- currentCommit to limit) yield i
    }
    var commitCounts = Map.empty[Int,Int]
    commits foreach {
      commitRange => commitRange foreach {
        commit =>
          commitCounts = commitCounts.updated(commit, commitCounts.getOrElse(commit, 0) + 1)
      }
    }
    val quorumCommits = commitCounts.filter {
      case (k, v) => v > quorum
    }.keySet

    if (quorumCommits.nonEmpty) {
      quorumCommits.max
    } else {
      0
    }
  }

}
