package io.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event.Booked;
import io.example.application.ParticipantSlotEntity.Event.Canceled;
import io.example.application.ParticipantSlotEntity.Event.MarkedAvailable;
import io.example.application.ParticipantSlotEntity.Event.UnmarkedAvailable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            return switch(event) {
                case ParticipantSlotEntity.Event.MarkedAvailable evt ->
                    // Create/update a row with status "available"
                        effects().updateRow(new SlotRow(
                                evt.slotId(),
                                evt.participantId(),
                                evt.participantType().toString(),
                                "", // no bookingId
                                "available"
                        ));
                case ParticipantSlotEntity.Event.Booked evt ->
                    // Create/update a row with status "booked"
                        effects().updateRow(new SlotRow(
                                evt.slotId(),
                                evt.participantId(),
                                evt.participantType().toString(),
                                evt.bookingId(),
                                "booked"
                        ));
                case ParticipantSlotEntity.Event.UnmarkedAvailable evt ->
                        effects().updateRow(new SlotRow(
                                evt.slotId(),
                                evt.participantId(),
                                evt.participantType().toString(),
                                "",
                                "not available"
                        ));
                case ParticipantSlotEntity.Event.Canceled evt ->
                        effects().updateRow(new SlotRow(
                                evt.slotId(),
                                evt.participantId(),
                                evt.participantType().toString(),
                                evt.bookingId(),
                                "cancelled"
                        ));
            };
        }
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

    // @Query("SELECT .... ")
    @Query("SELECT * AS slots FROM participant_slots WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        // The queryResult() method uses the above query
        return queryResult();
    }

    // @Query("SELECT ...")
    @Query("SELECT * AS slots FROM participant_slots WHERE participantId = :participantId AND status = :status")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
