package aecor.core.aggregate

import java.time.Instant

import aecor.core.actor.{EventsourcedBehavior, EventsourcedState}
import aecor.util._
import akka.Done
import cats.data.Xor

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

case class CommandId(value: String) extends AnyVal

case class EventId(value: String) extends AnyVal

case class AggregateEvent[+Event](id: EventId, event: Event, timestamp: Instant)

object AggregateEvent {
  implicit def projection[A, ACommand[_], AState, AEvent](implicit A: AggregateBehavior.Aux[A, ACommand, AState, AEvent]): EventsourcedState[AState, AggregateEvent[AEvent]] =
    new EventsourcedState[AState, AggregateEvent[AEvent]] {

      override def applyEvent(a: AState, e: AggregateEvent[AEvent]): AState =
        A.applyEvent(a, e.event)

      override def init: AState = A.init
    }
}

trait AggregateBehavior[A] {
  type Command[_]
  type State
  type Event

  def handleCommand[Response](a: A)(state: State, command: Command[Response]): Future[(Response, Seq[Event])]

  def init: State

  def applyEvent(state: State, event: Event): State
}

object AggregateBehavior {
  type AggregateResponse[+Rejection] = Xor[Rejection, Done]

  type AggregateDecision[+Rejection, +Event] = (AggregateResponse[Rejection], Seq[Event])

  object syntax {
    def accept[R, E](events: E*): AggregateDecision[R, E] = Xor.right(Done) -> events.toVector

    def reject[R, E](rejection: R): AggregateDecision[R, E] = Xor.left(rejection) -> Vector.empty

    implicit class AnyOps[A](a: A) {
      def withEvents[E](events: E*): (A, Seq[E]) = (a, Seq(events: _*))
      def noEvents[E]: (A, Seq[E]) = (a, Seq.empty)
    }
  }

  type Aux[A, Command0[_], State0, Event0] = AggregateBehavior[A] {
    type Command[X] = Command0[X]
    type State = State0
    type Event = Event0
  }
}

case class AggregateEventsourcedBehavior[Aggregate](aggregate: Aggregate)

object AggregateEventsourcedBehavior {

  implicit def instance[A, ACommand[_], AState, AEvent]
  (implicit A: AggregateBehavior.Aux[A, ACommand, AState, AEvent], ec: ExecutionContext): EventsourcedBehavior.Aux[AggregateEventsourcedBehavior[A], ACommand, AState, AggregateEvent[AEvent]] =
    new EventsourcedBehavior[AggregateEventsourcedBehavior[A]] {
      override type Command[X] = ACommand[X]
      override type State = AState
      override type Event = AggregateEvent[AEvent]

      override def handleCommand[R](a: AggregateEventsourcedBehavior[A])(state: State, command: Command[R]): Future[(R, Seq[Event])] =
        A.handleCommand(a.aggregate)(state, command).map {
          case (x, events) => x -> events.map(event => AggregateEvent(generate[EventId], event, Instant.now()))
        }
    }

}
