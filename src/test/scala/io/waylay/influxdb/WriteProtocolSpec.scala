package io.waylay.influxdb

import java.time.Instant
import java.util.concurrent.TimeUnit

import io.waylay.influxdb.Influx._
import org.specs2.mutable.Specification

class WriteProtocolSpec extends Specification {

  "The write protocol" should {

    "write with second precision" in {
      val dataLines = WriteProtocol.write(
        TimeUnit.SECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IInteger(21)), Instant.ofEpochSecond(12))
      )

      dataLines must be equalTo """temp,resource=test value=21i 12"""
    }

    "write with millisecond precision" in {
      val dataLines = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IInteger(21)), Instant.ofEpochSecond(1))
      )

      dataLines must be equalTo """temp,resource=test value=21i 1000"""
    }

    "write with nanosecond precision" in {
      val dataLines = WriteProtocol.write(
        TimeUnit.NANOSECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IInteger(21)), Instant.ofEpochMilli(5).plusNanos(123))
      )

      dataLines must be equalTo """temp,resource=test value=21i 5000123"""
    }

    "write double points with at least single decimal" in {
      val myValue = 21d
      //println(myValue.toString) // outputs scientific 2.1E-7

      val dataLines = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IFloat(myValue)), Instant.ofEpochSecond(1))
      )

      dataLines must be equalTo """temp,resource=test value=21.0 1000"""
    }

    "write double points without scientific if not needed" in {
      val myValue = 0.21d
      //println(myValue.toString) // outputs scientific 2.1E-7

      val dataLines = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IFloat(myValue)), Instant.ofEpochSecond(1))
      )

      dataLines must be equalTo """temp,resource=test value=0.21 1000"""
    }

    "write double points with all digits" in {
      val myValue = 0.21212121d
      //println(myValue.toString) // outputs scientific 2.1E-7

      val dataLines = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IFloat(myValue)), Instant.ofEpochSecond(1))
      )

      dataLines must be equalTo """temp,resource=test value=0.21212121 1000"""
    }

    "write double points with scientific notation where needed" in {
      val myValue = 0.00000021d
      //println(myValue.toString) // outputs scientific 2.1E-7

      val dataLines = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("resource" -> "test"), Seq("value" -> IFloat(myValue)), Instant.ofEpochSecond(1))
      )

      dataLines must be equalTo """temp,resource=test value=2.1e-7 1000"""
    }

    "handle tags with spaces correctly" in {
      val dataLine = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("my tag" -> "test"), Seq("value" -> IString("foo bar")), Instant.ofEpochSecond(1))
      )

      dataLine must be equalTo """temp,my\ tag=test value="foo bar" 1000"""
    }

    "handle multiline correctly" in {
      val dataLine = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint("temp", Seq("tag" -> "test"), Seq("value" -> IString("low")), Instant.ofEpochSecond(1)),
        IPoint("temp", Seq("tag" -> "test"), Seq("value" -> IString("high")), Instant.ofEpochSecond(2))
      )

      dataLine must be equalTo
      """temp,tag=test value="low" 1000
          |temp,tag=test value="high" 2000""".stripMargin
    }

    "handle multiple fields" in {
      val myValue = 21d

      val dataLine = WriteProtocol.write(
        TimeUnit.MILLISECONDS,
        IPoint(
          "temp",
          Seq("resource" -> "test"),
          Seq("value1"   -> IString("foo bar"), "value2" -> IInteger(21), "value3" -> IFloat(myValue)),
          Instant.ofEpochSecond(1)
        )
      )

      dataLine must be equalTo """temp,resource=test value1="foo bar",value2=21i,value3=21.0 1000"""
    }

  }
}
