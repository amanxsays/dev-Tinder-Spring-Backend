package integration_service.service.routing;

import integration_service.model.IntentType;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class FastIntentService {

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "(?iu)^(?:hi+|he+y+|hello+|greetings|good\\s+(?:morning|afternoon|evening))"
                    + "(?:\\s+(?:there|everyone|all|bot|ai))?[\\s\\p{Punct}\\p{So}\\uFE0F]*$");
    private static final Pattern BOT_QA_PATTERN = Pattern.compile(
            "(?iu)^(?:who are you|what can you do|how do you work|what are your capabilities)[\\s\\p{Punct}]*$");

    public IntentType classifyIntent(String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return IntentType.COMPLEX_QUERY;
        }

        String cleanedQuery = userQuery.trim();

        if (GREETING_PATTERN.matcher(cleanedQuery).matches()) {
            return IntentType.GREETING_ACK;
        }

        if (BOT_QA_PATTERN.matcher(cleanedQuery).matches()) {
            return IntentType.BOT_QA;
        }

        return IntentType.COMPLEX_QUERY;
    }
}
