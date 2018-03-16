package com.github.uryyyyyyy.kamon.stackdriver.example.collector

import java.time.Duration
import java.util.concurrent.{ScheduledFuture, TimeUnit}

import kamon.Kamon
import org.slf4j.LoggerFactory

object MyCollector {

  private val logger = LoggerFactory.getLogger("com.github.uryyyyyyy.kamon.simple.collector.MyCollector")

  private var scheduledCollection: ScheduledFuture[_] = null

  def startCollecting() = {
    val myMetrics = new MyMetrics()
    val updaterSchedule = new Runnable {
      override def run(): Unit = myMetrics.update()
    }
    scheduledCollection = Kamon.scheduler().scheduleAtFixedRate(
      updaterSchedule,
      Duration.ofSeconds(1).toMillis,
      Duration.ofSeconds(1).toMillis,
      TimeUnit.MILLISECONDS
    )
    logger.info("startCollecting done")
  }

  def stopCollecting():Boolean = {
    val b = scheduledCollection.cancel(false)
    scheduledCollection = null
    b
  }
}
