package roulette.ecs

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import display.ecs._
import display.ecs.fx._
import display.io._
import monix.eval.Task
import monix.execution.Cancelable
import monix.execution.cancelables.SerialCancelable
import monix.reactive.{Observable, Observer}
import roulette.Event.SpinCompleted
import roulette.{Event, State}
import rx.{Ctx, Rx, Var}

class BillboardSceneRB(seed: State) extends Scene[Event, State]("billboard2") {

  implicit def owner: Ctx.Owner = Ctx.Owner.Unsafe

  val state: Var[State] = Var(seed)

  override def bind(writer: Observer[State], reader: Observable[Event])(implicit scene: SceneContext): Unit = try {
    scene.loader.loadScene("MainScene")


    //Rx Level 0
    val spinResults = state.map(x => x.history)
    val maxSpins = state.map(x => x.maxSpinCount)
    val name = state.map(x => x.name)
    val min = state.map(x => x.min)
    val max = state.map(x => x.max)
    //Rx Level 1
    val lastWinNumber = spinResults.map(x => x.headOption.getOrElse(""))
    val spinHistory = spinResults.map(x => x.take(maxSpins()))
    val spinCount = Rx(if (spinHistory().isEmpty) maxSpins() else spinHistory().length)
    val blackNumbers = List(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35)
    val redNumbers = List(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
    val evenNumbers = List.range(2, 37, 2)
    val oddNumbers = List.range(1, 36, 2)

    //  val zeroSymbols = List(" 0", "00")
    val zeroSymbols = List(" 0")
    val blackSymbols = blackNumbers.map(i => (" " + i.toString).takeRight(2))
    val redSymbols = redNumbers.map(i => (" " + i.toString).takeRight(2))
    val evenSymbols = evenNumbers.map(i => (" " + i.toString).takeRight(2))
    val oddSymbols = oddNumbers.map(i => (" " + i.toString).takeRight(2))
    val symbols1to18 = List.range(1, 19).map(i => (" " + i.toString).takeRight(2))
    val symbols19to36 = List.range(19, 37).map(i => (" " + i.toString).takeRight(2))
    val symbols1to12 = List.range(1, 13).map(i => (" " + i.toString).takeRight(2))
    val symbols13to24 = List.range(13, 25).map(i => (" " + i.toString).takeRight(2))
    val symbols25to36 = List.range(25, 37).map(i => (" " + i.toString).takeRight(2))
    val symbolToInt = (blackSymbols ++ redSymbols ++ zeroSymbols)
      .groupBy(s => s)
      .mapValues(li => li.head.trim().toInt)
    val evenCount = spinHistory.map(x => x.count(m => evenSymbols.contains(m)))
    val oddCount = spinHistory.map(x => x.count(m => oddSymbols.contains(m)))
    val zeroCount = spinHistory.map(x => x.count(m => zeroSymbols.contains(m)))
    val redCount = spinHistory.map(x => x.count(m => redSymbols.contains(m)))
    val blackCount = spinHistory.map(x => x.count(m => blackSymbols.contains(m)))
    val oneTo18Count = spinHistory.map(x => x.count(m => symbols1to18.contains(m)))
    val nineteenTo36Count = spinHistory.map(x => x.count(m => symbols19to36.contains(m)))
    val oneTo12Count = spinHistory.map(x => x.count(m => symbols1to12.contains(m)))
    val thirteenTo24Count = spinHistory.map(x => x.count(m => symbols13to24.contains(m)))
    val twentyFiveTo36Count = spinHistory.map(x => x.count(m => symbols25to36.contains(m)))
    val greenPercentage = Rx("%d%%".format((100 * zeroCount()) / spinCount()))
    val oddPercentage = Rx("%d%%".format((100 * oddCount()) / spinCount()))
    val evenPercentage = Rx("%d%%".format((100 * evenCount()) / spinCount()))
    val redPercentage = Rx("%d%%".format((100 * redCount()) / spinCount()))
    val blackPercentage = Rx("%d%%".format((100 * blackCount()) / spinCount()))
    val oneTo12Percentage = Rx("%d%%".format((100 * oneTo12Count()) / spinCount()))
    val thirteenTo24Percentage = Rx("%d%%".format((100 * thirteenTo24Count()) / spinCount()))
    val twentyFiveTo36Percentage = Rx("%d%%".format((100 * twentyFiveTo36Count()) / spinCount()))
    val oneTo18Percentage = Rx("%d%%".format((100 * oneTo18Count()) / spinCount()))
    val nineteenTo36Percentage = Rx("%d%%".format((100 * nineteenTo36Count()) / spinCount()))

    val symbols = redSymbols ++ blackSymbols ++ zeroSymbols
    val emptyCounts = symbols.map(_ -> 0).toMap
    val liveCounts = spinHistory.map(xs => (emptyCounts ++ xs.groupBy(s => s).mapValues(_.size)).toSeq.sortBy(_._2))
    val hot = liveCounts.map(_.takeRight(4))
    val cold = liveCounts.map(_.take(4))

    val minWidth = 7.2
    val position0: Float = 310
    val red = Rx(100 * redCount() / spinCount() * minWidth)
    val odd = Rx(100 * oddCount() / spinCount() * minWidth)
    val oneTo18 = Rx(100 * oneTo18Count() / spinCount() * minWidth)
    val green = Rx(100 * zeroCount() / spinCount() * minWidth)


    val target = Var(Option.empty[EditText])
    val spinEdit = EditText(scene.root / "maxWin", 3, Var(true))
    val nameEdit = EditText(scene.root / "name", 10, Var(true))
    val minEdit = EditText(scene.root / "min", 5, Var(true))
    val maxEdit = EditText(scene.root / "max", 5, Var(true))

    (scene.root / "maxWin").label.setText(maxSpins.now.toString)
    (scene.root / "name").label.setText(name.now)
    (scene.root / "min").label.setText(min.now.toString)
    (scene.root / "max").label.setText(max.now.toString)

    oddPercentage.map(x => Rx(x).writes((scene.root / "oddPercentage").label))
    evenPercentage.map(x => Rx(x).writes((scene.root / "evenPercentage").label))

    redPercentage.map(x => Rx(x).writes((scene.root / "redPercentage").label))
    blackPercentage.map(x => Rx(x).writes((scene.root / "blackPercentage").label))

    oneTo18Percentage.map(x => Rx(x).writes((scene.root / "oneTo18Percentage").label))
    nineteenTo36Percentage.map(x => Rx(x).writes((scene.root / "nineteenTo36Percentage").label))

    import display.ecs.fx._
    import scala.concurrent.duration._
    val duration = 3.seconds
    val entity = scene.root / "lastWinNumber"
    val effect = entity.tint.fade(0f, Interpolation.bounceOut, duration).doOnFinish(_ => Task.evalOnce(entity.tint.color.a = 1f))

    reader.foreach  {
      case e: SpinCompleted =>
        effect.runAsync
        Task.evalOnce(state() = state.now.transition(e)).delayExecution(duration).runAsync
      case _ =>
    }

    spinResults.trigger {

      //Forward new state to UI
      writer.onNext(state.now)

      //Update LastWin Label
      updateEntity(scene.root / "lastWinNumber", lastWinNumber.now, getColor(lastWinNumber.now))

      (scene.root / "red").dimensions.width = red.now.toFloat
      (scene.root / "green1").transform.x = position0 + red.now.toFloat
      (scene.root / "green1").dimensions.width = green.now.toFloat

      (scene.root / "green2").transform.x = position0 + odd.now.toFloat
      (scene.root / "green2").dimensions.width = green.now.toFloat

      (scene.root / "green3").transform.x = position0 + oneTo18.now.toFloat
      (scene.root / "green3").dimensions.width = green.now.toFloat

    }

    hot.map(m => m.zipWithIndex
      .foreach {
        case ((x, y), z) =>
          val index = 4 - z
          (scene.root / s"hc$index").label.setText(s"$y")
          updateEntity(scene.root / s"h$index", x, getColor(x))
      })

    cold.map(m => m.zipWithIndex
      .foreach {
        case ((x, y), z) =>
          val index = z + 1
          (scene.root / s"cc$index").label.setText(s"$y")
          updateEntity(scene.root / s"c$index", x, getColor(x))
      })

    for (i <- 1 to 15) {
      disableEntity(scene.root / s"b$i")
      disableEntity(scene.root / s"r$i")
      disableEntity(scene.root / s"g$i")
    }
    spinResults.map(m => m.take(16).foldLeft(List[(String, String)]()) { (result, x) =>
      x match {
        case b if blackSymbols.contains(b) => if (result.nonEmpty) result.:+((x, "black")) else List((x, "black"))
        case r if redSymbols.contains(r) => if (result.nonEmpty) result.:+((x, "red")) else List((x, "red"))
        case _ => if (result.nonEmpty) result.:+((x, "green")) else List((x, "green"))
      }
    }.zipWithIndex foreach {
      case (e, i) =>
        (i, e._1, e._2) match {
          case (0, _, _) =>
          case (j, num, "black") =>
            disableEntity(scene.root / s"r$j")
            disableEntity(scene.root / s"g$j")
            updateEntity(scene.root / s"b$j", num, getColor(num))
          case (j, num, "red") =>
            disableEntity(scene.root / s"b$j")
            disableEntity(scene.root / s"g$j")
            updateEntity(scene.root / s"r$j", num, getColor(num))
          case (j, num, "green") =>
            disableEntity(scene.root / s"r$j")
            disableEntity(scene.root / s"b$j")
            updateEntity(scene.root / s"g$j", num, getColor(num))
          case (_, _, _) =>
        }
    })

    nameEdit.checked.trigger {
      val current = name.now
      nameEdit.checked.foreach(c => target() = if (c) Some(nameEdit) else None)
      nameEdit.checked.map(if (_) 0.5f else 1f).foreach(nameEdit.entity.tint.color.a = _)
      val keying = SerialCancelable()
      val keys = scene.inputs.collect {
        case KeyTyped(key) => key.toString
      }
      target.foreach(t =>
        keying := t.fold(Cancelable.empty)(e =>
          keys.takeWhile(_ != "\n")
            .doOnTerminate(_ => scene.input() = InputEmpty)
            .scan("")(_.takeRight(e.size - 1) + _).foreach(e.entity.label.setText)))

      val updated = (scene.root / "name").label.text.toString
      updated match {
        case x if x == "" => (scene.root / "name").label.setText(s"$current")
        case _ => state() = state.now.transition(Event.NameChanged(updated))
      }
    }


    spinEdit.checked.trigger {
      val current = maxSpins.now
      spinEdit.checked.foreach(c => target() = if (c) Some(spinEdit) else None)
      spinEdit.checked.map(if (_) 0.5f else 1f).foreach(spinEdit.entity.tint.color.a = _)
      val keying = SerialCancelable()
      val keys = scene.inputs.collect {
        case KeyTyped(key) => key.toString
      }
      target.foreach(t =>
        keying := t.fold(Cancelable.empty)(e =>
          keys.takeWhile(_ != "\n")
            .doOnTerminate(_ => scene.input() = InputEmpty)
            .scan("")(_.takeRight(e.size - 1) + _).foreach(e.entity.label.setText)))

      val updated = (scene.root / "maxWin").label.text.toString
      updated match {
        case x if x == "" => (scene.root / "maxWin").label.setText(s"$current")
        case x if (x forall Character.isDigit) && (x.toInt >= 15)
        => state() = state.now.transition(Event.MaxSpinChanged(updated.toInt))
        case _ => (scene.root / "maxWin").label.setText(s"$current")
      }
    }


    minEdit.checked.trigger {
      val current = min.now
      minEdit.checked.foreach(c => target() = if (c) Some(minEdit) else None)
      minEdit.checked.map(if (_) 0.5f else 1f).foreach(minEdit.entity.tint.color.a = _)
      val keying = SerialCancelable()
      val keys = scene.inputs.collect {
        case KeyTyped(key) => key.toString
      }
      target.foreach(t =>
        keying := t.fold(Cancelable.empty)(e =>
          keys.takeWhile(_ != "\n")
            .doOnTerminate(_ => scene.input() = InputEmpty)
            .scan("")(_.takeRight(e.size - 1) + _).foreach(e.entity.label.setText)))

      val updated = (scene.root / "min").label.text.toString
      updated match {
        case x if x == "" => (scene.root / "min").label.setText(s"$current")
        case x if (x forall Character.isDigit) && (x.toInt > 0)
        => state() = state.now.transition(Event.MinChanged(updated.toInt))
        case _ => (scene.root / "min").label.setText(s"$current")
      }
    }


    maxEdit.checked.trigger {
      val current = max.now
      maxEdit.checked.foreach(c => target() = if (c) Some(maxEdit) else None)
      maxEdit.checked.map(if (_) 0.5f else 1f).foreach(maxEdit.entity.tint.color.a = _)
      val keying = SerialCancelable()
      val keys = scene.inputs.collect {
        case KeyTyped(key) => key.toString
      }
      target.foreach(t =>
        keying := t.fold(Cancelable.empty)(e =>
          keys.takeWhile(_ != "\n")
            .doOnTerminate(_ => scene.input() = InputEmpty)
            .scan("")(_.takeRight(e.size - 1) + _).foreach(e.entity.label.setText)))

      val updated = (scene.root / "max").label.text.toString
      updated match {
        case x if x == "" => (scene.root / "max").label.setText(s"$current")
        case x if (x forall Character.isDigit) && (x.toInt > 0)
        => state() = state.now.transition(Event.MaxChanged(updated.toInt))
        case _ => (scene.root / "max").label.setText(s"$current")
      }
    }


    def disableEntity(e: Entity): Unit = {
      e.item.visible = false
    }

    def updateEntity(e: Entity, x: String, col: Color): Unit = {
      e.item.visible = true
      (e / "number").label.setText(x)
      (e / "color").tint.color = col
    }

    def getColor(s: String): Color = {
      s match {
        case x if blackSymbols.contains(x) => Color.BLACK
        case x if redSymbols.contains(x) => Color.RED
        case _ => Color.GREEN
      }
    }

  } catch {
    case t: Throwable => t.printStackTrace()
  }
}

case class EditText(entity: Entity, size: Int, enabled: Rx[Boolean])(implicit owner: Ctx.Owner, scene: SceneContext) {
  val checked: Rx.Dynamic[Boolean] = scene.input.fold(false) {
    case (prev, e: PointerUp) => if (enabled.now && entity.occupies(e.x, e.y)) !prev else prev
    case (_, InputEmpty) => false
    case (prev, _) => prev
  }
}


object BillboardSceneRB {
  def apply(seed: State): BillboardSceneRB = new BillboardSceneRB(seed)
}