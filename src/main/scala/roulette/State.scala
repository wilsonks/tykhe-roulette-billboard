package roulette

import roulette.Event._


sealed trait State extends Product with Serializable {
  type Transition = Event => State

  def name: String

  def history: Seq[String]

  def maxSpinCount: Int

  def min: Int

  def max: Int

  def stage: Int

  def transition: Transition
}


object State {

  case class Running(name: String, history: Seq[String], maxSpinCount: Int, min: Int, max: Int, stage: Int) extends State {
    def transition: Transition = {
      case SpinCompleted(num) => copy(history = (num +: history).take(maxSpinCount))
      case NameChanged(next) => copy(name = next)
      case MaxChanged(next) => copy(max = next)
      case MinChanged(next) => copy(min = next)
      case MaxSpinChanged(next) => copy(maxSpinCount = next)
      case StatusChanged(next) => copy(stage = next)
      case _ => copy()
    }

  }

}

sealed trait Event extends Product with Serializable

object Event {

  case class SpinCompleted(num: String) extends Event

  case class NameChanged(name: String) extends Event

  case class MinChanged(value: Int) extends Event

  case class MaxChanged(value: Int) extends Event

  case class MaxSpinChanged(value: Int) extends Event

  case class StatusChanged(value: Int) extends Event

}