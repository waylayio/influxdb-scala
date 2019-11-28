package io.waylay.influxdb

import java.time.Instant

import io.waylay.influxdb.Influx.{IFloat, IPoint, IString}
import io.waylay.influxdb.InfluxDB.{GT, IFieldFilter, LT, Mean, OR}
import io.waylay.influxdb.query.InfluxQueryBuilder
import io.waylay.influxdb.query.InfluxQueryBuilder.Interval
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.duration._

class InfluxDBSpec(implicit ee: ExecutionEnv)
    extends Specification
    with IntegrationSpec {


  "then influxdb client" should {

    "return a version on ping" in {
      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)
      val version = Await.result(influxClient.ping, 5.seconds)
      version must be equalTo("1.3.9")
    }

    "store and query data" in {
      val points = Seq(
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(20.3)),
          Instant.now()
        ),
        // 2 values
        IPoint(
          "indoor",
          Seq("location" -> "room2", "building" -> "A"),
          Seq("temperature" -> IFloat(19.3), "humidity" -> IFloat(35.1)),
          Instant.now()
        )
      )

      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "location" -> "room1",
        "temperature"
      )


      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)
      Await.result(
        influxClient.storeAndMakeDbIfNeeded("dbname", points),
        5.seconds
      )

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
          Seq("value" -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)
      Await.result(
        influxClient.storeAndMakeDbIfNeeded("dbname", points),
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

      //println(query)

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)
      data.error must beNone
      //println(data.results)
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
          Seq("value" -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value" -> IFloat(20)),
          Instant.ofEpochSecond(30)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value" -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value" -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value" -> IFloat(23)),
          Instant.ofEpochSecond(140)
        ),
        IPoint(
          "temperature",
          Seq("location" -> "roomFilterAggregated"),
          Seq("value" -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)
      Await.result(
        influxClient.storeAndMakeDbIfNeeded("dbname", points),
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

      //println(query)

      val data = Await.result(influxClient.query("dbname", query), 5.seconds)
      data.error must beNone
      //println(data.results)
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
          Seq("value" -> IFloat(20)),
          Instant.ofEpochSecond(0)
        ),
        IPoint(
          "humidity",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(22)),
          Instant.ofEpochSecond(60)
        ),
        IPoint(
          "noise",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(24)),
          Instant.ofEpochSecond(120)
        ),
        IPoint(
          "co2",
          Seq("location" -> "room1"),
          Seq("value" -> IFloat(26)),
          Instant.ofEpochSecond(180)
        )
      )

      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)
      Await.result(
        influxClient.storeAndMakeDbIfNeeded("testdb2", points),
        5.seconds
      )

      val query = "show measurements"

      //println(query)

      val data = Await.result(influxClient.query("testdb2", query), 5.seconds)
      data.error must beNone
      //println(data.results)
      data.results.get.head.series.get must have size 1
      data.results.get.head.series.get.head.values.get must be equalTo Seq(
        Seq(Some(IString("co2"))),
        Seq(Some(IString("humidity"))),
        Seq(Some(IString("noise"))),
        Seq(Some(IString("temperature")))
      )

    }
  }
}
