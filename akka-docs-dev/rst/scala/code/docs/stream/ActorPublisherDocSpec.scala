/**
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package docs.stream

import scala.annotation.tailrec
import akka.actor.Props
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.testkit.AkkaSpec

object ActorPublisherDocSpec {

  //#job-manager
  object JobManager {
    def props: Props = Props[JobManager]

    final case class Job(payload: String)
    case object JobAccepted
    case object JobDenied
  }

  class JobManager extends ActorPublisher[JobManager.Job] {
    import akka.stream.actor.ActorPublisherMessage._
    import JobManager._

    val MaxBufferSize = 100
    var buf = Vector.empty[Job]

    def receive = {
      case job: Job if buf.size == MaxBufferSize =>
        sender() ! JobDenied
      case job: Job =>
        sender() ! JobAccepted
        if (buf.isEmpty && totalDemand > 0)
          onNext(job)
        else {
          buf :+= job
          deliverBuf()
        }
      case Request(_) =>
        deliverBuf()
      case Cancel =>
        context.stop(self)
    }

    @tailrec final def deliverBuf(): Unit =
      if (totalDemand > 0) {
        if (totalDemand <= Int.MaxValue) {
          val (use, keep) = buf.splitAt(totalDemand.toInt)
          buf = keep
          use foreach onNext
        } else {
          val (use, keep) = buf.splitAt(Int.MaxValue)
          buf = keep
          use foreach onNext
          deliverBuf()
        }
      }
  }
  //#job-manager
}

class ActorPublisherDocSpec extends AkkaSpec {
  import ActorPublisherDocSpec._

  implicit val mat = FlowMaterializer()

  "illustrate usage of ActorPublisher" in {
    def println(s: String): Unit =
      testActor ! s

    //#actor-publisher-usage
    val jobManagerSource = Source[JobManager.Job](JobManager.props)
    val materializedMap = jobManagerSource
      .map(_.payload.toUpperCase)
      .map { elem => println(elem); elem }
      .to(Sink.ignore)
      .run()

    val ref = materializedMap.get(jobManagerSource)
    ref ! JobManager.Job("a")
    ref ! JobManager.Job("b")
    ref ! JobManager.Job("c")
    //#actor-publisher-usage

    expectMsg("A")
    expectMsg("B")
    expectMsg("C")
  }

}