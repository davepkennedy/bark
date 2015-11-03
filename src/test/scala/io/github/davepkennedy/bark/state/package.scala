package io.github.davepkennedy.bark

import java.nio.ByteBuffer

/**
 * Created by dave on 28/10/2015.
 */
package object state {

  def logUpTo (term: Int, maxEntry: Int): Log = {
    val log = new Log
    val entries = for (i <- 1 to maxEntry) yield LogEntry (term, i, bytesFrom(i))
    log.append(entries)
    log
  }
}
