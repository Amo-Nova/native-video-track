package local.bitwig.video;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Bounded-memory media I/O. Called only from workers, never the host event thread. */
final class MediaAssets {
    record Asset(String id,String original,String relative,String audioRelative,long bytes,long audioBytes,
                 double duration,int channels,int sampleRate) {}
    static final class Cancelled extends IOException { Cancelled(){super("操作已取消，原片段保持不变");} }
    private final Path player,logs;
    MediaAssets(Path player,Path logs){this.player=player;this.logs=logs;}

    static String identity(String value)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24);
    }
    Asset collectVideo(Path root,Path source,double duration,Consumer<String> progress)throws Exception{
        source=source.toRealPath();var state=Files.readAttributes(source,BasicFileAttributes.class);
        String id=identity(source+"\n"+state.size()+"\n"+state.lastModifiedTime().toMillis());
        String relative="Video/"+id+"/"+source.getFileName();
        copy(source,under(root,relative),state.size(),progress,()->false,"收集视频");
        return new Asset(id,source.toString(),relative,"",state.size(),0,duration,0,0);
    }
    void recoverAudio(Path root,Asset asset,Consumer<String> progress)throws Exception{
        Path video=under(root,asset.relative()),audio=under(root,asset.audioRelative());int aid=0;
        Files.createDirectories(logs);
        try(MpvConnection probe=new MpvConnection(player,logs.resolve("recover-"+asset.id()+".log"),false)){
            probe.load(video);
            for(JsonElement item:probe.get("track-list").getAsJsonArray()){
                JsonObject track=item.getAsJsonObject();
                if(!"audio".equals(track.get("type").getAsString())||flag(track,"external"))continue;
                if(aid==0||flag(track,"default")){aid=track.get("id").getAsInt();if(flag(track,"default"))break;}
            }
        }
        if(aid==0)throw new IOException("已收集视频中找不到原音轨");
        extract(video,audio,aid,asset.duration(),asset.sampleRate(),asset.channels(),progress,()->false);
        if(Files.size(audio)!=asset.audioBytes())throw new IOException("恢复的音频长度与原素材不同，请重新导入");
    }

    Asset prepare(Path root,Path source,Consumer<String> progress,BooleanSupplier cancelled)throws Exception{
        check(cancelled);Files.createDirectories(logs);
        source=source.toRealPath();
        var before=Files.readAttributes(source,BasicFileAttributes.class);
        PlayerRuntime.ensure(player,progress,cancelled);
        String fingerprint=source+"\n"+before.size()+"\n"+before.lastModifiedTime().toMillis();
        String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24);
        progress.accept("检查视频 · "+source.getFileName());
        double duration;int aid=0,channels=0,rate=0;
        try(MpvConnection probe=new MpvConnection(player,logs.resolve("probe-"+id+".log"),false)){
            duration=probe.load(source);check(cancelled);
            JsonArray tracks=probe.get("track-list").getAsJsonArray();
            for(JsonElement item:tracks){
                JsonObject track=item.getAsJsonObject();
                if(!"audio".equals(track.get("type").getAsString())||flag(track,"external"))continue;
                if(aid==0||flag(track,"default")){
                    aid=track.get("id").getAsInt();channels=number(track,"demux-channel-count",2);rate=number(track,"demux-samplerate",48000);
                    if(flag(track,"default"))break;
                }
            }
        }
        String relative="Video/"+id+"/"+source.getFileName();
        Path video=under(root,relative);
        copy(source,video,before.size(),progress,cancelled,"收集视频");
        String audioRelative=aid==0?"":"samples/Video/"+id+".wav";
        long audioBytes=0;
        if(aid!=0){
            Path audio=under(root,audioRelative);
            Path done=audio.resolveSibling(audio.getFileName()+".complete");
            if(!Files.isRegularFile(audio)||!Files.isRegularFile(done)||!Files.readString(done).equals(Long.toString(Files.size(audio)))){
                extract(video,audio,aid,duration,rate,channels,progress,cancelled);
                Files.writeString(done,Long.toString(Files.size(audio)),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            }
            audioBytes=Files.size(audio);
        }
        var after=Files.readAttributes(source,BasicFileAttributes.class);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("导入过程中源视频发生变化，请重试。");
        check(cancelled);
        return new Asset(id,source.toString(),relative,audioRelative,before.size(),audioBytes,duration,channels,rate);
    }
    private static boolean flag(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    private static int number(JsonObject o,String key,int fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsInt():fallback;}

    private void extract(Path source,Path target,int aid,double duration,int rate,int channels,
                         Consumer<String> progress,BooleanSupplier cancelled)throws Exception{
        Files.createDirectories(target.getParent());
        Path partial=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".partial");
        Process process=null;
        try{
            Path readyPlayer=PlayerRuntime.ensure(player,progress,cancelled);
            List<String> args=List.of(readyPlayer.resolve("mpv.exe").toString(),"--no-config","--load-scripts=no","--no-video",
                "--aid="+aid,"--sub=no","--audio-file-auto=no","--ytdl=no","--input-terminal=no","--terminal=no",
                "--of=wav","--ofopts=rf64=auto","--oac=pcm_f32le","--audio-channels=auto",
                "--af=lavfi=[aresample=async=1:first_pts=0]","--o="+partial,
                "--log-file="+logs.resolve("audio-"+target.getFileName()+".log"),source.toString());
            ProcessBuilder pb=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.environment().remove("JAVA_TOOL_OPTIONS");process=pb.start();
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)){
                check(cancelled);
                long bytes=Files.exists(partial)?Files.size(partial):0;
                int percent=(int)Math.min(99,bytes*100.0/Math.max(1,duration*rate*Math.max(1,channels)*4));
                progress.accept("提取音频 "+percent+"% · "+source.getFileName());
            }
            check(cancelled);
            if(process.exitValue()!=0||!Files.isRegularFile(partial)||Files.size(partial)<128)
                throw new IOException("音频提取失败；请查看 "+logs.resolve("audio-"+target.getFileName()+".log"));
            try(RandomAccessFile f=new RandomAccessFile(partial.toFile(),"r")){
                byte[] magic=new byte[4];f.readFully(magic);String header=new String(magic,java.nio.charset.StandardCharsets.US_ASCII);
                if(!(header.equals("RIFF")||header.equals("RF64")))throw new IOException("音频输出不是完整 WAV");
            }
            publish(partial,target);
        }finally{
            if(process!=null&&process.isAlive()){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();}
            Files.deleteIfExists(partial);
        }
    }
    static Path under(Path root,String relative)throws IOException{
        Path normalized=root.toAbsolutePath().normalize(),result=normalized.resolve(relative).normalize();
        if(relative.isBlank()||Path.of(relative).isAbsolute()||!result.startsWith(normalized)||result.equals(normalized))
            throw new IOException("无效的工程媒体路径");
        return result;
    }
    static void check(BooleanSupplier cancelled)throws Cancelled{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new Cancelled();}

    static void copy(Path source,Path target,long expected,Consumer<String> progress,BooleanSupplier cancelled,String label)throws Exception{
        check(cancelled);
        if(Files.isRegularFile(target)){
            if(Files.size(target)==expected)return;
            throw new IOException("目标素材大小不符，未覆盖："+target);
        }
        if(!Files.isRegularFile(source)||Files.size(source)!=expected)throw new IOException("素材缺失或已改变："+source);
        Files.createDirectories(target.getParent());
        Path partial=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".partial");
        var sourceState=Files.readAttributes(source,BasicFileAttributes.class);
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0,last=0;
            byte[] buffer=new byte[1024*1024];
            try(InputStream in=Files.newInputStream(source);OutputStream out=Files.newOutputStream(partial,StandardOpenOption.CREATE_NEW)){
                for(int n;(n=in.read(buffer))>=0;){check(cancelled);out.write(buffer,0,n);digest.update(buffer,0,n);total+=n;
                    long now=System.nanoTime();if(now-last>200_000_000){progress.accept(label+" "+(int)(total*100.0/Math.max(1,expected))+"% · "+target.getFileName());last=now;}}
            }
            var after=Files.readAttributes(source,BasicFileAttributes.class);
            if(total!=expected||after.size()!=expected||!sourceState.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("复制过程中源素材改变");
            progress.accept("校验素材 · "+target.getFileName());
            MessageDigest verify=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(partial)){for(int n;(n=in.read(buffer))>=0;){check(cancelled);verify.update(buffer,0,n);}}
            if(!MessageDigest.isEqual(digest.digest(),verify.digest()))throw new IOException("素材复制校验失败");
            check(cancelled);publish(partial,target);
        }finally{Files.deleteIfExists(partial);}
    }
    private static void publish(Path partial,Path target)throws IOException{
        try{Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(partial,target);}
    }
}
