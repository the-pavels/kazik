package fr.simulator.scenario.setup

import cats.effect._
import cats.effect.unsafe.implicits.global
import munit.{FunSuite, Location}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

trait IOSpec extends FunSuite {
  override def munitTimeout: Duration = new FiniteDuration(30, TimeUnit.MINUTES)

  override def munitValueTransforms: List[ValueTransform] =
    super.munitValueTransforms :+ new ValueTransform("IO", {
      case ioa: IO[_] => ioa.unsafeToFuture()
    })
}
