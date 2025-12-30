package io.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        var event= new BookingEvent.ParticipantMarkedAvailable(
                entityId, cmd.participant().id(), cmd.participant().participantType());
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        var event= new BookingEvent.ParticipantUnmarkedAvailable(
                entityId, cmd.participant().id(), cmd.participant().participantType()
        );
        return effects()
                .persist(event)
                .thenReply(newState -> Done.done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        if(currentState().isBookable(cmd.studentId, cmd.aircraftId, cmd.instructorId)){
            var studentEvent = new BookingEvent.ParticipantBooked(entityId, cmd.studentId(), Participant.ParticipantType.STUDENT, cmd.bookingId());
            var aircraftEvent = new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId(), Participant.ParticipantType.AIRCRAFT, cmd.bookingId());
            var instructorEvent= new BookingEvent.ParticipantBooked(entityId, cmd.instructorId(), Participant.ParticipantType.INSTRUCTOR, cmd.bookingId());
            return effects()
                    .persist(studentEvent, aircraftEvent, instructorEvent)
                    .thenReply(newState -> Done.done());

        }
        else{
            return effects().error("Slot is not bookable. Not all participants are available.");
        }

    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        var booking = currentState().findBooking(bookingId);
        if(booking.isEmpty()){
            return effects().error("No booking to cancel");
        }
        else{
            /// Create the 3 ParticipantCanceled events directly
            var studentEvent = new BookingEvent.ParticipantCanceled(
                    entityId,
                    booking.get(0).participant().id(),
                    booking.get(0).participant().participantType(),
                    bookingId);

            var aircraftEvent = new BookingEvent.ParticipantCanceled(
                    entityId,
                    booking.get(1).participant().id(),
                    booking.get(1).participant().participantType(),
                    bookingId);

            var instructorEvent = new BookingEvent.ParticipantCanceled(
                    entityId,
                    booking.get(2).participant().id(),
                    booking.get(2).participant().participantType(),
                    bookingId);

            return effects()
                    .persist(studentEvent, aircraftEvent, instructorEvent)
                    .thenReply(newState -> Done.done());

        }



    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantMarkedAvailable evt ->
                // call the 'reserve' method and pass the event to it.
                    currentState().reserve(evt);
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                currentState().unreserve(evt);
            case BookingEvent.ParticipantBooked evt ->
                    currentState().book(evt);
            case BookingEvent.ParticipantCanceled evt ->
                // This method removes all participants for the bookingId
                    currentState().cancelBooking(evt.bookingId());


        };



    };

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
