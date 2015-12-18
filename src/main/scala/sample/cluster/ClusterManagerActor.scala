package sample.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import sample.cluster.ClusterManagerActor.{JoinCluster, UnreachableTimeout}

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
class ClusterManagerActor(seedNode: Address, unreachableTimeout: Long) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  private val unreachable: mutable.Map[Address, Cancellable] = mutable.Map()

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
    cluster.leave(cluster.selfAddress)
  }

  override def receive = LoggingReceive {
    case JoinCluster =>
      log.info("Trying to join the cluster ...")
      cluster.join(seedNode)

    case UnreachableTimeout(address) =>
      log.info(s"Unreachable timeout received: $address")
      cluster.state.members.find(_.address == address).foreach { m =>
        log.info(s"Removing node '$address' from cluster ...")
        cluster.down(address)
      }

    case MemberUp(member) =>
      log.info(s"Node is up: $member")
      joinTimer.cancel()
      cancelUnreachableTimer(member.address)

    case UnreachableMember(member) =>
      log.info(s"Node is unreachable: $member")
      val cancellable = context.system.scheduler.scheduleOnce(unreachableTimeout.seconds, self, UnreachableTimeout(member.address))
      unreachable += (member.address -> cancellable)

    case ReachableMember(member) =>
      log.info(s"Node is reachable again: $member")
      cancelUnreachableTimer(member.address)

    case MemberRemoved(member, prevStatus) => log.info(s"Node is removed: $member after $prevStatus")
    case ev: MemberEvent => log.info(s"[Event: $ev")
  }

  private def cancelUnreachableTimer(address: Address): Unit = {
    unreachable.remove(address).foreach(_.cancel())
  }
}

object ClusterManagerActor {
  case object JoinCluster
  case class UnreachableTimeout(address: Address)

  def props(seedNode: Address, unreachableTimeout: Long): Props = Props(classOf[ClusterManagerActor], seedNode, unreachableTimeout)
}
