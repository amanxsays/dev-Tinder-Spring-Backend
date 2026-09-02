package integration_service.service.ingestion;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;

@Service
public class DocumentParsingService {

    private final Tika tika;

    public DocumentParsingService() {
        this.tika = new Tika();
    }

    public String parseFromUrl(String fileUrl) throws Exception {
        URL url = new URI(fileUrl).toURL();
        try (InputStream stream = url.openStream()) {
            return tika.parseToString(stream);
        }
    }
}