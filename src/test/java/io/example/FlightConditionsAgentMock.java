package io.example;

import io.example.application.FlightConditionsAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlightConditionsAgentMock {
    @Test
    public void testAgentParsesWeatherFromMock() {
        // 1. DEFINE THE SCENARIO
        String testSlotId = "2025-12-30-10"; // Date we are looking for

        // 2. CREATE THE MOCK DATA (The "Fake Internet")
        // create a JSON string that matches the structure your Agent expects.
        String mockJsonResponse = """
    {
      "forecastHours": [
        {
          "interval": { 
              "startTime": "2025-12-30T10:00:00Z", 
              "endTime": "2025-12-30:00:00Z" 
          },
          "thunderstormProbability": 0,
          "wind": { 
              "speed": { "value": 15, "unit": "km/h" },
              "gust": { "value": 20, "unit": "km/h" }
          },
          "visibility": { "distance": 10000, "unit": "meters" },
          "precipitation": { 
              "probability": { 
                  "percent": 0, 
                  "type": "none" 
              } 
          }
        }
      ]
    }
""";

        // 3. DEFINE THE SERVICE
        // always returns the string.
        FlightConditionsAgent.WeatherService mockService = url -> mockJsonResponse;

        // 4. INSTANTIATE AGENT (Using the Testing Constructor)
        // inject the mock.
        FlightConditionsAgent agent = new FlightConditionsAgent(mockService);

        // 5. RUN THE TEST
        // Call the tool method directly.
        String result = agent.getWeatherForecast(testSlotId);

        // 6. VERIFY
        // We expect the result to contain the specific hour data we mocked
        assertTrue(result.contains("\"startTime\":\"2025-12-30T10:00:00Z\""));
        assertTrue(result.contains("\"wind\""));
    }
}
