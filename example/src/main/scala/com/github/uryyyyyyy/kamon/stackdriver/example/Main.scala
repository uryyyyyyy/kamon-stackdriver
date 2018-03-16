package com.github.uryyyyyyy.kamon.stackdriver.example

import com.github.uryyyyyyy.kamon.stackdriver.example.collector.MyCollector
import kamon.Kamon
import kamon.system.SystemMetrics
import org.slf4j.LoggerFactory

object Main {

  private val logger = LoggerFactory.getLogger(classOf[Main])

  def main(args: Array[String]): Unit = {
    Kamon.loadReportersFromConfig()
    SystemMetrics.startCollecting()
    MyCollector.startCollecting()
    logger.info("start")
    Thread.sleep(1000000)
    SystemMetrics.stopCollecting()
    MyCollector.stopCollecting()
    Kamon.stopAllReporters()
    Kamon.scheduler().shutdown()
    logger.info("finish")
    System.exit(0) // force shutdown
  }

}

class Main