package integration


import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.DockerFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import io.waylay.influxdb.InfluxDB
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.ws.ahc.AhcWSClient
import resource._

import scala.concurrent.{Await, Future}


class VersionIntegrationSpec extends FlatSpec with Matchers with ScalaFutures with ManagedSupport
  with DockerInfluxDBService
  with DockerTestKit {

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())

  "Getting the influxDB version" should "return version" in {

    val resources = for{
      actorSystem <- managed(ActorSystem())
      materializer <- managed(ActorMaterializer()(actorSystem))
      wsClient <- managed(AhcWSClient()(materializer))
    }yield{
      wsClient
    }

    resources.acquireFor{ wsClient =>
      influxdbContainer.getPorts().map(_(DefaultInfluxDBPort)).flatMap { mappedInfluxPort: Int =>
        // TODO how to get the host
        val host = "localhost" //service.docker.host
        new InfluxDB(wsClient, host, mappedInfluxPort)(dockerExecutionContext).ping
      }.futureValue
    } shouldBe Right("1.0.1")
  }
}

trait ManagedSupport extends LowPriorityManagedSupport{

  import scala.concurrent.duration._
  import scala.language.reflectiveCalls

  type ReflectiveTerminatable = { def terminate(): Future[Terminated] }
  implicit def reflectiveTerminatableResource[A <: ReflectiveTerminatable]: Resource[A] = new Resource[A] {
    override def close(r : A) = Await.result(r.terminate(), 10.seconds)
    override def toString = "Resource[{ def terminate(): Future[Terminated] }]"
  }
}

trait LowPriorityManagedSupport{

  import scala.language.reflectiveCalls

  type ReflectiveShutdownable = { def shutdown(): Unit }
  implicit def reflectiveShutdownableResource[A <: ReflectiveShutdownable]: Resource[A] = new Resource[A] {
    override def close(r : A) = r.shutdown()
    override def toString = "Resource[{ def shutdown(): Unit }]"
  }

}