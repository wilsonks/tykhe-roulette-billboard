package roulette

import java.util.concurrent.CountDownLatch

import better.files.File
import com.typesafe.config.ConfigFactory
import device.cammegh.slingshot._
import device.io._
import device.io.operators._
import device.io.usb.Pl2303
import display.io.WindowConfig
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import pureconfig._
import roulette.State.Running
import roulette.ecs.{BillboardScene, BillboardSceneRB}
import scodec.bits.ByteVector

import scala.concurrent.duration._

object BillboardApp extends App {

  implicit val scheduler: SchedulerService = Scheduler.fixedPool("usb", 4)
  implicit val usbConfig: UsbConfig = UsbConfig()

  //main thread will wait until gate is open - gate is open when ui thread on complete.
  val latch = new CountDownLatch(1)

  val conf = ConfigFactory.load

  val file = File(conf.getString("persistence.file"))

  val seed: State = if (file.exists) {
    file.readDeserialized[Running]
  } else {
    file.createIfNotExists(asDirectory = false, createParents = true)
    file.writeSerialized(Running("EU-01", Seq.empty[String], 100, 100, 10000))
    file.readDeserialized[Running]
  }

  val config = loadConfigOrThrow[WindowConfig]("window")

  val window = if(conf.getString("app.theme") == "red-black") BillboardSceneRB(seed) else BillboardScene(seed)


  device.io.usb.Hub() match {
    case util.Success(hub) =>
      display.io.desktop.open(window -> config).runAsync.onComplete {
        case util.Success(dp) =>
          val (scene, ui) = dp.unicast

          val device = Observable.interval(10.seconds)
            .map { x => (math.random() * 37).toInt }
            .debug("number")
            .map { s => (" " + s).takeRight(2) }
            .map(s => ByteVector(s.toCharArray.map(_.toByte)).bits)
            .debug("bits")

          hub.scan(Pl2303).foreach {
            case DeviceAttached(port) =>
              hub.open(port)
                .drive(Input.codec)
                .liftByOperator(debug[Input]("protocol"))
                .collect {
                  case Win(num) => Event.SpinCompleted(num)
                }.unicast._2.foreach(scene.onNext)
            case DeviceDetached(port) =>
              println(s"$port detached")
          }
          device.decode(Input.codec)
            .debug("protocol")
            .collect {
              case Win(num) => Event.SpinCompleted(num)
            }.foreach(scene.onNext)

          // latch gate is open when ui thread on complete.
          ui.doOnTerminate(_ => latch.countDown()).foreach(s => file.writeSerialized(s))
        case util.Failure(ex) =>
          ex.printStackTrace()
          latch.countDown()
      }
    case util.Failure(ex) =>
      ex.printStackTrace()
      latch.countDown()
  }
  latch.await()
}
