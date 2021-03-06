package cromwell.engine.workflow.lifecycle.execution.callcaching

import akka.actor.{ActorLogging, ActorRef, Props}
import cats.data.NonEmptyList
import cromwell.backend.BackendJobDescriptorKey
import cromwell.core.Dispatcher.EngineDispatcher
import cromwell.core.{LoadConfig, WorkflowId}
import cromwell.core.actor.BatchActor.CommandAndReplyTo
import cromwell.core.callcaching.HashResult
import cromwell.core.instrumentation.InstrumentationPrefixes
import cromwell.engine.workflow.lifecycle.execution.callcaching.CallCacheReadActor._
import cromwell.services.EnhancedThrottlerActor

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Queues up work sent to it because its receive is non-blocking.
  *
  * Would be nice if instead there was a pull- rather than push-based mailbox but I can't find one...
  */
class CallCacheReadActor(cache: CallCache,
                         override val serviceRegistryActor: ActorRef,
                         override val threshold: Int)
  extends EnhancedThrottlerActor[CommandAndReplyTo[CallCacheReadActorRequest]]
    with ActorLogging {
  override def routed = true
  override def processHead(request: CommandAndReplyTo[CallCacheReadActorRequest]): Future[Int] = instrumentedProcess {
    val response = request.command match {
      case HasMatchingInitialHashLookup(initialHash) =>
        cache.hasBaseAggregatedHashMatch(initialHash) map {
          case true => HasMatchingEntries
          case false => NoMatchingEntries
        }
      case HasMatchingInputFilesHashLookup(fileHashes) =>
        cache.hasKeyValuePairHashMatch(fileHashes) map {
          case true => HasMatchingEntries
          case false => NoMatchingEntries
        }
      case CacheLookupRequest(aggregatedCallHashes, cacheHitNumber) =>
        cache.callCachingHitForAggregatedHashes(aggregatedCallHashes, cacheHitNumber) map {
          case Some(nextHit) => CacheLookupNextHit(nextHit)
          case None => CacheLookupNoHit
        }
      case call @ CallCacheEntryForCall(workflowId, jobKey) =>
        import cromwell.core.ExecutionIndex._
        cache.cacheEntryExistsForCall(workflowId.toString, jobKey.call.fullyQualifiedName, jobKey.index.fromIndex) map {
          case true => HasCallCacheEntry(call)
          case false => NoCallCacheEntry(call)
        }
    }

    response onComplete {
      case Success(success) => request.replyTo ! success
      case Failure(f) => request.replyTo ! CacheResultLookupFailure(f)
    }

    response.map(_ => 1)
  }

  // EnhancedBatchActor overrides
  override def receive: Receive = enhancedReceive.orElse(super.receive)
  override protected def instrumentationPath = NonEmptyList.of("callcaching", "read")
  override protected def instrumentationPrefix = InstrumentationPrefixes.JobPrefix
  override def commandToData(snd: ActorRef) = {
    case request: CallCacheReadActorRequest => CommandAndReplyTo(request, snd)
  }
}

object CallCacheReadActor {
  def props(callCache: CallCache, serviceRegistryActor: ActorRef): Props = {
    Props(new CallCacheReadActor(callCache, serviceRegistryActor, LoadConfig.CallCacheReadThreshold)).withDispatcher(EngineDispatcher)
  }

  private[CallCacheReadActor] case class RequestTuple(requester: ActorRef, request: CallCacheReadActorRequest)

  object AggregatedCallHashes {
    def apply(baseAggregatedHash: String, inputFilesAggregatedHash: String) = {
      new AggregatedCallHashes(baseAggregatedHash, Option(inputFilesAggregatedHash))
    }
  }
  case class AggregatedCallHashes(baseAggregatedHash: String, inputFilesAggregatedHash: Option[String])

  sealed trait CallCacheReadActorRequest
  final case class CacheLookupRequest(aggregatedCallHashes: AggregatedCallHashes, cacheHitNumber: Int) extends CallCacheReadActorRequest
  final case class HasMatchingInitialHashLookup(aggregatedTaskHash: String) extends CallCacheReadActorRequest
  final case class HasMatchingInputFilesHashLookup(fileHashes: NonEmptyList[HashResult]) extends CallCacheReadActorRequest
  final case class CallCacheEntryForCall(workflowId: WorkflowId, jobKey: BackendJobDescriptorKey) extends CallCacheReadActorRequest

  sealed trait CallCacheReadActorResponse
  // Responses on whether or not there is at least one matching entry (can for initial matches of file matches)
  case object HasMatchingEntries extends CallCacheReadActorResponse
  case object NoMatchingEntries extends CallCacheReadActorResponse

  // Responses when asking for the next cache hit
  final case class CacheLookupNextHit(hit: CallCachingEntryId) extends CallCacheReadActorResponse
  case object CacheLookupNoHit extends CallCacheReadActorResponse

  final case class HasCallCacheEntry(call: CallCacheEntryForCall) extends CallCacheReadActorResponse
  final case class NoCallCacheEntry(call: CallCacheEntryForCall) extends CallCacheReadActorResponse

  // Failure Response
  case class CacheResultLookupFailure(reason: Throwable) extends CallCacheReadActorResponse
}
