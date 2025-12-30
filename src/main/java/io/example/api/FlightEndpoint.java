package io.example.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.example.application.BookingSlotEntity;
import io.example.application.ParticipantSlotsView;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.workflows.FlightBookingWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import io.example.application.ParticipantSlotsView.SlotList;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/flight")
public class FlightEndpoint extends AbstractHttpEndpoint {
    private final Logger log = LoggerFactory.getLogger(FlightEndpoint.class);

    private final ComponentClient componentClient;

    public FlightEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Creates a new booking. All three identified participants will
    // be considered booked for the given timeslot, if they are all
    // "available" at the time of booking.
    @Post("/bookings/{slotId}")
    public HttpResponse createBooking(String slotId, BookingRequest request) {
        try{
            validateSlotId(slotId);
        } catch(IllegalArgumentException e){
            throw HttpException.badRequest("Cannot schedule an appointment for past dates");

        }
        log.info("Creating booking for slot {}: {}", slotId, request);

        var command = new FlightBookingWorkflow.BookingCommand(slotId, request);
         componentClient
                 .forWorkflow(slotId)
                 .method(FlightBookingWorkflow::startWorkflow)
                 .invoke(command);

        return HttpResponses.created();

    }

    // Cancels an existing booking. Note that both the slot
    // ID and the booking ID are required.
    @Delete("/bookings/{slotId}/{bookingId}")
    public HttpResponse cancelBooking(String slotId, String bookingId) {
        log.info("Canceling booking id {}", bookingId);
        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::cancelBooking)
                .invoke(bookingId);

        return HttpResponses.ok();
    }

    // Retrieves all slots in which a given participant has the supplied status.
    // Used to retrieve bookings and slots in which the participant is available
    @Get("/slots/{participantId}/{status}")
    public SlotList slotsByStatus(String participantId, String status) {
             return componentClient
                    .forView()
                    .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                    .invoke(new ParticipantSlotsView.ParticipantStatusInput(participantId, status));


    }

    // Returns the internal availability state for a given slot
    @Get("/availability/{slotId}")
    public Timeslot getSlot(String slotId) {
        try{
            validateSlotId(slotId);
        } catch(IllegalArgumentException e){
            throw HttpException.badRequest("slot id not a bad request");

        }
        return componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::getSlot)
                .invoke();

    }

    // Indicates that the supplied participant is available for booking
    // within the indicated time slot
    @Post("/availability/{slotId}")
    public HttpResponse markAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;

        try {
            validateSlotId(slotId);
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());

        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type or wrong Slot id format");
        }

        log.info("Marking timeslot available for entity {}", slotId);

        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::markSlotAvailable)
                .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(new Participant(request.participantId, participantType)));


        return HttpResponses.ok();
    }

    // Unmarks a slot as available for the given participant.
    @Delete("/availability/{slotId}")
    public HttpResponse unmarkAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;
        try {
            validateSlotId(slotId);
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }

        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::unmarkSlotAvailable)
                .invoke(new BookingSlotEntity.Command.UnmarkSlotAvailable(new Participant(request.participantId, participantType)));


        return HttpResponses.ok();
    }

    // Public API representation of a booking request
    public record BookingRequest(
            String studentId, String aircraftId, String instructorId, String bookingId) {
    }

    // Public API representation of an availability mark/unmark request
    public record AvailabilityRequest(String participantId, String participantType) {
    }

    /**
     * Validates that a slotId is in the correct 'YYYY-MM-DD-HH' format
     * and represents a time in the future.
     * Throws HttpException if validation fails.
     */
    private LocalDateTime validateSlotId(String slotId) {
        // We append ":00" to match the hour, as the format requires minutes
        String dateTimeString = slotId + ":00";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

        LocalDateTime slotTime;
        try {
            slotTime = LocalDateTime.parse(dateTimeString, formatter);
        } catch (DateTimeParseException e) {
            log.warn("Invalid slotId format for '{}'", slotId);
            throw HttpException.badRequest("Invalid slotId format. Expected 'YYYY-MM-DD-HH'.");
        }

        // Check if the slot time is in the past
        if (slotTime.isBefore(LocalDateTime.now())) {
            log.warn("SlotId '{}' is in the past", slotId);
            throw HttpException.badRequest("Slot is in the past. Only future slots can be modified.");
        }

        return slotTime;
    }
}
