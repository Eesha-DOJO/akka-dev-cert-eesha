package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@ComponentId("booking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

    private final ComponentClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SlotToParticipantConsumer(ComponentClient client) {
        this.client = client;
    }

    public Effect onEvent(BookingEvent event) {
        var participantSlot = participantSlotId(event);
    //When an event arrives in onEvent(), it transforms that event into a corresponding command:
        //ParticipantMarkedAvailable event → MarkAvailable command
        return switch (event) {
            case BookingEvent.ParticipantMarkedAvailable evt -> effects().asyncDone(
                            client.forEventSourcedEntity(participantSlot)
                                    .method(ParticipantSlotEntity::markAvailable)
                                    .invokeAsync(new ParticipantSlotEntity.Commands.MarkAvailable(
                                            evt.slotId(), evt.participantId(), evt.participantType()
                                    ))
                        );

            case BookingEvent.ParticipantUnmarkedAvailable evt -> effects().asyncDone(
                            client.forEventSourcedEntity(participantSlot)
                                    .method(ParticipantSlotEntity::unmarkAvailable)
                                    .invokeAsync(new ParticipantSlotEntity.Commands.UnmarkAvailable(
                                            evt.slotId(), evt.participantId(), evt.participantType()
                                    )));

            case BookingEvent.ParticipantBooked evt -> effects().asyncDone(
                            client.forEventSourcedEntity(participantSlot)
                                    .method(ParticipantSlotEntity::book)
                                    .invokeAsync(new ParticipantSlotEntity.Commands.Book(
                                            evt.slotId(), evt.participantId(), evt.participantType(), evt.bookingId()
                                    )));

            case BookingEvent.ParticipantCanceled evt ->effects().asyncDone(

                            client.forEventSourcedEntity(participantSlot)
                                    .method(ParticipantSlotEntity::cancel)
                                    .invokeAsync(new ParticipantSlotEntity.Commands.Cancel(
                                            evt.slotId(), evt.participantId(), evt.participantType(), evt.bookingId()
                                    )));

        };
    }

    // Participant slots are keyed by a derived key made up of
    // {slotId}-{participantId}
    // We don't need the participant type here because the participant IDs
    // should always be unique/UUIDs
    private String participantSlotId(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
        };
    }
}
