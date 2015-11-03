package io.github.davepkennedy.bark.state

final case class LogEntry (term: Int, index: Int, data: Array[Byte])

class Log {

  private var commited = 0
  private var entries: Array[LogEntry] = Array.empty

  def add (term: Int, data: Array[Byte]): Unit = {
    entries = entries :+ LogEntry(term, entries.length + 1, data)
  }

  def hasEntryAt (prevLogIndex: Int, prevLogTerm: Int): Boolean = {
    if (prevLogIndex == 0 && prevLogTerm == 0) {
      true
    }
    else {
      entries.find {
        entry => entry.index == prevLogIndex
      }.exists { e => e.term == prevLogTerm }
    }
  }

  private def removeConflicts (newEntries: Seq[LogEntry]): Unit = {
    newEntries foreach {
      newEntry =>
        entries = entries.filterNot {
          entry => entry.index >= newEntry.index && entry.term != newEntry.term
        }
    }
  }

  def append (newEntries: Seq[LogEntry]): Unit = {
    removeConflicts(newEntries)
    entries ++= newEntries
  }

  def commitTo (leaderCommit: Int): Unit = {
    commited = leaderCommit
  }

  def between (start: Int, end: Int): Seq[LogEntry] = {
    entries.filter {
      entry =>
        entry.index >= start && entry.index <= end
    }
  }

  def lastApplied: Int = {
    if (entries.isEmpty) 0
    else entries.maxBy (_.index).index
  }

  def lastCommitted: Int = {
    commited
  }

  def lastTerm: Int = {
    if (entries.isEmpty) 0
    else {
      val lastIndex = lastApplied
      entries.find {
        entry => entry.index == lastIndex
      }.map {e => e.term}.getOrElse(0)
    }
  }


  override def toString = s"Log(Comitted: $commited, Entries: $entries, Applied: $lastApplied, Last Term: $lastTerm)"
}
