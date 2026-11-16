import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Runs against the unmodified Launcher 0.9.2 classes, without starting its UI or a host. */
public final class LauncherPackageTest {
    static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static Map<String, Object> manifest(Path zip) throws Exception {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            return Mods.Json.object(Mods.Json.parse(new String(z.getInputStream(z.getEntry("video-preview/mod.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
    static void tinyZip(Path zip, String entry, String text) throws Exception {
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new ZipEntry(entry)); z.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); z.closeEntry();
        }
    }
    static Map<String, Object> release(String file, String hash, boolean draft) {
        return Map.of("draft", draft, "prerelease", true, "html_url", "https://github.com/Amo-Nova/native-video-track/releases/tag/test",
            "assets", List.of(Map.of("name", file, "browser_download_url", "https://example.invalid/" + file, "digest", "sha256:" + hash)));
    }
    public static void main(String[] args) throws Exception {
        Path candidate = Path.of(args[0]).toAbsolutePath(), root = Path.of(args[1]).toAbsolutePath();
        expect(!Files.exists(root), "Test root must be new"); Files.createDirectories(root);
        System.setProperty("launcher.data", root.toString());
        System.setProperty("java.awt.headless", "true");
        Path mods = root.resolve("mods"), downloads = root.resolve("downloads");
        String digest = Mods.sha256(candidate), name = candidate.getFileName().toString();
        var source = manifest(candidate);
        var info = Mods.infoOf(source);
        expect(info.name().equals("Native Video Track") && info.author().equals("Amo Nova"), "Display metadata");
        expect(info.builtFor().equals("6.1.7") && info.requires().size() == 3, "Compatibility/dependencies");
        String catalogJson = Mods.Json.write(List.of(release("video-preview-0.5.0-dev.2.zip", digest, false),
            release(name, digest, false), release("video-preview-99.0.0.zip", digest, true)));
        var catalog = Mods.parseCatalog(catalogJson, "Amo-Nova/native-video-track", Pattern.compile("video-preview-.*\\.zip"));
        var chosen = catalog.get("video-preview");
        expect(catalog.size() == 1 && chosen.version().equals("0.5.0-dev.3"), "Highest published prerelease selected; draft excluded");
        var check = Mods.check(candidate, mods);
        expect(check.version().equals(chosen.version()), "Checked archive version");

        // The previous-version fixture is metadata only; it is not a previous executable release.
        var old = Mods.Json.object(Mods.Json.parse(Mods.Json.write(source)));
        Mods.Json.object(old.get("mod")).put("version", "0.5.0-dev.2");
        Path oldZip = root.resolve("old-fixture.zip"); tinyZip(oldZip, "video-preview/mod.json", Mods.Json.write(old));
        Mods.install(Mods.check(oldZip, mods), mods);
        Path settings = root.resolve("config/mods/video-preview/settings.properties");
        Path project = root.resolve("projects/fixture/Video/media.bin");
        Files.createDirectories(settings.getParent()); Files.writeString(settings, "placement=left\n");
        Files.createDirectories(project.getParent()); Files.writeString(project, "synthetic media sentinel");
        String settingsSha = Mods.sha256(settings), mediaSha = Mods.sha256(project);

        Map<String, Mods.Available> all = new TreeMap<>(catalog);
        Map<String, Path> inputs = new HashMap<>(); inputs.put("video-preview", candidate);
        for (int i = 2; i < args.length; i++) {
            Path dep = Path.of(args[i]).toAbsolutePath(); var c = Mods.check(dep, mods);
            all.put(c.id(), new Mods.Available(c.id(), c.version(), "https://example.invalid/" + dep.getFileName(), Mods.sha256(dep), "fixture/frameworks", "https://example.invalid"));
            inputs.put(c.id(), dep);
        }
        var notes = Mods.installAll(List.of(chosen), all, mods, downloads,
            (a, target) -> Files.copy(inputs.get(a.id()), target), System.out::println);
        expect(notes.isEmpty(), "All dependencies resolved: " + notes);
        expect(Mods.installed(mods).size() == 4 && Mods.installed(mods).get("video-preview").version().equals("0.5.0-dev.3"), "Update and real framework packages installed");
        expect(settingsSha.equals(Mods.sha256(settings)) && mediaSha.equals(Mods.sha256(project)), "External settings/media changed");
        String helperSha = Mods.sha256(mods.resolve("video-preview/helper.jar"));

        boolean failed = false;
        try {
            Mods.installAll(List.of(chosen), all, mods, downloads, (a, target) -> { Files.writeString(target, "partial"); throw new IOException("simulated interrupted transfer"); }, s -> {});
        } catch (IOException expected) { failed = true; }
        expect(failed && helperSha.equals(Mods.sha256(mods.resolve("video-preview/helper.jar"))), "Interrupted transfer replaced working package");
        failed = false;
        try {
            Mods.installAll(List.of(chosen), all, mods, downloads, (a, target) -> Files.writeString(target, "bad digest"), s -> {});
        } catch (IOException expected) { failed = true; }
        expect(failed && helperSha.equals(Mods.sha256(mods.resolve("video-preview/helper.jar"))), "Checksum failure replaced working package");
        Path invalid = root.resolve("invalid.zip"); tinyZip(invalid, "../escape.txt", "must be rejected");
        failed = false;
        try { Mods.check(invalid, mods); } catch (IOException expected) { failed = true; }
        expect(failed && !Files.exists(root.resolve("escape.txt")), "Unsafe archive accepted");
        Path truncated = root.resolve("truncated.zip"); Files.write(truncated, Arrays.copyOf(Files.readAllBytes(candidate), 256));
        failed = false;
        try { Mods.check(truncated, mods); } catch (IOException expected) { failed = true; }
        expect(failed && helperSha.equals(Mods.sha256(mods.resolve("video-preview/helper.jar"))), "Truncated archive changed install");
        try (var paths = Files.list(mods)) { expect(paths.noneMatch(p -> p.getFileName().toString().startsWith(Mods.STAGING_PREFIX)), "Staging directory leaked"); }
        try (var paths = Files.list(downloads)) { expect(paths.findAny().isEmpty(), "Partial download retained"); }
        var missing = Mods.installAll(List.of(chosen), catalog, root.resolve("missing-mods"), root.resolve("missing-downloads"),
            (a, target) -> Files.copy(candidate, target), s -> {});
        expect(missing.size() == 3, "Missing dependencies not reported");
        System.out.println("LAUNCHER_092_ACCEPTANCE_OK catalog, metadata, real-archive install, dependency resolution, update, failed transfer, bad digest, invalid/truncated ZIP, external state preserved");
    }
}
