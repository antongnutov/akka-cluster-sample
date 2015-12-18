package sample.cluster

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, AddressFromURIString}
import com.typesafe.config.ConfigFactory

/**
 * @author Anton Gnutov
 */
object Main extends App {
  val system = ActorSystem("sample")
  val config = ConfigFactory.load()

  val seedNode = AddressFromURIString(config.getString("sample.seed-node"))
  val unreachableTimeout = config.getDuration("sample.unreachable.timeout", TimeUnit.SECONDS)

  system.actorOf(ClusterManagerActor.props(seedNode, unreachableTimeout), "ClusterManager")

  sys.addShutdownHook {
    system.terminate()
  }
}