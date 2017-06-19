
package com.github.uryyyyyyy.kamon.stackdriver

import java.net.InetSocketAddress

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.util.ConfigTools.Syntax
import kamon.metric._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import kamon.util.NeedToScale

object Stackdriver extends ExtensionId[StackdriverExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Stackdriver
  override def createExtension(system: ExtendedActorSystem): StackdriverExtension = new StackdriverExtension(system)
}

class StackdriverExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit private val as = system
  val log = Logging(system, classOf[StackdriverExtension])
  log.info("Starting the Kamon(Stackdriver) extension")

  private val stackdriverConfig = system.settings.config.getConfig("kamon.stackdriver")

  val stackdriverHost = new InetSocketAddress(stackdriverConfig.getString("hostname"), stackdriverConfig.getInt("port"))
  private val flushInterval = stackdriverConfig.getFiniteDuration("flush-interval")
  private val tickInterval = Kamon.metrics.settings.tickInterval

  private val stackDriverMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  private val subscriptions = stackdriverConfig.getConfig("subscriptions")
  subscriptions.firstLevelKeys.foreach(subscriptionCategory =>
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, stackDriverMetricsListener, permanently = true)
    }
  )

  def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration): ActorRef = {
    assert(flushInterval >= tickInterval, "StackDriver flush-interval needs to be equal or greater to the tick-interval")

    val metricsSender = system.actorOf(Props[StackdriverAPIMetricsSender], "stackdriver-metrics-sender")

    val decoratedSender = stackdriverConfig match {
      case NeedToScale(scaleTimeTo, scaleMemoryTo) =>
        system.actorOf(MetricScaleDecorator.props(scaleTimeTo, scaleMemoryTo, metricsSender), "stackdriver-metric-scale-decorator")
      case _ ⇒ metricsSender
    }

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      decoratedSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, decoratedSender), "stackdriver-metrics-buffer")
    }
  }
}

