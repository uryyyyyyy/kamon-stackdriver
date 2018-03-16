//package com.github.uryyyyyyy.kamon.stackdriver
//
//import akka.actor.ActorSystem
//import akka.testkit.TestKitBase
//import kamon.Kamon
//import org.scalatest.{Matchers, WordSpecLike}
//
//class SrackdriverMetricSenderSpec extends TestKitBase with Matchers with WordSpecLike {
//
//  "the DataDogMetricSender" should {
//    "send latency measurements" in {
//      println("TBD")
//    }
//  }
//  override implicit lazy val system: ActorSystem = {
//    Kamon.start()
//    ActorSystem()
//  }
//}