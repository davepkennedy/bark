package io.github.davepkennedy.bark.ui


import akka.actor.{Props, Actor}
import com.googlecode.lanterna.TerminalFacade
import com.googlecode.lanterna.terminal.Terminal
import com.googlecode.lanterna.terminal.Terminal.SGR

private [ui] sealed trait Style {
  def applyStyle(terminal: Terminal): Unit
  def unapplyStyle(terminal: Terminal): Unit

  def foreach[U] (terminal: Terminal) (f: Terminal => U): Unit = {
    applyStyle(terminal)
    val r = f(terminal)
    unapplyStyle(terminal)
  }
}

private [ui] class ColorStyle (color: Terminal.Color) extends Style {
  override def applyStyle(terminal: Terminal): Unit = terminal.applyForegroundColor(color)
  override def unapplyStyle(terminal: Terminal): Unit = terminal.applyForegroundColor(Terminal.Color.DEFAULT)
}

private [ui] class TextStyle (applied: SGR, unapplied: SGR) extends Style {
  override def applyStyle(terminal: Terminal): Unit = terminal.applySGR(applied)
  override def unapplyStyle(terminal: Terminal): Unit = terminal.applySGR(unapplied)
}

object Display {
  case class TextAt (message: String, x: Int, y: Int)
  case class PeerState (id: Int,
                        name: String,
                        leader: Boolean,
                        currentTerm: Int,
                        commitIndex: Int,
                        votedFor: Option[Int],
                        votes: Int,
                        heartbeat: Long)

  def props = Props[Display]

  val Black = new ColorStyle (Terminal.Color.BLACK)
  val Blue = new ColorStyle (Terminal.Color.BLUE)
  val Cyan = new ColorStyle (Terminal.Color.CYAN)
  val Green = new ColorStyle (Terminal.Color.GREEN)
  val Magenta = new ColorStyle (Terminal.Color.MAGENTA)
  val Red = new ColorStyle (Terminal.Color.RED)
  val White = new ColorStyle (Terminal.Color.WHITE)
  val Yellow = new ColorStyle (Terminal.Color.YELLOW)

  val Blink = new TextStyle (Terminal.SGR.ENTER_BLINK, Terminal.SGR.EXIT_BLINK)
  val Bold = new TextStyle (Terminal.SGR.ENTER_BOLD, Terminal.SGR.EXIT_BOLD)
  val Reverse = new TextStyle (Terminal.SGR.ENTER_REVERSE, Terminal.SGR.EXIT_REVERSE)
  val Underline = new TextStyle (Terminal.SGR.ENTER_UNDERLINE, Terminal.SGR.EXIT_UNDERLINE)

  def using (terminal: Terminal, styles: Style*)(f: => Unit): Unit = {
    styles foreach (_.applyStyle(terminal))
    f
    styles foreach (_.unapplyStyle(terminal))
  }

  val ActorColumn = 1
  val TermColumn = 11
  val CommitColumn = 24
  val VotedForColumn = 37
  val VotesColumn = 50
  val HeartbeatColumn = 64
}

class Display extends Actor {
  import Display._

  val terminal = TerminalFacade.createTerminal()



  override def preStart(): Unit = {
    terminal.enterPrivateMode()
    using (terminal, Blue, Bold) {
      textAt("Actor", ActorColumn, 0)
      textAt("Term", TermColumn, 0)
      textAt("Commit", CommitColumn, 0)
      textAt("Voted For", VotedForColumn, 0)
      textAt("Votes", VotesColumn, 0)
      textAt("Heartbeat", HeartbeatColumn, 0)
    }
  }

  override def postStop(): Unit = {
    terminal.exitPrivateMode()
  }

  private def textAt (message: String, x: Long, y: Long): Unit = {
    terminal.moveCursor(x.asInstanceOf[Int], y.asInstanceOf[Int])
    message.take(20) foreach terminal.putCharacter
    terminal.moveCursor(0, 0)
    terminal.flush()
  }

  override def receive: Receive = {
    case TextAt(message, x, y) => textAt(message, x, y)
    case PeerState(id, name, leader, currentTerm, commitIndex, votedFor, votes, heartbeat) =>


      using (terminal, if (leader) Yellow else Red, Bold) {
        textAt(name, ActorColumn, id + 1)
      }
      textAt (f"$currentTerm%6d", TermColumn, id + 1)
      textAt (f"$commitIndex%6d", CommitColumn, id + 1)
      textAt (f"$votedFor%8s", VotedForColumn, id + 1)
      textAt (f"$votes%4d", VotesColumn, id + 1)
      textAt (s"$heartbeat", HeartbeatColumn, id + 1)

  }
}
