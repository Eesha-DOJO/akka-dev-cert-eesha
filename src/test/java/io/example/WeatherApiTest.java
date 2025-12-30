package io.example;

import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WeatherApiTest extends TestKitSupport {
    private static final String LATITUDE = "51.5072";
    private static final String LONGITUDE = "-0.1276";

    @Test
    public void getWeather() {
        String apiKey = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
        String url = String.format("https://weather.googleapis.com/v1/forecast/hours:lookup?key=%s&location.latitude=%s&location.longitude=%s", apiKey, LATITUDE, LONGITUDE );
        try{
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("API Request Failed. Status: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            WeatherResponse weatherResponse = mapper.readValue(response.body(), WeatherResponse.class);


            for (ForecastHour hour : weatherResponse.forecastHours) {
                String startTimeApi = hour.interval.startTime;
                Instant timestamp = Instant.parse(startTimeApi);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);
                String formattedStartTime = formatter.format(timestamp);

                if (formattedStartTime.equals("2025-12-23-09") ) {
                    System.out.println(mapper.writeValueAsString(hour));
                }
            }
            System.out.println("Forecast not available for this timeslot");

        }
        catch(Exception e) {
            System.out.println("{\"error\": \"Failed to retrieve weather data: " + e.getMessage() + "\"}");
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
