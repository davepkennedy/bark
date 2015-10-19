package io.github.davepkennedy.bark

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import io.github.davepkennedy.bark.state.{ActorState, Follower}
import io.github.davepkennedy.bark.ui.Display

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object BarkActor {
  def props (display: ActorRef, id: Int) = Props (classOf[BarkActor], display, id)

  case class Bootstrap (peers: Seq[ActorRef])
}

class BarkActor (display: ActorRef, id: Int) extends Actor with ActorLogging {
  import context._

  import BarkActor._

  override def receive: Receive = {
    case Bootstrap (peers) =>
      display ! Display.PeerState (id, s"Actor $id", leader = false, 0, 0, votedFor = None, votes = 0, heartbeat = 0)
      system.scheduler.scheduleOnce(Follower.nextTick, self, Ticker)
      val state = ServerState (serverInfo = ServerInfo (id, peers))

      become(barkFSM(state, Follower))
  }

  def barkFSM (serverState: ServerState, actorState: ActorState): Receive = {
    case Ping =>
      log.info(s"Got ping from $sender at $now")
      sender ! Pong
    case Pong =>
      log.info(s"Got pong from $sender at $now")
    case Ticker =>
      val (requestOption, newServerState, newActorState) = actorState.timerTick(serverState)

      requestOption foreach {
        request =>
        serverState.serverInfo.peers foreach {
          peer => peer ! request
        }
      }

      //newActorState.showOn(display, serverState)
      become(barkFSM(newServerState, newActorState))
      system.scheduler.scheduleOnce(actorState.nextTick, self, Ticker)

    case appendEntries: AppendEntries =>
      val origin = sender()
      val (response, newServerState, newActorState) = actorState.appendEntries(appendEntries, serverState)
      origin ! response
      newActorState.showOn(display, newServerState)
      become (barkFSM(newServerState, newActorState))
    case requestVote: RequestVote=>
      val origin = sender()
      val (response, newServerState, newActorState) = actorState.requestVote(requestVote, serverState)
      origin ! response
      newActorState.showOn(display, newServerState)
      become (barkFSM(newServerState, newActorState))
    case vote: Vote =>
      val origin = sender()
      val (newServerState, newActorState) = actorState.vote(vote, serverState)
      newActorState.showOn(display, newServerState)
      become (barkFSM(newServerState, newActorState))
  }
}
