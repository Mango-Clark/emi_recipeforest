package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class FabricMetadataTest {
    @Test
    void contactPointsToThisProjectInsteadOfFabricExamples() throws Exception {
        String metadata = Files.readString(Path.of("..", "fabric", "src", "main", "resources", "fabric.mod.json"));
        var sources = Pattern.compile("\"sources\"\\s*:\\s*\"([^\"]+)\"").matcher(metadata);
        assertTrue(sources.find());
        assertEquals(URI.create("https://github.com/Mango-Clark/emi_recipeforest"), URI.create(sources.group(1)));
        assertFalse(metadata.contains("fabric-example-mod"));
        assertFalse(metadata.contains("https://fabricmc.net/"));
    }
}
