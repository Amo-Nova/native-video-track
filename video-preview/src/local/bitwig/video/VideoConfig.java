package local.bitwig.video;

import com.google.gson.Gson;

/** A single atomic project setting. No global fallback and no project sidecar. */
public record VideoConfig(int schema, String file, double offset, boolean enabled) {
    private static final Gson JSON = new Gson();
    public static final VideoConfig EMPTY = new VideoConfig(1, "", 0, true);
    public VideoConfig {
        if (schema != 1) throw new IllegalArgumentException("不支持的视频设置版本：" + schema);
        if (file == null || !Double.isFinite(offset) || Math.abs(offset) > 86400)
            throw new IllegalArgumentException("视频设置无效");
    }
    public String encode() { return JSON.toJson(this); }
    public static VideoConfig decode(String value) {
        if (value == null || value.isBlank()) return EMPTY;
        VideoConfig c = JSON.fromJson(value, VideoConfig.class);
        if (c == null) throw new IllegalArgumentException("视频设置为空");
        return c;
    }
}
