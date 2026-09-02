package integration_service.service.ingestion;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    public List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String normalizedText = text.replaceAll("\\s+", " ").trim();
        int length = normalizedText.length();

        if (length <= CHUNK_SIZE) {
            chunks.add(normalizedText);
            return chunks;
        }

        int start = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);

            if (end < length && normalizedText.charAt(end) != ' ') {
                int lastSpace = normalizedText.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String chunk = normalizedText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            int nextStart = end - CHUNK_OVERLAP;
            if (nextStart <= start) {
                start = end;
            } else {
                start = nextStart;
            }
        }

        return chunks;
    }
}