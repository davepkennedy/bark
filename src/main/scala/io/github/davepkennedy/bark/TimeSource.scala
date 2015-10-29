package io.github.davepkennedy.bark

/**
 * Created by dave on 26/10/2015.
 */
trait TimeSource {
  def now: Long
}

trait SystemTime extends TimeSource {
  def now: Long = System.currentTimeMillis()
}