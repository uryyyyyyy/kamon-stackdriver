package com.github.uryyyyyyy.kamon.stackdriver.example.collector

import kamon.Kamon
import org.slf4j.LoggerFactory

class MyMetrics {

  private val logger = LoggerFactory.getLogger(classOf[MyMetrics])

  val hist1    = Kamon.histogram("my-reporter.my-metrics.hist1")
  val counter1    = Kamon.counter("my-reporter.my-metrics.counter1")
  val sampler1    = Kamon.rangeSampler("my-reporter.my-metrics.sampler1")

  def update() = {
    logger.info("MyMetrics update")

    hist1.record(10)
    counter1.increment(1)
    counter1.increment(1)
    sampler1.increment(2)
  }
}
