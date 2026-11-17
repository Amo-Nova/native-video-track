package local.bitwig.video.install;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Installed entry point. No launcher environment, service or global Java options required.
 *  macOS port: user data lives in ~/Library/Application Support/BitwigVideo; the component
 *  files live beside bitwig.jar under Contents/Java/bitwig-video/releases/<version>. */
public final class InstalledRuntime {
    public static final String VERSION = "1.0.0-dev.3";
    private record Configuration(Path bindings, Path log) {}
    private static volatile Configuration configuration;
    private InstalledRuntime() {}

    public static String getenv(String key) {
        if ("BML_MOD_CONFIG".equals(key)) return configuration().bindings().toString();
        if ("BITWIG_RE_MOD_LOG".equals(key)) {
            try { return configuration().log().toString(); }
            catch (RuntimeException unavailable) { return null; } // Logging must never crash the host.
        }
        return System.getenv(key);
    }

    private static Configuration configuration() {
        Configuration result = configuration;
        if (result != null) return result;
        synchronized (InstalledRuntime.class) {
            if (configuration == null) configuration = initialize();
            return configuration;
        }
    }

    private static Configuration initialize() {
        try {
            Path jar = Path.of(InstalledRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path bin = jar.getParent();
            Path component = bin.resolve("bitwig-video/releases/" + VERSION);
            if (!Files.isRegularFile(component.resolve("component.json")))
                throw new IOException("Video component files are missing. Run Install or Restore from the video installer.");
            // The optional override is useful for an isolated acceptance copy. Normal installs need none.
            String override = System.getenv("BITWIG_VIDEO_DATA");
            Path data = override != null && !override.isBlank() ? Path.of(override).toAbsolutePath().normalize()
                : Path.of(System.getProperty("user.home"), "Library", "Application Support", "BitwigVideo").toAbsolutePath().normalize();
            Properties bindings = new Properties();
            try (InputStream input = InstalledRuntime.class.getResourceAsStream("/bitwig-video-install/bindings.properties")) {
                if (input == null) throw new IOException("Missing embedded video bindings");
                bindings.load(input);
            }
            for (String key : bindings.stringPropertyNames()) {
                String value = bindings.getProperty(key).replace("${component.root}", slash(component))
                    .replace("${user.data}", slash(data)).replace("${bitwig.root}", slash(bin.getParent()))
                    .replace("${component.uri}", component.toUri().toASCIIString().replaceAll("/$", ""))
                    .replace("${user.uri}", data.toUri().toASCIIString().replaceAll("/$", ""));
                if (value.contains("${")) throw new IOException("Unresolved video resource: " + key);
                bindings.setProperty(key, value);
            }
            for (String id : bindings.getProperty("loader.mod_ids").split(","))
                Files.createDirectories(Path.of(bindings.getProperty("mod." + id + ".data.path")));
            Files.createDirectories(Path.of(bindings.getProperty("mod.video-preview.resource.logs.path")));
            long pid = ProcessHandle.current().pid();
            Path session = data.resolve("sessions/" + Instant.now().toString().replace(':', '-') + "-" + pid);
            Files.createDirectories(session);
            Path config = session.resolve("bindings.properties");
            Path temp = session.resolve("bindings.tmp");
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {
                // ASCII escapes are compatible with both BML's InputStream and bridge UTF-8 Reader.
                bindings.store(output, "Bitwig Video installed bindings " + VERSION);
            }
            Files.move(temp, config, StandardCopyOption.ATOMIC_MOVE);
            Properties status = new Properties();
            status.setProperty("integration.version", VERSION);
            status.setProperty("video.version", "0.5.0-dev.9");
            status.setProperty("pid", Long.toString(pid));
            status.setProperty("host.jar", jar.toString());
            status.setProperty("component.root", component.toString());
            status.setProperty("bindings", config.toString());
            status.setProperty("started", Instant.now().toString());
            try (OutputStream output = Files.newOutputStream(session.resolve("startup.properties"))) {
                status.store(output, "Persistent component initialized");
            }
            return new Configuration(config, session.resolve("mod.log"));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize installed Bitwig Video " + VERSION, error);
        }
    }

    private static String slash(Path path) { return path.toString().replace('\\', '/'); }

    /** Offline installation diagnosis, using the same code as ordinary host startup. */
    public static void main(String[] args) throws Exception {
        Path bindings = Path.of(getenv("BML_MOD_CONFIG"));
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(bindings)) { p.load(input); }
        for (String key : p.stringPropertyNames()) {
            if ((key.endsWith(".path") || key.endsWith(".descriptor") || key.endsWith(".jar"))
                    && !key.contains(".target.") && !key.endsWith("data.path") && !key.equals("loader.components.path")) {
                if (!Files.exists(Path.of(p.getProperty(key)))) throw new IOException("Missing resource " + key + ": " + p.getProperty(key));
            }
        }
        // Initialize the remapping bridges as a guard against stale or double-mapped bindings.
        for (String name : bridgeNames())
            Class.forName("dev.bitwigre.bml.bridge.BmlReflectionBridge" + name);
        System.out.println("INSTALLED_RUNTIME_OK " + VERSION + " " + bindings);
    }

    private static List<String> bridgeNames() {
        // The prepared runtime's generated bridge class suffixes, resolved by scanning our own jar.
        try {
            List<String> names = new ArrayList<>();
            var stream = InstalledRuntime.class.getClassLoader().resources("dev/bitwigre/bml/bridge");
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                var uri = iterator.next().toURI();
                if (!"jar".equals(uri.getScheme())) continue;
                try (var jar = new java.util.jar.JarFile(Path.of(uri).toFile())) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String n = entries.nextElement().getName();
                        if (n.startsWith("dev/bitwigre/bml/bridge/BmlReflectionBridge") && n.endsWith(".class"))
                            names.add(n.substring("dev/bitwigre/bml/bridge/BmlReflectionBridge".length(),
                                                  n.length() - ".class".length()));
                    }
                }
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }
}
