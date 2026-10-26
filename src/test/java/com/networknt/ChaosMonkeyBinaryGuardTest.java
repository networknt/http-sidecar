package com.networknt;

import java.io.File;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChaosMonkeyBinaryGuardTest {
    private static List<String> staleConfigReferences(byte[] bytes) {
        var model = ClassFile.of().parse(bytes);
        var stale = new ArrayList<String>();
        for (var entry : model.constantPool()) {
            if (entry instanceof MethodRefEntry method) {
                String owner = method.owner().asInternalName();
                String descriptor = method.type().stringValue();
                if ((owner.equals("com/networknt/config/Config") || owner.equals("com/networknt/config/JsonMapper"))
                        && (descriptor.contains("Lcom/fasterxml/jackson/databind/")
                        || descriptor.contains("Lcom/fasterxml/jackson/dataformat/"))) {
                    stale.add(owner + "." + method.name().stringValue() + descriptor);
                }
            }
        }
        return stale;
    }

    @Test
    void publishedJackson2BinaryIsRejectedIndependently() throws Exception {
        try (var input = getClass().getResourceAsStream("/legacy-chaos/ChaosMonkeyPostHandler.class.bin")) {
            assertNotNull(input);
            assertFalse(staleConfigReferences(input.readAllBytes()).isEmpty());
        }
    }

    @Test
    void everyApplicationChaosClassMustUseCompatibleFrameworkAbi() throws Exception {
        Set<String> scanned = new HashSet<>();
        List<String> stale = new ArrayList<>();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        for (String item : classpath.split(File.pathSeparator)) {
            Path path = Path.of(item);
            if (Files.isDirectory(path)) {
                Path classes = path.resolve("com/networknt/chaos");
                if (!Files.exists(classes)) continue;
                try (var files = Files.walk(classes)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                        String name = path.relativize(file).toString().replace(File.separatorChar, '/');
                        scanned.add(name);
                        for (String reference : staleConfigReferences(Files.readAllBytes(file))) stale.add(name + ": " + reference);
                    }
                }
            } else if (item.endsWith(".jar")) {
                try (var jar = new JarFile(item)) {
                    for (var entry : jar.stream().filter(e -> e.getName().startsWith("com/networknt/chaos/")
                            && e.getName().endsWith(".class")).toList()) {
                        scanned.add(entry.getName());
                        try (var input = jar.getInputStream(entry)) {
                            for (String reference : staleConfigReferences(input.readAllBytes())) stale.add(entry.getName() + ": " + reference);
                        }
                    }
                }
            }
        }
        assertTrue(scanned.contains("com/networknt/chaos/ChaosMonkeyPostHandler.class"));
        assertTrue(scanned.contains("com/networknt/chaos/LatencyAssaultHandler.class"));
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/chaos-abi-audit.txt"), scanned.stream().sorted().toList());
        assertTrue(stale.isEmpty(), "Stale framework method references: " + stale);
    }

    @Test
    void distributionModeRejectsRemainingJackson2Databind() throws Exception {
        var boundaries = new ArrayList<String>();
        try {
            Class<?> legacy = Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, getClass().getClassLoader());
            boundaries.add(legacy.getProtectionDomain().getCodeSource().getLocation().toString());
        } catch (ClassNotFoundException absent) {
            // Shared annotations are allowed; Jackson 2 databind is not a distribution boundary we can waive.
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/jackson2-distribution-boundary.txt"), boundaries);
        assertTrue(!Boolean.getBoolean("jackson3.requireDistribution") || boundaries.isEmpty(),
                "Jackson 3 distribution is blocked by Jackson 2 databind: " + boundaries);
    }
}
