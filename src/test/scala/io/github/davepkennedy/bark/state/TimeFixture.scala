package io.github.davepkennedy.bark.state

import io.github.davepkennedy.bark.TimeSource

trait TimeFixture extends TimeSource {
  var time: Long = 0

  def setTime(time: Long): Unit = this.time = time
  def now: Long = time
}
