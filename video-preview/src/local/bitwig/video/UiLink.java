package local.bitwig.video;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** A local, authenticated control channel; the host JVM never initializes AWT. */
final class UiLink implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final ExecutorService tasks=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"Video-preview-controls");t.setDaemon(true);return t;});
    private ServerSocket server;
    private Process child;
    private volatile Socket client;
    private volatile long showSerial, errorSerial;
    private volatile String error="";
    private final String token=UUID.randomUUID().toString();
    private volatile Object document;
    private volatile long documentId;
    private volatile boolean closed;

    void show() { tasks.execute(()->{synchronized(this){try{showSerial++;if(child==null||!child.isAlive())launch();}catch(Exception e){BitwigBridge.MOD.log("Cannot start video controls",e);}}}); }
    void error(String text) { error=text; errorSerial++; }
    private void launch() throws Exception {
        if(server==null){
            server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
            tasks.execute(this::acceptLoop);
        }
        Path java=Path.of(System.getProperty("java.home"),"bin","javaw.exe");
        Path libs=Path.of(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String cp=BitwigBridge.MOD.resource("ui")+File.pathSeparator+libs;
        ProcessBuilder pb=new ProcessBuilder(java.toString(),"-Djava.awt.headless=false","-Dfile.encoding=UTF-8","-cp",cp,
            "local.bitwig.video.VideoControls",Integer.toString(server.getLocalPort()),token);
        pb.environment().remove("JAVA_TOOL_OPTIONS");pb.environment().remove("_JAVA_OPTIONS");pb.environment().remove("JDK_JAVA_OPTIONS");
        Path logs=BitwigBridge.MOD.resource("logs");Files.createDirectories(logs);
        pb.redirectErrorStream(true).redirectOutput(logs.resolve("controls.log").toFile());child=pb.start();
    }
    private void acceptLoop() {
        while(!closed){
            try{
                Socket s=server.accept();s.setTcpNoDelay(true);s.setSoTimeout(4000);
                BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                String hello=readLine(in);
                if(!token.equals(hello)){s.close();continue;}
                s.setSoTimeout(0);Socket old=client;client=s;if(old!=null)old.close();
                tasks.execute(()->serve(s,in));
            }catch(Exception e){if(!closed)BitwigBridge.MOD.log("Video control connection",e);}
        }
    }
    private static String readLine(Reader in)throws IOException{
        StringBuilder b=new StringBuilder();for(int c;(c=in.read())!=-1;){if(c=='\n')return b.toString();if(c!='\r')b.append((char)c);if(b.length()>65536)throw new IOException("Control message too large");}return null;
    }
    private void serve(Socket socket,BufferedReader in){
        Future<?> sender=tasks.submit(()->sendState(socket));
        try{
            String line;
            while((line=readLine(in))!=null){
                JsonObject cmd=JsonParser.parseString(line).getAsJsonObject();
                String action=cmd.get("action").getAsString();
                if(action.equals("show")){BitwigBridge.showPreview();continue;}
                if(action.equals("hide")){BitwigBridge.hidePreview();continue;}
                if(action.equals("placement")){PreviewPlacement.select(cmd.get("position").getAsString());continue;}
                if(action.equals("reset-layout")){PreviewPlacement.reset();continue;}
                if(action.equals("cancel-media")){VideoTimeline.cancel();continue;}
                BitwigBridge.Snapshot origin=BitwigBridge.snapshot();
                if(origin.document()==null||origin.document()!=document||cmd.get("document").getAsLong()!=documentId){error("工程已切换，请重新操作。");continue;}
                if(action.equals("choose"))BitwigBridge.choose(origin,Path.of(cmd.get("file").getAsString()));
                if(action.equals("import")){
                    List<Path> files=new ArrayList<>();
                    if(cmd.has("files"))for(JsonElement value:cmd.getAsJsonArray("files"))files.add(Path.of(value.getAsString()));
                    else files.add(Path.of(cmd.get("file").getAsString()));
                    bitwig.obf.drf.EO().asyncExec(()->{
                        if(BitwigBridge.currentDocument()==origin.document())VideoTimeline.importFiles(origin.document(),files,origin.document().XzC().MgQ(origin.seconds()));
                    });
                }
                if(action.equals("relink"))VideoTimeline.relink(origin.document(),cmd.get("previous").getAsString(),Path.of(cmd.get("file").getAsString()));
                if(action.equals("separate")||action.equals("bind-audio")||action.equals("collect")||action.equals("lock-video-time"))bitwig.obf.drf.EO().asyncExec(()->{
                    if(BitwigBridge.currentDocument()!=origin.document())return;
                    if(action.equals("separate"))VideoTimeline.separate();
                    if(action.equals("bind-audio"))VideoTimeline.bindAudio();
                    if(action.equals("collect"))VideoTimeline.retryCollection();
                    if(action.equals("lock-video-time"))VideoTimeline.lockSelected();
                });
                if(action.equals("marker-add")){
                    double seconds=cmd.has("seconds")?cmd.get("seconds").getAsDouble():origin.seconds();
                    if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("标记位置无效");
                    VideoMarkers.add(new BitwigBridge.Snapshot(origin.document(),origin.config(),origin.playback(),seconds,origin.playing(),origin.nanos()),cmd.get("name").getAsString(),!cmd.has("locked")||cmd.get("locked").getAsBoolean());
                }
                if(action.equals("marker-lock"))VideoMarkers.setLocked(origin.document(),cmd.get("id").getAsLong(),cmd.get("locked").getAsBoolean());
                if(action.equals("marker-rename"))VideoMarkers.edit(origin.document(),cmd.get("id").getAsLong(),cmd.get("name").getAsString(),false);
                if(action.equals("marker-go"))VideoMarkers.edit(origin.document(),cmd.get("id").getAsLong(),null,true);
                if(action.equals("apply"))BitwigBridge.commit(origin,new VideoConfig(1,origin.config().file(),cmd.get("offset").getAsDouble(),cmd.get("enabled").getAsBoolean()));
            }
        }catch(Exception e){if(!socket.isClosed())error(e.getMessage());}
        finally{sender.cancel(true);try{socket.close();}catch(IOException ignored){}}
    }
    private void sendState(Socket socket){
        try{
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            while(!closed&&!socket.isClosed()){
                BitwigBridge.Snapshot s=BitwigBridge.snapshot();
                if(s.document()!=document){document=s.document();documentId++;}
                JsonObject state=new JsonObject();state.addProperty("document",s.document()==null?0:documentId);
                state.add("config",JSON.toJsonTree(s.config()));state.addProperty("seconds",s.seconds());state.addProperty("playing",s.playing());
                state.add("markers",JSON.toJsonTree(VideoMarkers.state()));
                state.add("media",JSON.toJsonTree(VideoTimeline.files()));
                state.addProperty("timeMode",VideoTimeline.timeMode());
                state.addProperty("previewPosition",PreviewPlacement.position().key());state.addProperty("previewShown",PreviewPlacement.shown());
                state.addProperty("status",VideoTimeline.status().isBlank()?BitwigBridge.playerStatus():VideoTimeline.status());state.addProperty("time",BitwigBridge.playerTime());
                state.addProperty("show",showSerial);state.addProperty("errorSerial",errorSerial);state.addProperty("error",error);
                out.write(JSON.toJson(state));out.newLine();out.flush();Thread.sleep(150);
            }
        }catch(Exception ignored){}
    }
    public void close(){closed=true;try{if(server!=null)server.close();if(client!=null)client.close();}catch(IOException ignored){}if(child!=null)child.destroy();tasks.shutdownNow();}
}
