package integration_service.service.routing;

import integration_service.model.IntentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FastIntentServiceTests {
    private final FastIntentService service = new FastIntentService();

    @ParameterizedTest
    @ValueSource(strings = {"hi", "hii", "hiii", " HIIII! ", "hello", "hellooo", "heyyy", "Hi there!", "good morning", "hi 👋"})
    void recognizesStandaloneGreetings(String query) {
        assertEquals(IntentType.GREETING_ACK, service.classifyIntent(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hi, find React developers", "hello can you compare resumes?", "hiring React developers",
            "Find React developers", "who are you and can you find candidates?", "hi\nfind Java developers"})
    void preservesSubstantiveRequests(String query) {
        assertEquals(IntentType.COMPLEX_QUERY, service.classifyIntent(query));
    }
}
