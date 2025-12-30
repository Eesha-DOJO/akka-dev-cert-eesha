package io.example.application;
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/*
 * The flight conditions agent is responsible for making a determination about the flight
 * conditions for a given day and time. You will need to clearly define the success criteria
 * for the report and instruct the agent (in the system prompt) about the schema of
 * the results it must return (the ConditionsReport).
 *
 * Also be sure to provide clear instructions on how and when tools should be invoked
 * in order to generate results.
 *
 * Flight conditions criteria don't need to be exhaustive, but you should supply the
 * criteria so that an agent does not need to make an external HTTP call to query
* the condition limits.
 */

@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {
    public interface WeatherService {
        String fetchForecast(String url);
    }
    private final WeatherService weatherService;

//    for mock tests
    public FlightConditionsAgent(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

//    default constructor
    public FlightConditionsAgent() {
        this.weatherService = url -> {
            try {
                var client = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .GET().build();
                return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            } catch (Exception e) { throw new RuntimeException(e); }
        };
    }

    /* use for bad conditions */
//    private static final String LATITUDE = "44.2705";
//    private static final String LONGITUDE = "-71.3033";
    /* use for good conditions */
    private static final String LATITUDE = "51.5072";
    private static final String LONGITUDE = "-0.1276";
    private static final Logger log = LoggerFactory.getLogger(FlightConditionsAgent.class);


    public record ConditionsReport(
            String timeSlotId,
            boolean meetsRequirements,
            Integer thunderstormProbability,
            Integer windSpeed,
            Integer windGust,
            Integer visibility,
            Integer precipitation ) {
        public static String getSample(){
            var res=
                    new ConditionsReport(
                            "2025-12-25",
                            true,
                            10,
                            10,
                            10,
                            10,
                            10
                    );
            return JsonSupport.encodeToString(res);
        }
    }


    private static final String SYSTEM_MESSAGE = """
            You are an agent responsible for evaluating flight conditions... You have a Function Tool which you have to use to
            retrieve the weather for given timeSlotId. Always call the getWeatherForecast tool to retrieve the weather for the given timeSlotId before deciding.
            Do not answer based only on your own knowledge; you must use the tool.” If you receive a json object:
             you need to check the following parameters are within the defined levels:
            - visibility must be above 4km
            - wind speed must be below 35
            - wind gust must be below 35
            - thunderstormProbability must below 40
            
            if not then you must return 'false' for the meetsRequirements parameter. Also if precipitation{"type"} is ice/snow AND the precipitation{"probability"} is anything above 0 you must also return 'false'.
            
            if the response from the Function Tool is a string that says "Forecast not available for this timeslot". Then by default return "true" for this case.
            
            Makesure to populate the report with the values you receive from the tool.
            
            * MANDATORY OUTPUT FORMAT: *
                    Respond in a JSON format like the following example:
                    %s
            """.formatted(ConditionsReport.getSample());


    public Effect<ConditionsReport> query(String timeSlotId) {
        return effects().systemMessage(SYSTEM_MESSAGE)
                .userMessage("Validate the weather conditions for timeslot:" + timeSlotId + "Always call the getWeatherForecast tool to retrieve the weather for the given timeSlotId before deciding.\n" +
                        "Do not answer based only on your own knowledge")
                .responseAs(ConditionsReport.class)
                .onFailure(throwable -> {
                    if (throwable instanceof JsonParsingException) {
                        // Fallback
                        return new ConditionsReport(
                                timeSlotId,
                                true,
                                0, 0, 0, 0, 0
                        );
                    } else {
                        throw new RuntimeException(throwable);
                    }
                })
                .thenReply();
    }

    /*
     * You can choose to hard code the weather conditions for specific days or you
     * can actually
     * communicate with an external weather API. You should be able to get both
     * suitable weather
     * conditions and poor weather conditions from this tool function for testing.
     */
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
     public String getWeatherForecast(String timeSlotId) {
        String apiKey = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
        String url = String.format("https://weather.googleapis.com/v1/forecast/hours:lookup?key=%s&location.latitude=%s&location.longitude=%s", apiKey, LATITUDE, LONGITUDE );
        try{
            String response = this.weatherService.fetchForecast(url);

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            WeatherResponse weatherResponse = mapper.readValue(response, WeatherResponse.class);


            for (ForecastHour hour : weatherResponse.forecastHours) {
                String startTimeApi = hour.interval.startTime;
                Instant timestamp = Instant.parse(startTimeApi);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);
                String formattedStartTime = formatter.format(timestamp);

                if (formattedStartTime.equals(timeSlotId) ) {
                    return mapper.writeValueAsString(hour) ;
                }
            }
            return "Forecast not available for this timeslot";

        }
        catch(Exception e) {
            return "{\"error\": \"Failed to retrieve weather data: " + e.getMessage() + "\"}";
        }

    }



    static class WeatherResponse {
        public List<ForecastHour> forecastHours;
    }

    static class ForecastHour {
        public Interval interval;
        public Integer thunderstormProbability;
        public Wind wind;
        public String windGust;
        public Visibility visibility;
        public Precipitation precipitation;
    }

    static class Precipitation {
        public Probability probability;
    }

    static class Visibility {
        public Integer distance;
        public String unit;
    }

    static class Probability {
        public Integer percent;
        public String type;

    }

    static class Interval {
        public String startTime;
        public String endTime;
    }

    static class Wind {
        public Speed speed;
        public Gust gust;
    }

    static class Speed {
        public Integer value;
        public String unit;
    }
    static class Gust {
        public Integer value;
        public String unit;
    }
}