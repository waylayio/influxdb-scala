package io.waylay.influxdb.query

import io.waylay.influxdb.Influx.{IFloat, IInteger, IString}
import io.waylay.influxdb.InfluxDB._
import io.waylay.influxdb.query.InfluxQueryBuilder.{Interval, Order}
import org.specs2.mutable.Specification

import java.time.Instant

class InfluxQueryBuilderSpec extends Specification {

  "querying" should {

    "apply time start correctly" in {
      val query = InfluxQueryBuilder.simpleMultipleMeasurements(
        Seq("value"),
        "resource" -> "Living",
        Seq("CO2"),
        Interval.from(Instant.ofEpochMilli(0))
      )

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms
                              |ORDER BY time ASC""".stripMargin
    }

    "apply time interval correctly" in {
      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "resource" -> "Living",
        "CO2",
        Interval.fromUntil(Instant.ofEpochMilli(0), Instant.ofEpochMilli(1000))
      )

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms AND time < 1000ms
                              |ORDER BY time ASC""".stripMargin
    }

    "remove empty lines in simple query" in {
      val query = InfluxQueryBuilder.simpleMultipleMeasurements(Seq("value"), "resource" -> "Living", Seq("CO2"))

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |ORDER BY time ASC""".stripMargin
    }

    "apply ordering correctly" in {
      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "resource" -> "Living",
        "CO2",
        Interval.from(Instant.ofEpochMilli(0)),
        order = Order.Descending
      )

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms
                              |ORDER BY time DESC""".stripMargin
    }

    "work grouped" in {
      val query = InfluxQueryBuilder.grouped(
        Count(Distinct("value")),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeToNow(Duration.days(7))
      )

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT COUNT(DISTINCT("value"))
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= now() - 7d
                              |GROUP BY time(1h)""".stripMargin
    }

    "remove empty lines in grouped query" in {
      val query = InfluxQueryBuilder.grouped(
        Count("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1)
      )

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT COUNT("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |GROUP BY time(1h)""".stripMargin
    }

    "work with multiple groups" in {
      val query = InfluxQueryBuilder.groupedMultiple(
        Seq(Count(Distinct("value")), Max("value")),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeToNow(Duration.days(7))
      )

      query must be equalTo """SELECT COUNT(DISTINCT("value")), MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= now() - 7d
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply the where time clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0))
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply a single filter clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)),
        Some(IFieldFilter("value", LTE, IFloat(130.98)))
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |AND ("value" <= 130.98)
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply a multiple and filter clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)),
        Some(AND(IFieldFilter("value", LTE, IFloat(130.98)), IFieldFilter("value", GTE, IFloat(98.78))))
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |AND (("value" <= 130.98) AND ("value" >= 98.78))
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply a multiple or filter clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)),
        Some(OR(IFieldFilter("value", LTE, IFloat(98.78)), IFieldFilter("value", GTE, IFloat(130.98))))
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |AND (("value" <= 98.78) OR ("value" >= 130.98))
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply a not filter clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)),
        Some(NOT(IFieldFilter("value", LTE, IFloat(98.78))))
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |AND (NOT ("value" <= 98.78))
                              |GROUP BY time(1h)""".stripMargin
    }

    "apply a complex filter clause correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Max("value"),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)),
        Some(
          OR(
            IFieldFilter("value", LTE, IFloat(98.78)),
            AND(IFieldFilter("value", GTE, IFloat(130.98)), NOT(IFieldFilter("value", EQ, IInteger(140)))),
            IFieldFilter("value", NE, IString("error"))
          )
        )
      )

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |AND (("value" <= 98.78) OR (("value" >= 130.98) AND (NOT ("value" = 140))) OR ("value" <> 'error'))
                              |GROUP BY time(1h)""".stripMargin
    }

    "group aggregating sum correctly" in {
      val query = InfluxQueryBuilder.grouped(
        Sum("value"),
        "resource" -> "person1",
        "steps",
        Duration.hours(1),
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0))
      )

      query must be equalTo """SELECT SUM("value")
                              |FROM "steps"
                              |WHERE "resource"='person1'
                              |AND time >= 0ms - 10d
                              |GROUP BY time(1h)""".stripMargin
    }

    "metricnames should return correct query" in {
      val query = InfluxQueryBuilder.metricNames("test", "resource" -> "person")
      query must be equalTo """SHOW TAG KEYS ON "test" WHERE resource = 'person'"""
    }

    "delete series process time ranges" in {
      val query = InfluxQueryBuilder.deleteSeries("resource" -> "test", None, None)
      query must be equalTo
      """DELETE
          |WHERE "resource"='test'""".stripMargin
      val query2 = InfluxQueryBuilder.deleteSeries("resource" -> "test", Some(Instant.ofEpochMilli(10)), None)
      query2 must be equalTo
      """DELETE
          |WHERE "resource"='test' AND time >= 10ms""".stripMargin

      val query3 = InfluxQueryBuilder.deleteSeries(
        "resource" -> "test",
        Some(Instant.ofEpochMilli(10)),
        Some(Instant.ofEpochMilli(20))
      )
      query3 must be equalTo
      """DELETE
          |WHERE "resource"='test' AND time >= 10ms AND time <= 20ms""".stripMargin

    }
  }
}
