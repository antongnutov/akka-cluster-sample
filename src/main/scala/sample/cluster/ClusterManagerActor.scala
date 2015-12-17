package sample.cluster

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive

/**
  * @author Anton Gnutov
  */
class ClusterManagerActor extends Actor with ActorLogging {

  val cluster = Cluster(context.system)


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent], classOf[UnreachableMember])
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive = LoggingReceive {
    case MemberUp(member) => log.info(s"[ClusterManager] node is up: $member")
    case UnreachableMember(member) => log.info(s"[ClusterManager] node is unreachable: $member")
    case MemberRemoved(member, prevStatus) => log.info(s"[ClusterManager] node is removed: $member after $prevStatus")
    case ev: MemberEvent => log.info(s"[ClusterManager] event: $ev")
  }
}
