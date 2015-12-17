package sample.cluster

import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * @author Anton Gnutov
 */
object Main extends App {
  val system = ActorSystem("system")

  system.actorOf(Props[ClusterManagerActor], "ClusterManager")

  sys.addShutdownHook {
    system.terminate()
  }
}