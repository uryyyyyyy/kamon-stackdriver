package com.github.uryyyyyyy.kamon.stackdriver

import java.io.IOException

import akka.actor.Actor
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import akka.actor.ActorLogging
import kamon.metric.Entity
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.metric.MetricKey
import kamon.metric.instrument.Histogram
import kamon.metric.instrument.Counter
import java.lang.management.ManagementFactory

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.monitoring.v3.model._
import com.google.api.services.monitoring.v3.{Monitoring, MonitoringScopes}
import com.google.common.collect.{ImmutableMap, Lists}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util

/**
 * Sends metrics to Stackdriver through its client API.
 */
class StackdriverAPIMetricsSender extends Actor with ActorLogging {

  private val config = context.system.settings.config.getConfig("kamon.stackdriver")
  private val appName = config.getString("application-name")
  private val monitoringService = authenticate()

  override def receive = {
    case tick: TickMetricSnapshot =>
      send(tick)
  }

  private def createTimeSeries(metricType: String, metricKind: String, value: Double): TimeSeries = {
    val rfc3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    val now = ZonedDateTime.now(ZoneOffset.UTC)

    val metricLabel: util.Map[String, String] = ImmutableMap.of("environment", "STAGING")
    val resourceLabel: util.Map[String, String] = ImmutableMap.of("instance_id", "test-instance", "zone", "us-central1-f")


    val timeSeries: TimeSeries = new TimeSeries
    val metric: Metric = new Metric
    metric.setType(s"custom.googleapis.com/${metricType}")
    metric.setLabels(metricLabel)
    timeSeries.setMetric(metric)
    val monitoredResource: MonitoredResource = new MonitoredResource
    monitoredResource.setType("gce_instance")
    monitoredResource.setLabels(resourceLabel)
    timeSeries.setResource(monitoredResource)
    timeSeries.setMetricKind(metricKind)
    val point: Point = new Point
    val ti: TimeInterval = new TimeInterval
    val nowStr = rfc3339.format(now)
    ti.setStartTime(nowStr)
    ti.setEndTime(nowStr)
    point.setInterval(ti)
    point.setValue(new TypedValue().setDoubleValue(value))
    timeSeries.setPoints(Lists.newArrayList[Point](point))
    timeSeries
  }

  private def createRequest(timeSeriesList: Seq[TimeSeries]): CreateTimeSeriesRequest = {
    val timeSeriesRequest: CreateTimeSeriesRequest = new CreateTimeSeriesRequest
    timeSeriesRequest.setTimeSeries(Lists.newArrayList[TimeSeries](timeSeriesList :_*))
    timeSeriesRequest
  }

  def send(tick: TickMetricSnapshot): Unit = {
    val timeSeriesList: Seq[TimeSeries] = (for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } yield {
      val key = buildMetricName(groupIdentity, metricIdentity)

      def emit(keyPostfix: String, metricKind: String, value: Double): TimeSeries = {
        createTimeSeries(key + keyPostfix, metricKind, value)
      }

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          Seq(
            emit(".min", "GAUGE", hs.min),
            emit(".max", "GAUGE", hs.max),
            emit(".cnt", "GAUGE", hs.numberOfMeasurements),
            emit(".sum", "GAUGE", hs.sum),
            emit(".p95", "GAUGE", hs.percentile(0.95))
          )
        case cs: Counter.Snapshot ⇒
          if (cs.count > 0) Seq(emit("", "GAUGE", cs.count)) else Seq()
      }
    }).flatten.toSeq.slice(200, 250)

    val project = "adplan-reach-simulator-stg"
    val projectResource = "projects/" + project

    val timeSeriesRequest = createRequest(timeSeriesList)

    try {
      monitoringService.projects.timeSeries.create(projectResource, timeSeriesRequest).execute
    } catch {
      case e: IOException => log.error("stackdriver request failed, some metrics may have been dropped: {}", e.getMessage)
    }
  }

  def isSingleInstrumentEntity(entity: Entity): Boolean =
    SingleInstrumentEntityRecorder.AllCategories.contains(entity.category)

  def buildMetricName(entity: Entity, metricKey: MetricKey): String =
    if (isSingleInstrumentEntity(entity))
      s"$appName.${entity.category}.${entity.name}"
    else
      s"$appName.${entity.category}.${metricKey.name}"

  def systemHostName: String = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)

  private def authenticate() = {
    val credential = GoogleCredential.getApplicationDefault.createScoped(MonitoringScopes.all)
    val httpTransport = new NetHttpTransport
    val jsonFactory = new JacksonFactory
    new Monitoring.Builder(httpTransport, jsonFactory, credential).setApplicationName("Monitoring Sample").build
  }
}
