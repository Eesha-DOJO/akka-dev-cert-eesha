package io.example.workflows;


import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import io.example.api.FlightEndpoint;
import io.example.application.BookingSlotEntity;
import io.example.domain.FlightBookingState;
import io.example.application.FlightConditionsAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;


@Component(id="flight-booking-workflow")
public class FlightBookingWorkflow extends Workflow<FlightBookingState> {
    private static final Logger log = LoggerFactory.getLogger(FlightBookingWorkflow.class);
    private final ComponentClient componentClient;

    public FlightBookingWorkflow(
            ComponentClient componentClient) {
        this.componentClient = componentClient;

    }

    @Override
    public Workflow.WorkflowSettings settings() {
        // This is so it doesnt timeout when LLM is generating response
        return Workflow.WorkflowSettings.builder()
                .stepTimeout(
                        FlightBookingWorkflow::updateStateWithWeatherConditions,
                        Duration.ofSeconds(30)) // 30-second timeout for this step
                .build();
    }

    public record BookingCommand(String slotId, FlightEndpoint.BookingRequest request){}

    public Effect<Done> startWorkflow(BookingCommand cmd) {
//        checks if work flow has already started, if so then it returns a done effect as the step is already done.
        if(currentState() != null) {
            return effects().reply(Done.getInstance());
        }

        var newState = FlightBookingState.initial(cmd.slotId, Optional.empty(), cmd.request);
        return effects()
                .updateState(newState)
                .transitionTo(FlightBookingWorkflow::updateStateWithWeatherConditions)
                .thenReply(Done.getInstance());
    }

    private StepEffect updateStateWithWeatherConditions() {

        var response = componentClient
                .forAgent()
                .inSession(UUID.randomUUID().toString()) // Use a new session for each request
                .method(FlightConditionsAgent::query)
                .invoke(currentState().slotId());

        log.info(response.toString());

        var newState = FlightBookingState.withWeatherCheck(currentState().slotId(), Optional.of(response), currentState().request());
        return stepEffects()
                .updateState(newState)
                .thenTransitionTo(FlightBookingWorkflow::bookOrBlockSlot);
    }




    private StepEffect bookOrBlockSlot() {
        FlightConditionsAgent.ConditionsReport agentResponse = currentState().agentWeatherCheckResponse().get();
        if(agentResponse.meetsRequirements()) {
            componentClient
                    .forEventSourcedEntity(currentState().slotId())
                    .method(BookingSlotEntity::bookSlot)
                    .invoke(new BookingSlotEntity.Command.BookReservation(currentState().request().studentId(), currentState().request().aircraftId(), currentState().request().instructorId(), currentState().request().bookingId()));

            log.info("Booking slot booked successfully");

        }
        else{
            log.info("Unable to book slot as weather conditions are dangerous");

        }
        return stepEffects().thenEnd();

    }




}
