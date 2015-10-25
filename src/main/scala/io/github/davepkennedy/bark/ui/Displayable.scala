package io.github.davepkennedy.bark.ui

/**
 * Created by dave on 24/10/2015.
 */
trait Displayable {
  def display (id: Int,
               name: String,
               leader: Boolean,
               currentTerm: Int,
               commitIndex: Int,
               votedFor: Option[Int],
               votes: Int,
               heartbeat: Long)
}
