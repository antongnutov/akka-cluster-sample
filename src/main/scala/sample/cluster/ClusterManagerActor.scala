package sample.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import sample.cluster.ClusterManagerActor.{JoinCluster, UnreachableTimeout}

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
class ClusterManagerActor(seedNode: Address, unreachableTimeout: Long) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  import context.dispatcher
  val joinTimer = context.system.scheduler.schedule(1.second, 5.seconds, self, JoinCluster)

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent], classOf[ReachabilityEvent])
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    joinTimer.cancel()
    cluster.unsubscribe(self)
    cluster.down(cluster.selfAddress)
  }

  override def receive = LoggingReceive {
    case JoinCluster =>
      log.debug("Trying to join the cluster ...")
      cluster.join(seedNode)

    case MemberUp(member) =>
      log.debug(s"Node is up: $member")
      if (member.address == cluster.selfAddress) {
        log.info("Joined the cluster")
        joinTimer.cancel()
        context.become(active(Map()))
      }
  }

  def active(unreachable: Map[Address, Cancellable]): Receive = LoggingReceive {
    case UnreachableTimeout(address) =>
      log.debug(s"Unreachable timeout received: $address")
      cluster.state.members.find(_.address == address).foreach { m =>

        if (address == seedNode) {
          log.info(s"seedNode is unreachable: restarting ...")
          context.stop(self)
        } else {
          log.info(s"Removing node [$address] from cluster ...")
          cluster.down(address)
        }
      }

    case UnreachableMember(member) =>
      log.debug(s"Node is unreachable: $member")
      val cancellable =
        context.system.scheduler.scheduleOnce(unreachableTimeout.seconds, self, UnreachableTimeout(member.address))
      context.become(active(unreachable + (member.address -> cancellable)))

    case ReachableMember(member) =>
      log.debug(s"Node is reachable again: $member")
      unreachable.get(member.address).foreach(_.cancel())
      context.become(active(unreachable - member.address))

    case MemberUp(member) => log.debug(s"Node is up: $member")
    case MemberRemoved(member, prevStatus) => log.debug(s"Node is removed: $member after $prevStatus")
    case ev: MemberEvent => log.debug(s"[Event: $ev")
  }
}

object ClusterManagerActor {
  case object JoinCluster
  case class UnreachableTimeout(address: Address)

  def props(seedNode: Address, unreachableTimeout: Long): Props = Props(classOf[ClusterManagerActor], seedNode, unreachableTimeout)
}
