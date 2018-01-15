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
  implicit val usbConfig: UsbConfig = UsbConfig(uart = Uart(stopBits = 1) )

  //main thread will wait until gate is open - gate is open when ui thread on complete.
  val latch = new CountDownLatch(1)

  //Read application.conf
  val conf = ConfigFactory.load
  val databaseFile = File(conf.getString("persistence.file"))
  val config = loadConfigOrThrow[WindowConfig]("window")

  //Load seed based on databaseFile
  val seed: Running = if (databaseFile.exists) {
    databaseFile.readDeserialized[Running]
  } else {
    databaseFile.createIfNotExists(asDirectory = false, createParents = true)
    databaseFile.writeSerialized(Running("EU01", Seq.empty[String], 100, 1000, 100000))
    databaseFile.readDeserialized[Running]
  }

  //Select the window based on app theme
  val window = conf.getString("app.theme") match {
    case "red-black" => BillboardSceneRB(seed)
    case _ => BillboardScene(seed)
  }

  device.io.usb.Hub() match {
    case util.Success(hub) =>
      display.io.desktop.open(window -> config).runAsync.onComplete {
        case util.Success(dp) =>
          val (scene, ui) = dp.unicast

          val device = Observable.interval(10.seconds)
            .map { _ => (math.random() * 37).toInt }
            .debug("number")
            .map { s => (" " + s).takeRight(2) }
            .map {s => s + "\n\r"}
            .map(s => ByteVector(s.toCharArray.map(_.toByte)).bits)
            .debug("bits")

          hub.scan(Pl2303).foreach {
            case DeviceAttached(port) =>
              println(s"$port attached")
              hub.open(port)
                .drive(Input.codec)
                .liftByOperator(debug[Input]("protocol"))
                .collect {
                  case Win(num) => Event.SpinCompleted(num)
//                  case Status(BallDetected,x,num,y,z,a) => Event.SpinCompleted(num)
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
          ui.doOnTerminate(_ => latch.countDown()).foreach(s => databaseFile.writeSerialized(s))

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
