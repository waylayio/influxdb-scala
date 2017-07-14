package integration


import akka.actor.{ActorSystem, Cancellable}
import akka.stream._
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.DockerFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import io.waylay.influxdb.{Influx, InfluxDB}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}


class VersionIntegrationSpec extends FlatSpec with Matchers with ScalaFutures
  with DockerInfluxDBService
  with DockerTestKit {

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())

  "Getting the influxDB version" should "return version" in {
    withInfluxClient(this) { influxClient =>
      influxClient.ping.futureValue shouldBe "1.1.1"
    }
  }


  def withInfluxClient[T](service:DockerInfluxDBService)(block:InfluxDB => T) = {
    implicit val materializer = NoMaterializer
    val state = service.getContainerState(service.influxdbContainer)
    state.isReady().futureValue shouldBe true
    val ports = Await.result(state.getPorts()(service.dockerExecutor, service.dockerExecutionContext), 5.seconds)
    val mappedInfluxPort = ports(InfluxDB.DEFAULT_PORT)
    val host = "localhost" //state.docker.host
    val wsClient = AhcWSClient()
    try {
      val influxClient = new InfluxDB(wsClient, host, mappedInfluxPort)(service.dockerExecutionContext)
      block(influxClient)
    }finally {
      wsClient.close()
    }
  }

  // copied from akka
  private[integration] object NoMaterializer extends Materializer {
    override def withNamePrefix(name: String): Materializer =
      throw new UnsupportedOperationException("NoMaterializer cannot be named")
    override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat =
      throw new UnsupportedOperationException("NoMaterializer cannot materialize")
    override def materialize[Mat](runnable: Graph[ClosedShape, Mat], initialAttributes: Attributes): Mat =
      throw new UnsupportedOperationException("NoMaterializer cannot materialize")

    override def executionContext: ExecutionContextExecutor =
      throw new UnsupportedOperationException("NoMaterializer does not provide an ExecutionContext")

    def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable =
      throw new UnsupportedOperationException("NoMaterializer cannot schedule a single event")

    def schedulePeriodically(initialDelay: FiniteDuration, interval: FiniteDuration, task: Runnable): Cancellable =
      throw new UnsupportedOperationException("NoMaterializer cannot schedule a repeated event")
  }
}
