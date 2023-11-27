package io.waylay.influxdb

import io.waylay.influxdb.Influx._
import io.waylay.influxdb.InfluxDB._
import io.waylay.influxdb.query.InfluxQueryBuilder
import io.waylay.influxdb.query.InfluxQueryBuilder.Interval
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.{JsDefined, JsString}

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration._

class InfluxDB2Spec(implicit ee: ExecutionEnv) extends Specification with IntegrationSpecV2 {
  sequential

  "then influxdb2 client" should {

    "create bucket with retention" in {
      val bucket = "testdb2.io"
      val influxClient =
        new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "4w")
      Await.result(influxClient.createBucket(bucket), 5.seconds)
      Await.result(influxClient.getRetention(bucket), 5.seconds) must be equalTo InfluxDB
        .parseDurationLiteral("4w")
        .toMillis / 1000

    }

    "return a version on ping" in {
      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort)
      val version      = Await.result(influxClient.ping, 15.seconds)
      version must be equalTo "v2.7.4"
    }

    "return ready" in {
      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort)
      val stats        = Await.result(influxClient.ready, 15.seconds)
      stats \ "status" must be equalTo JsDefined(JsString("ready"))
    }

    "store and query data" in {
      val points = Seq(
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(20.3)),
          Instant.now()
        ),
        // 2 values
        IPoint(
          "indoor",
          Seq("location"    -> "room2", "building"      -> "A"),
          Seq("temperature" -> IFloat(19.3), "humidity" -> IFloat(35.1)),
          Instant.now()
        )
      )

      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "location" -> "room1",
        "temperature"
      )

      val influxClient =
        new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "INF")
      val storeResult = Await.result(
        influxClient.storeAndMakeBucketIfNeeded("dbname", points),
        5.seconds
      )
      println(storeResult)
      storeResult must be equalTo ()

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)

      (data.error must beNone) and
      (data.results.get.head.series.get must have size 1) and
      (data.results.get.head.series.get.head.name must be equalTo "temperature")
    }

    "query aggregated data" in {

      val points = Seq(
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "INF")
      Await.result(
        influxClient.storeAndMakeBucketIfNeeded("dbname", points),
        5.seconds
      )

      val query = InfluxQueryBuilder.grouped(
        Mean("value"),
        "location" -> "room1",
        "temperature",
        InfluxDB.Duration.minutes(2),
        Interval
          .fromUntil(Instant.ofEpochSecond(0), Instant.ofEpochSecond(200))
      )

      // println(query)

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)
      data.error must beNone
      // println(data.results)
      data.results.get.head.series.get must have size 1
      data.results.get.head.series.get.head.values.get must be equalTo Seq(
        Seq(Some(IString("1970-01-01T00:00:00Z")), Some(IFloat(21.0))),
        Seq(Some(IString("1970-01-01T00:02:00Z")), Some(IFloat(25.0)))
      )
    }
    "query aggregated data with filter" in {

      val points = Seq(
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(20)),
          Instant.ofEpochSecond(30)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(23)),
          Instant.ofEpochSecond(140)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value"    -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "INF")
      Await.result(
        influxClient.storeAndMakeBucketIfNeeded("dbname", points),
        5.seconds
      )

      val query = InfluxQueryBuilder.grouped(
        Mean("value"),
        "location" -> "roomFilterAggregated",
        "temperature",
        InfluxDB.Duration.minutes(2),
        Interval
          .fromUntil(Instant.ofEpochSecond(0), Instant.ofEpochSecond(200)),
        Some(OR(IFieldFilter("value", LT, IFloat(22)), IFieldFilter("value", GT, IFloat(23))))
      )

      // println(query)

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)
      data.error must beNone
      // println(data.results)
      data.results.get.head.series.get must have size 1
      data.results.get.head.series.get.head.values.get must be equalTo Seq(
        Seq(Some(IString("1970-01-01T00:00:00Z")), Some(IFloat(20.0))),
        Seq(Some(IString("1970-01-01T00:02:00Z")), Some(IFloat(25.0)))
      )
    }

    "query measurements" in {
      val points = Seq(
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "humidity",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "noise",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "co2",
          Seq("location" -> "room1"),
          Seq("value"    -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "INF")
      Await.result(
        influxClient.storeAndMakeBucketIfNeeded("testdb2", points),
        5.seconds
      )

      val query = "show measurements"

      // println(query)

      val data = Await.result(influxClient.query("testdb2", query), 5.seconds)
      data.error must beNone
      // println(data.results)
      data.results.get.head.series.get must have size 1
      data.results.get.head.series.get.head.values.get must be equalTo Seq(
        Seq(Some(IString("co2"))),
        Seq(Some(IString("humidity"))),
        Seq(Some(IString("noise"))),
        Seq(Some(IString("temperature")))
      )

    }

    "query aggregated data for string values returns result with error" in {
      val stringmeasurement = "stringmeasurement"

      val points = Seq(
        IPoint(
          stringmeasurement,
          Seq("location" -> "room2"),
          Seq("value"    -> IString("hello")),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          stringmeasurement,
          Seq("location" -> "room2"),
          Seq("value"    -> IString("hello2")),
          Instant.ofEpochSecond(60)
        )
      )

      val influxClient = new InfluxDB2(wsClient, host, org, token, mappedInfluxPort, defaultRetention = "INF")
      Await.result(
        influxClient.storeAndMakeBucketIfNeeded("dbname", points),
        5.seconds
      )

      val query = InfluxQueryBuilder.grouped(
        Mean("value"),
        "location" -> "room2",
        stringmeasurement,
        InfluxDB.Duration.minutes(2),
        Interval
          .fromUntil(Instant.ofEpochSecond(0), Instant.ofEpochSecond(200))
      )

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)
      data.error must beNone
      data.results.get.head mustEqual Result(
        None,
        Some("unsupported mean iterator type: *query.stringInterruptIterator")
      )
    }
  }
}
