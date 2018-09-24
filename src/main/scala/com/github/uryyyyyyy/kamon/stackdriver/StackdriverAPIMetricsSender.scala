package com.github.uryyyyyyy.kamon.stackdriver

import java.time.{ZoneOffset, ZonedDateTime}
import java.util

import com.google.api.MetricDescriptor.MetricKind
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.{Metric, MonitoredResource}
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.monitoring.v3.{MetricServiceClient, MetricServiceSettings}
import com.google.common.collect.ImmutableMap
import com.google.monitoring.v3._
import com.google.protobuf.Timestamp
import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.{Kamon, MetricReporter, Tags}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * Sends metrics to Stackdriver through its client API.
  */
class StackdriverAPIMetricsSender extends MetricReporter {

  private val logger = LoggerFactory.getLogger(classOf[StackdriverAPIMetricsSender])
  private val config = Kamon.config().getConfig("kamon.stackdriver")
  private val projectID = config.getString("project-id")
  logger.info(s"project-id -> ${projectID}")
  private val monitoredResourceType = config.getString("monitored-resource-type")
  logger.info(s"monitored-resource-type -> ${monitoredResourceType}")

  private val resourceLabel: util.Map[String, String] = {
    val builder = ImmutableMap.builder[String, String]()
    val obj = config.getConfig("resource-label")
    config.getObject("resource-label").keySet().asScala.foreach(key => {
      builder.put(key, obj.getString(key))
    })
    builder.build()
  }
  logger.info(s"resource-label -> ${resourceLabel.asScala.map(v => s"(${v._1}, ${v._2})").mkString(",")}")

  private val metricServiceClient = authenticate()

  private def authenticate() = {
    val credentials = GoogleCredentials.getApplicationDefault
    val provider = FixedCredentialsProvider.create(credentials)
    val metricServiceSettings = MetricServiceSettings.newBuilder.setCredentialsProvider(provider).build
    MetricServiceClient.create(metricServiceSettings)
  }

  override def start(): Unit =
    logger.info("Started the Kamon Stackdriver reporter")

  override def stop(): Unit = {
    logger.info("Stopped the Kamon Stackdriver reporter")
    metricServiceClient.close()
  }

  //TODO: should change some config?
  override def reconfigure(config: Config): Unit = {}

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val nowTimestamp = Timestamp.newBuilder()
      .setSeconds(now.toEpochSecond)
      .setNanos(now.getNano)
      .build()

    val counterTimeSeries = snapshot.metrics.counters.map(metric => {
      //TODO should use unit?
      createTimeSeries(s"kamon.${metric.name}", MetricKind.GAUGE, metric.tags, metric.value, nowTimestamp)
    })
    val gaugeTimeSeries = snapshot.metrics.gauges.map(metric => {
      //TODO should use unit?
      createTimeSeries(s"kamon.${metric.name}", MetricKind.GAUGE, metric.tags, metric.value, nowTimestamp)
    })
    val histgramTimeSeries = snapshot.metrics.histograms.flatMap(metric => {
      //TODO should use unit?
      Seq(
        createTimeSeries(s"kamon.${metric.name}.min", MetricKind.GAUGE, metric.tags, metric.distribution.min, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.max", MetricKind.GAUGE, metric.tags, metric.distribution.max, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.cnt", MetricKind.GAUGE, metric.tags, metric.distribution.count, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.sum", MetricKind.GAUGE, metric.tags, metric.distribution.sum, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.p95", MetricKind.GAUGE, metric.tags, metric.distribution.percentile(0.95).value, nowTimestamp)
      )
    })
    val rangeSamplerTimeSeries = snapshot.metrics.rangeSamplers.flatMap(metric => {
      //TODO should use unit?
      Seq(
        createTimeSeries(s"kamon.${metric.name}.min", MetricKind.GAUGE, metric.tags, metric.distribution.min, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.max", MetricKind.GAUGE, metric.tags, metric.distribution.max, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.cnt", MetricKind.GAUGE, metric.tags, metric.distribution.count, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.sum", MetricKind.GAUGE, metric.tags, metric.distribution.sum, nowTimestamp),
        createTimeSeries(s"kamon.${metric.name}.p95", MetricKind.GAUGE, metric.tags, metric.distribution.percentile(0.95).value, nowTimestamp)
      )
    })

    // API cannot accept when timeSeries over 250
    val timeSeriesListList = (counterTimeSeries ++ gaugeTimeSeries ++ histgramTimeSeries ++ rangeSamplerTimeSeries).grouped(200)

    timeSeriesListList.foreach(_timeSeriesList => {
      val createTimeSeriesRequest = CreateTimeSeriesRequest.newBuilder()
        .addAllTimeSeries(_timeSeriesList.toList.asJava)
        .setName(s"projects/${projectID}")
        .build()
      try {
        metricServiceClient.createTimeSeries(createTimeSeriesRequest)
      } catch {
        case e: Exception => {
          logger.warn("stackdriver request failed, some metrics may have been dropped. {}", e.getMessage)
          _timeSeriesList.foreach(v => logger.warn(s"stackdriver fail: metricType: ${v.getMetric.getType}, label: ${v.getMetric.getLabelsMap}"))
        }
      }
    })
  }

  private def createTimeSeries(metricType: String, metricKind: MetricKind, tags: Tags, value: Double, now: Timestamp): TimeSeries = {

    val metric = Metric.newBuilder()
      .setType(s"custom.googleapis.com/${metricType}")
      .putAllLabels(tags.map{case (k, v) => (k.replace('.', '_'), v)}.asJava)
      .build()

    val monitoredResource = MonitoredResource.newBuilder()
      .setType(monitoredResourceType)
      .putAllLabels(resourceLabel)
      .build()

    val interval = TimeInterval.newBuilder()
      .setStartTime(now)
      .setEndTime(now)
      .build()

    val point = Point.newBuilder()
      .setInterval(interval)
      .setValue(TypedValue.newBuilder().setDoubleValue(value).build())
      .build()

    TimeSeries.newBuilder()
      .setMetric(metric)
      .setResource(monitoredResource)
      .setMetricKind(metricKind)
      .addPoints(point)
      .build()
  }
}