package io.waylay.influxdb.query

import java.time.Instant

import io.waylay.influxdb.InfluxDB.{Count, Distinct, Duration, Max}
import io.waylay.influxdb.query.InfluxQueryBuilder.Interval
import org.specs2.mutable.Specification

class InfluxQueryBuilderSpec extends Specification {

  "querying" should {


    "apply time start correctly" in {
      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "resource" -> "Living",
        "CO2",
        Interval.from(Instant.ofEpochMilli(0)))

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms""".stripMargin
    }

    "apply time interval correctly" in {
      val query = InfluxQueryBuilder.simple(
        Seq("value"),
        "resource" -> "Living",
        "CO2",
        Interval.fromUntil(Instant.ofEpochMilli(0), Instant.ofEpochMilli(1000)))

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT "value"
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms AND time < 1000ms""".stripMargin
    }

    "work grouped" in {
      val query = InfluxQueryBuilder.grouped(
        Count(Distinct("value")),
        "resource" -> "Living",
        "CO2",
        Duration.hours(1),
        Interval.relativeToNow(Duration.days(7)))

      //println(query.replace("\n", " "))

      query must be equalTo """SELECT COUNT(DISTINCT("value"))
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
        Interval.relativeTo(Duration.days(10), Instant.ofEpochMilli(0)))

      println(query.replace("\n", " "))

      query must be equalTo """SELECT MAX("value")
                              |FROM "CO2"
                              |WHERE "resource"='Living'
                              |AND time >= 0ms - 10d
                              |GROUP BY time(1h)""".stripMargin
    }
  }
}
