package com.github.uryyyyyyy.kamon.stackdriver

import java.lang.management.ManagementFactory
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util

import akka.actor.{Actor, ActorLogging}
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.monitoring.v3.model._
import com.google.api.services.monitoring.v3.{Monitoring, MonitoringScopes}
import com.google.common.collect.{ImmutableMap, Lists}
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{Counter, Histogram}
import kamon.metric.{Entity, MetricKey, SingleInstrumentEntityRecorder}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
 * Sends metrics to Stackdriver through its client API.
 */
class StackdriverAPIMetricsSender extends Actor with ActorLogging {

  private val logger = LoggerFactory.getLogger(classOf[StackdriverAPIMetricsSender])
  private val config = context.system.settings.config.getConfig("kamon.stackdriver")
  private val appName = config.getString("application-name")
  private val projectID = config.getString("project-id")
  logger.info(s"StackdriverAPIMetricsSender: project-id -> ${projectID}")
  private val projectResource = "projects/" + projectID
  private val monitoredResourceType = config.getString("monitored-resource-type")
  logger.info(s"StackdriverAPIMetricsSender: monitored-resource-type -> ${monitoredResourceType}")

  private val metricLabel: util.Map[String, String] = {
    val builder = ImmutableMap.builder[String, String]()
    val obj = config.getConfig("metric-label")
    config.getObject("metric-label").keySet().asScala.foreach(key => {
      builder.put(key, obj.getString(key))
    })
    builder.build()
  }
  logger.info(s"StackdriverAPIMetricsSender: metric-label -> ${metricLabel.asScala.map(v => s"(${v._1}, ${v._2})").mkString(",")}")

  private val resourceLabel: util.Map[String, String] = {
    val builder = ImmutableMap.builder[String, String]()
    val obj = config.getConfig("resource-label")
    config.getObject("resource-label").keySet().asScala.foreach(key => {
      builder.put(key, obj.getString(key))
    })
    builder.build()
  }
  logger.info(s"StackdriverAPIMetricsSender: resource-label -> ${resourceLabel.asScala.map(v => s"(${v._1}, ${v._2})").mkString(",")}")

  private val monitoringService = authenticate()

  override def receive = {
    case tick: TickMetricSnapshot =>
      send(tick)
  }

  private def createTimeSeries(metricType: String, metricKind: String, value: Double, nowStr: String): TimeSeries = {

    val timeSeries: TimeSeries = new TimeSeries
    val metric: Metric = new Metric
    metric.setType(s"custom.googleapis.com/${metricType}")
    metric.setLabels(metricLabel)
    timeSeries.setMetric(metric)
    val monitoredResource: MonitoredResource = new MonitoredResource
    monitoredResource.setType(monitoredResourceType)
    monitoredResource.setLabels(resourceLabel)
    timeSeries.setResource(monitoredResource)
    timeSeries.setMetricKind(metricKind)
    val point: Point = new Point
    val ti: TimeInterval = new TimeInterval
    ti.setStartTime(nowStr)
    ti.setEndTime(nowStr)
    point.setInterval(ti)
    point.setValue(new TypedValue().setDoubleValue(value))
    timeSeries.setPoints(Lists.newArrayList[Point](point))
    timeSeries
  }

  private def createRequest(timeSeriesList: Iterable[TimeSeries]): CreateTimeSeriesRequest = {
    val timeSeriesRequest: CreateTimeSeriesRequest = new CreateTimeSeriesRequest
    timeSeriesRequest.setTimeSeries(Lists.newArrayList[TimeSeries](timeSeriesList.toSeq :_*))
    timeSeriesRequest
  }

  def send(tick: TickMetricSnapshot): Unit = {
    val timeSeriesListList: Iterator[Iterable[TimeSeries]] = (for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } yield {
      val key = buildMetricName(groupIdentity, metricIdentity)
      val rfc3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
      val now = ZonedDateTime.now(ZoneOffset.UTC)
      val nowStr = rfc3339.format(now)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          Seq(
            createTimeSeries(key + ".min", "GAUGE", hs.min, nowStr),
            createTimeSeries(key + ".max", "GAUGE", hs.max, nowStr),
            createTimeSeries(key + ".cnt", "GAUGE", hs.numberOfMeasurements, nowStr),
            createTimeSeries(key + ".sum", "GAUGE", hs.sum, nowStr),
            createTimeSeries(key + ".p95", "GAUGE", hs.percentile(0.95), nowStr)
          )
        case cs: Counter.Snapshot ⇒
          if (cs.count > 0) Seq(createTimeSeries(key, "GAUGE", cs.count, nowStr)) else Seq()
      }
    }).flatten.grouped(100)

    try {
      timeSeriesListList.foreach(timeSeriesList => {
        val timeSeriesRequest = createRequest(timeSeriesList)
        monitoringService.projects.timeSeries.create(projectResource, timeSeriesRequest).execute
      })
    } catch {
      case e: Exception => logger.error("stackdriver request failed, some metrics may have been dropped: {}", e.getMessage)
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
