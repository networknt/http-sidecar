package com.networknt;

import com.networknt.chaos.ChaosMonkeyPostHandler;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChaosMonkeyBinaryGuardTest {
    private static boolean referencesJackson2ConfigMapper(InputStream input) throws Exception {
        assertNotNull(input, "Missing chaos-monkey class fixture");
        try (input) {
            String constants = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(constants.contains("com/networknt/config/Config"), "Expected Config caller");
            return constants.contains("()Lcom/fasterxml/jackson/databind/ObjectMapper;");
        }
    }

    @Test
    void publishedJackson2BinaryIsRejectedIndependently() throws Exception {
        assertTrue(referencesJackson2ConfigMapper(getClass().getResourceAsStream(
                "/legacy-chaos/ChaosMonkeyPostHandler.class.bin")));
    }

    @Test
    void applicationChaosHandlerMustUseJackson3ConfigAbi() throws Exception {
        assertFalse(referencesJackson2ConfigMapper(ChaosMonkeyPostHandler.class.getResourceAsStream(
                "/com/networknt/chaos/ChaosMonkeyPostHandler.class")),
                "Stale Jackson 2 chaos-monkey binary: rebuild the dependency from qualified Jackson 3 sources before packaging");
    }
}
