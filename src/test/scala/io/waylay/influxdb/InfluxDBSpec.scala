package io.waylay.influxdb

import java.time.Instant

import akka.stream.Materializer
import com.whisk.docker.DockerKit
import io.waylay.influxdb.Influx.{IFloat, IPoint}
import io.waylay.influxdb.InfluxDB.Mean
import io.waylay.influxdb.query.InfluxQueryBuilder
import io.waylay.influxdb.query.InfluxQueryBuilder.Interval
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.specs2.mutable.{BeforeAfter, Specification}
import org.specs2.specification.core.Env
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration._

class InfluxDBSpec(env: Env) extends Specification{

  // TODO Reenable -> Waiting for https://github.com/whisklabs/docker-it-scala/issues/20
  skipAll

  implicit val ee = env.executionEnv

  "then influxdb client" should{

    "return a version on ping" in new MutableDockerTestKit with DockerInfluxDBService{
      withInfluxClient(this){ influxClient =>
        val version = Await.result(influxClient.ping, 5.seconds)
        println(version)
        version must not(beEmpty)
      }
    }

    "store and query data" in new MutableDockerTestKit with DockerInfluxDBService{
      withInfluxClient(this){ influxClient =>
        val points = Seq(
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(20.3)), Instant.now())
        )

        val query = InfluxQueryBuilder.simple(Seq("value"), "location" -> "room1", "temperature")

        Await.result(influxClient.storeAndMakeDbIfNeeded("dbname", points), 5.seconds)
        // TODO why do we need this eventually? Is influx storage async? And whys so slow?
        eventually(40, 250.millis){
          val data = Await.result(influxClient.query("dbname", query), 5.seconds)

          println(data)

          (data.error must beNone) and
            (data.results.get.head.series.get must have size 1) and
            (data.results.get.head.series.get.head.name must be equalTo "temperature")
        }
      }
    }

    "query aggregated data" in new MutableDockerTestKit with DockerInfluxDBService{
      skipped("see https://github.com/influxdb/influxdb/issues/5120")
      withInfluxClient(this){ influxClient =>
        val points = Seq(
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(20)), Instant.ofEpochSecond(0)),
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(22)), Instant.ofEpochSecond(60)),
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(24)), Instant.ofEpochSecond(120)),
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(26)), Instant.ofEpochSecond(180))
        )

        Await.result(influxClient.storeAndMakeDbIfNeeded("dbname", points), 5.seconds)

        val query = InfluxQueryBuilder.grouped(
          Mean("value"),
          "location" -> "room1",
          "temperature",
          InfluxDB.Duration.minutes(1),
          Interval.fromUntil(Instant.ofEpochSecond(0), Instant.ofEpochSecond(200))
        )

        println(query)

        val data = Await.result(influxClient.query("dbname", query), 5.seconds)
        data.error must beNone
        println(data.results)
        data.results.get.head.series.get must have size 4
        data.results.get.head.series.get.head.values.get.head must be equalTo Seq(Some(IFloat(21)), Some(IFloat(25)))
      }
    }

  }


  def withInfluxClient[T](service:DockerInfluxDBService)(block:InfluxDB => T) = {
    service.influxdbContainer.isReady() must beTrue.await
    val ports = Await.result(service.influxdbContainer.getPorts()(service.docker, service.dockerExecutionContext), 5.seconds)
    val mappedInfluxPort = ports.get(InfluxDB.DEFAULT_PORT).get

    val config = new DefaultAsyncHttpClientConfig.Builder().build()
    implicit val materializer: Materializer = ???
    val wsClient = new AhcWSClient(config)
    try {
      val influxClient = new InfluxDB(wsClient, service.docker.host, mappedInfluxPort)(service.dockerExecutionContext)
      block(influxClient)
    }finally {
      wsClient.close()
    }
  }
}

trait MutableDockerTestKit extends BeforeAfter with DockerKit {
  def before() = {
    println("!!! starting docker images(s): " + dockerContainers.map(_.image).mkString(", "))
    startAllOrFail()
  }

  def after() = {
    println("!!! stopping docker images(s): " + dockerContainers.map(_.image).mkString(", "))
    stopAllQuietly()
  }
}
