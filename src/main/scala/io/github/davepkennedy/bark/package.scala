package io.github.davepkennedy

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

package object bark {
  private val random = new Random()

  def randomInterval: FiniteDuration = FiniteDuration(100 + random.nextInt(100), TimeUnit.MILLISECONDS)
  def now = System.currentTimeMillis()
  def chance(pct: Int): Boolean = random.nextInt(100) <= pct

  def bytesFrom (i: Int) = ByteBuffer.allocate(4).putInt(i).array()
}
