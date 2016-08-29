package io.waylay.influxdb

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.DockerFactory
import com.whisk.docker.specs2.DockerTestKit
import integration.DockerInfluxDBService
import io.waylay.influxdb.Influx.{IFloat, IPoint, IString}
import io.waylay.influxdb.InfluxDB.Mean
import io.waylay.influxdb.query.InfluxQueryBuilder
import io.waylay.influxdb.query.InfluxQueryBuilder.Interval
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.specs2.mutable.Specification
import org.specs2.specification.core.Env
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration._

class InfluxDBSpec(environment: Env) extends Specification with DockerInfluxDBService with DockerTestKit {


  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())

  implicit val ee = environment.executionEnv

  "then influxdb client" should{

    "return a version on ping" in {
      withInfluxClient(this){ influxClient =>
        val version = Await.result(influxClient.ping, 5.seconds)
        println(version)
        version must not(beEmpty)
      }
    }

    "store and query data" in {
      withInfluxClient(this){ influxClient =>
        val points = Seq(
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(20.3)), Instant.now())
        )

        val query = InfluxQueryBuilder.simple(Seq("value"), "location" -> "room1", "temperature")

        Await.result(influxClient.storeAndMakeDbIfNeeded("dbname", points), 5.seconds)
        // TODO why do we need this eventually? Is influx storage async? And whys so slow?
        eventually(40, 250.millis){
          val data = Await.result(influxClient.query("dbname", query), 5.seconds)

          //println(data)

          (data.error must beNone) and
            (data.results.get.head.series.get must have size 1) and
            (data.results.get.head.series.get.head.name must be equalTo "temperature")
        }
      }
    }

    "query aggregated data" in {
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
          InfluxDB.Duration.minutes(2),
          Interval.fromUntil(Instant.ofEpochSecond(0), Instant.ofEpochSecond(200))
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
    }

    "query measurements" in {
      withInfluxClient(this){ influxClient =>
        val points = Seq(
          IPoint("temperature", Seq("location" -> "room1"), Seq("value" -> IFloat(20)), Instant.ofEpochSecond(0)),
          IPoint("humidity", Seq("location" -> "room1"), Seq("value" -> IFloat(22)), Instant.ofEpochSecond(60)),
          IPoint("noise", Seq("location" -> "room1"), Seq("value" -> IFloat(24)), Instant.ofEpochSecond(120)),
          IPoint("co2", Seq("location" -> "room1"), Seq("value" -> IFloat(26)), Instant.ofEpochSecond(180))
        )

        Await.result(influxClient.storeAndMakeDbIfNeeded("dbname", points), 5.seconds)

        val query = "show measurements"

        //println(query)

        val data = Await.result(influxClient.query("dbname", query), 5.seconds)
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


  def withInfluxClient[T](service:DockerInfluxDBService)(block:InfluxDB => T) = {
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val state = service.getContainerState(service.influxdbContainer)
    state.isReady() must beTrue.await
    val ports = Await.result(state.getPorts()(service.dockerExecutor, service.dockerExecutionContext), 5.seconds)
    val mappedInfluxPort = ports(InfluxDB.DEFAULT_PORT)
    val host = "localhost" //state.docker.host
    val config = new DefaultAsyncHttpClientConfig.Builder().build()
    val wsClient = new AhcWSClient(config)
    try {
      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)(service.dockerExecutionContext)
      block(influxClient)
    }finally {
      wsClient.close()
      materializer.shutdown()
      Await.result(actorSystem.terminate(), 10.seconds)
    }
  }
}
