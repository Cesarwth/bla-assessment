package com.ballastlane.assessment.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the bundled demo snapshot (real PokeAPI responses saved once), so starting the demo needs no network call.
 * Its own mapper keeps the file format independent from the web layer's serialization settings.
 */
public class DemoDataReader {

    private static final String RESOURCE = "demo/pokemon.json";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public List<DemoEntry> read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return JSON.readValue(in, new TypeReference<List<DemoEntry>>() { });
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Could not read the demo data (" + RESOURCE + ")", e);
        }
    }
}
