package local.bitwig.video;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** macOS player binary resolution. The mod ships no mpv binary: the player is the user's own
 *  install (Homebrew or a binary dropped into the mod's player/ resource). Resolution order:
 *  player/mpv in this mod's resources, /opt/homebrew/bin/mpv, /usr/local/bin/mpv, then PATH.
 *  A bundled player/mpv is made executable on first use; Homebrew's mpv needs its dylibs,
 *  so it is used from its own absolute path, never copied. */
final class MpvBin {
    private static volatile Path resolved;

    static Path resolve(Path player) {
        Path found = resolved;
        if (found != null && Files.isRegularFile(found)) return found;
        List<Path> candidates = new ArrayList<>();
        if (player != null) candidates.add(player.resolve("mpv"));
        candidates.add(Path.of("/opt/homebrew/bin/mpv"));
        candidates.add(Path.of("/usr/local/bin/mpv"));
        String override = System.getenv("BITWIG_VIDEO_MPV");
        if (override != null && !override.isBlank()) candidates.add(0, Path.of(override.trim()));
        for (Path candidate : candidates) {
            try {
                if (!Files.isRegularFile(candidate)) continue;
                if (!Files.isExecutable(candidate)) {
                    // A binary unpacked from a mod ZIP loses its +x bit; restore it once.
                    try {
                        Files.setPosixFilePermissions(candidate,
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
                    } catch (Exception ignored) {}
                    if (!Files.isExecutable(candidate)) continue;
                }
                resolved = candidate;
                return candidate;
            } catch (Throwable ignored) {}
        }
        Optional<Path> onPath = Optional.ofNullable(System.getenv("PATH")).flatMap(MpvBin::fromPath);
        if (onPath.isPresent()) { resolved = onPath.get(); return onPath.get(); }
        throw new IllegalStateException("找不到 mpv。请安装 mpv（brew install mpv），或把 mpv 可执行文件放到 "
            + (player == null ? "mod 的 player 目录" : player) + "，或设置 BITWIG_VIDEO_MPV。");
    }

    private static Optional<Path> fromPath(String path) {
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) continue;
            try {
                Path candidate = Path.of(dir, "mpv");
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return Optional.of(candidate);
            } catch (Throwable ignored) {}
        }
        return Optional.empty();
    }

    private MpvBin() {}
}
