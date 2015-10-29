package io.github.davepkennedy.bark.state

import java.nio.ByteBuffer

import org.scalatest.{Matchers, FreeSpec}

class LogSpec extends FreeSpec with Matchers {

  "When adding entries" - {
    "the log should start empty" in {
      val log = new Log
      log.lastApplied should be (0)
      log.lastCommitted should be (0)
    }

    "the log should increase applied when adding an entry" in {
      val log = new Log
      log.add(1, bytesFrom (1))
      log.lastApplied should be (1)
    }

    "the log should not change the commit index when adding an entry" in {
      val log = new Log
      log.add(1, bytesFrom (1))
      log.lastCommitted should be (0)
    }

    "the log should append entries" in {
      val log = new Log
      log.add (1, bytesFrom(1))
      log.add (1, bytesFrom(2))

      val newEntries = for (i <- 10 to 15) yield LogEntry (2, i, bytesFrom(i))
      log.append(newEntries)
      log.lastApplied should be (15)
    }

    "the log should remove a conflicting entry and every entry after it" in {
      val log = new Log
      log.add (1, bytesFrom(1))
      log.add (1, bytesFrom(2))
      log.add (1, bytesFrom(3))

      log.lastApplied should be (3)

      val newEntries = Seq(LogEntry (term = 2, index = 2, bytesFrom(22)))
      log.append (newEntries)

      log.lastApplied should be (2)
    }
  }
}
