package io.github.davepkennedy.bark

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import io.github.davepkennedy.bark.state._
import io.github.davepkennedy.bark.ui.{Displayable, Display}
import org.apache.log4j.BasicConfigurator

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object BarkStateManager {
  def props(id: Int, display: ActorRef) = Props(classOf[BarkStateManager], id, display)

  case class Bootstrap (peers: Seq[ActorRef])
}

class BarkStateManager (val id: Int, display: ActorRef) extends Candidate with Follower with Leader with Displayable with SystemTime {
  import BarkStateManager._
  import context._

  override def display(id: Int,
                       name: String,
                       leader: Boolean,
                       currentTerm: Int,
                       commitIndex: Int,
                       votedFor: Option[Int],
                       votes: Int, heartbeat: Long): Unit = {
    display ! Display.PeerState (id, name, leader, currentTerm, commitIndex, votedFor, votes, heartbeat)
  }

  override def shouldRetire: Boolean = chance(1)

  startWith(BootState, FollowerData (now))

  when (BootState) {
    case Event (Bootstrap (peers), data: FollowerData) =>
      goto (FollowerState) using data.copy(peers = peers)
  }


  def scheduleTick (): Unit = {
    system.scheduler.scheduleOnce(randomInterval) {
      self ! Tick
      scheduleTick()
    }
  }

  scheduleTick()
}

object BarkApplication {
  val actorSystem = ActorSystem ("bark")

  def main (args: Array[String]): Unit = {
    BasicConfigurator.configure()
    
    val display = actorSystem.actorOf(Display.props)

    val peers = for (i <- 0 until 27) yield actorSystem.actorOf(BarkStateManager.props(i, display), s"Actor-$i")
    peers foreach {
      peer => peer ! BarkStateManager.Bootstrap (peers)
    }

    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }
}
