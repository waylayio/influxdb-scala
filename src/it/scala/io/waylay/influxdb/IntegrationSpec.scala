package io.waylay.influxdb

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.spotify.docker.client.DefaultDockerClient
import com.typesafe.config.ConfigFactory
import com.whisk.docker.testkit.{
  Container, ContainerCommandExecutor, ContainerSpec, DockerContainerManager, DockerReadyChecker, DockerTestTimeouts,
  SingleContainer
}
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._
import scala.util.Try

trait IntegrationSpec extends BeforeAfterAllStopOnError {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val DefaultInfluxDBAdminPort = 8083
  val DefaultInfluxDBPort      = 8086

  def influxdbContainer: Container

  def beforeAll(): Unit =
    startAllOrFail()

  def afterAll(): Unit =
    stopAllQuietly()

  lazy val containerManager = new DockerContainerManager(
    SingleContainer(influxdbContainer),
    // the manager takes care of cleanup of the client
    new ContainerCommandExecutor(DefaultDockerClient.fromEnv().build()),
    DockerTestTimeouts.Default,
    Implicits.global
  )

  val classloader: ClassLoader                 = getClass.getClassLoader
  implicit val actorSystem: ActorSystem        = ActorSystem("test", ConfigFactory.load(classloader), classloader)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  lazy val mappedInfluxPort: Int               = influxdbContainer.mappedPort(InfluxDB.DEFAULT_PORT)
  val host                                     = "localhost" // state.docker.host
  val wsClient: StandaloneAhcWSClient          = StandaloneAhcWSClient()
  val org: String                              = "myorg"
  val token: String                            = "mytoken"

  // Do we have a around available that makes this more robust?

  def startAllOrFail(): Unit = {
    containerManager.start()
    afterStart()
  }

  def stopAllQuietly(): Unit = {
    Try(beforeAll())
    wsClient.close()
    materializer.shutdown()
    actorSystem.terminate()
    try containerManager.stopRmAll()
    catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
  }
//
//  abstract override def run(testName: Option[String], args: Args): Status = {
//    containerManager.start()
//    afterStart()
//    try {
//      super.run(testName, args)
//    } finally {
//      try {
//        beforeStop()
//      } finally {
//        containerManager.stop()
//      }
//    }
//  }

  def afterStart(): Unit = {}

  def beforeStop(): Unit = {}
}

trait IntegrationSpecV1 extends IntegrationSpec {
  lazy val influxdbContainer: Container = ContainerSpec("influxdb:1.8.9-alpine")
    .withExposedPorts(DefaultInfluxDBPort, DefaultInfluxDBAdminPort)
    .withReadyChecker(
      DockerReadyChecker
        .HttpResponseCode(DefaultInfluxDBPort, "/ping", code = 204)
        .within(100.millis)
        .looped(20, 250.millis)
    )
    .toContainer

}

trait IntegrationSpecV2 extends IntegrationSpec {
  lazy val influxdbContainer: Container = ContainerSpec("influxdb:2.7-alpine")
    .withExposedPorts(DefaultInfluxDBPort, DefaultInfluxDBAdminPort)
    .withReadyChecker(
      DockerReadyChecker
        .HttpResponseCode(DefaultInfluxDBPort, "/ping", code = 204)
        .within(100.millis)
        .looped(40, 250.millis)
    )
    .withEnv(
      "DOCKER_INFLUXDB_INIT_MODE=setup",
      "DOCKER_INFLUXDB_INIT_USERNAME=admin",
      "DOCKER_INFLUXDB_INIT_PASSWORD=adminpasswordpasswordpassword",
      s"DOCKER_INFLUXDB_INIT_ORG=$org",
      "DOCKER_INFLUXDB_INIT_BUCKET=testbucket",
      s"DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=$token"
    )
    .toContainer

}
