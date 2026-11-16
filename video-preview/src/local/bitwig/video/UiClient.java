package local.bitwig.video;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class UiClient {
    record State(long document,VideoConfig config,double seconds,boolean playing,String status,String time,long show,long errorSerial,String error,java.util.List<VideoMarkerInfo> markers,java.util.List<String> media,String timeMode,String previewPosition,boolean previewShown){}
    private static final Gson JSON=new Gson();
    private volatile State state=new State(0,VideoConfig.EMPTY,0,false,"正在连接 Bitwig…","",0,0,"",java.util.List.of(),java.util.List.of(),"","bottom",true);
    private final Socket socket;
    private final BufferedWriter out;
    UiClient(int port,String token)throws Exception{
        socket=new Socket(InetAddress.getByName("127.0.0.1"),port);socket.setTcpNoDelay(true);
        out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));out.write(token);out.newLine();out.flush();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))){
                for(String line;(line=in.readLine())!=null;)state=JSON.fromJson(line,State.class);
            }catch(Exception ignored){}
            State old=state;state=new State(0,old.config(),old.seconds(),false,"已断开 Bitwig；请从 Bitwig 视频菜单重新打开","",old.show(),old.errorSerial(),old.error(),old.markers(),old.media(),old.timeMode(),old.previewPosition(),old.previewShown());
        },"Video-controls-reader");reader.setDaemon(true);reader.start();
    }
    State state(){return state;}
    synchronized void send(String action,Object...pairs){
        try{JsonObject cmd=new JsonObject();cmd.addProperty("action",action);for(int i=0;i<pairs.length;i+=2)cmd.add((String)pairs[i],JSON.toJsonTree(pairs[i+1]));out.write(JSON.toJson(cmd));out.newLine();out.flush();}
        catch(IOException e){throw new IllegalStateException("已断开 Bitwig",e);}
    }
}
