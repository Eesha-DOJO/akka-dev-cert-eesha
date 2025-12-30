package io.example.domain;

import io.example.api.FlightEndpoint;
import io.example.application.FlightConditionsAgent;

import java.util.Optional;

public record FlightBookingState(
        String slotId,
        Optional<FlightConditionsAgent.ConditionsReport> agentWeatherCheckResponse,
        FlightEndpoint.BookingRequest request

    ){

    public static FlightBookingState initial(
            String slotId,
            Optional<FlightConditionsAgent.ConditionsReport> agentWeatherCheckResponse,
            FlightEndpoint.BookingRequest request

    ){
        return new FlightBookingState(
                slotId,
                Optional.empty(),
                request



        );
    }

    public static FlightBookingState withWeatherCheck(
            String slotId,
            Optional<FlightConditionsAgent.ConditionsReport> agentWeatherCheckResponse,
            FlightEndpoint.BookingRequest request

    ){
        return new FlightBookingState(
                slotId,
                agentWeatherCheckResponse,
                request



        );
    }




}
