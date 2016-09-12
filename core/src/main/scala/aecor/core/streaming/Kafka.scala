package aecor.core.streaming

import aecor.core.aggregate.AggregateEvent
import aecor.core.serialization.Encoder
import aecor.core.serialization.protobuf.EventEnvelope
import akka.NotUsed
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.google.protobuf.ByteString
import org.apache.kafka.clients.producer.ProducerRecord


object Kafka {
  def eventSink[A: Encoder](producerSettings: ProducerSettings[String, EventEnvelope], topic: String) =
    Flow[CommittableJournalEntry[AggregateEvent[A]]].map {
      case (offset, JournalEntry(persistenceId, sequenceNr, AggregateEvent(eventId, event, timestamp))) =>
        val payload = EventEnvelope(eventId.value, ByteString.copyFrom(Encoder[A].encode(event)), timestamp.toEpochMilli)
        val producerRecord = new ProducerRecord(topic, null, payload.timestamp, persistenceId, payload)
        ProducerMessage.Message(producerRecord, offset)
    }.to(Producer.commitableSink(producerSettings))


  def flow[A: Encoder, PassThrough](producerSettings: ProducerSettings[String, EventEnvelope], topic: String): Flow[(PassThrough, JournalEntry[AggregateEvent[A]]), PassThrough, NotUsed]=
    Flow[(PassThrough, JournalEntry[AggregateEvent[A]])].map {
      case (offset, JournalEntry(persistenceId, sequenceNr, AggregateEvent(eventId, event, timestamp))) =>
        val payload = EventEnvelope(eventId.value, ByteString.copyFrom(Encoder[A].encode(event)), timestamp.toEpochMilli)
        val producerRecord = new ProducerRecord(topic, null, payload.timestamp, persistenceId, payload)
        ProducerMessage.Message(producerRecord, offset)
    }.via(Producer.flow(producerSettings)).map(_.message.passThrough)
}
