package io.github.davepkennedy.bark

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import io.github.davepkennedy.bark.ui.Display
import org.apache.log4j.BasicConfigurator

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object BarkApplication {
  val actorSystem = ActorSystem ("bark")

  def main (args: Array[String]): Unit = {
    BasicConfigurator.configure()
    
    val display = actorSystem.actorOf(Display.props)

    val peers = for (i <- 0 until 5) yield actorSystem.actorOf(BarkActor.props(display, i))
    peers foreach {
      peer => peer ! BarkActor.Bootstrap (peers)
    }

    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }
}
