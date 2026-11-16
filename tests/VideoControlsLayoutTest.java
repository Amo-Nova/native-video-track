package local.bitwig.video;

import com.google.gson.Gson;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;

/** Measures only this MOD's own, unshown Swing components with synthetic data.
 * Does not capture windows, desktop pixels or accessibility data. */
public final class VideoControlsLayoutTest {
    static int buttons,labels;
    public static void main(String[] args)throws Exception{
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            UiClient client=new UiClient(server.getLocalPort(),"layout-fixture");
            try(Socket peer=server.accept()){
                var in=new BufferedReader(new InputStreamReader(peer.getInputStream(),StandardCharsets.UTF_8));
                if(!in.readLine().equals("layout-fixture"))throw new AssertionError("Wrong fixture");
                var out=new BufferedWriter(new OutputStreamWriter(peer.getOutputStream(),StandardCharsets.UTF_8));
                var fixture=new UiClient.State(1,VideoConfig.EMPTY,30,false,"素材已收集 · 2 个视频","视频 20.000 / 60.000 s",0,0,"",java.util.List.of(new VideoMarkerInfo(1,"画面事件",30,true),new VideoMarkerInfo(2,"音乐入点",31,false)),java.util.List.of("fixture-a.mp4","fixture-b.mp4"),"选中视频：绝对时间 · 原速","right",true);
                out.write(new Gson().toJson(fixture));out.newLine();out.flush();
                long until=System.nanoTime()+2_000_000_000L;while(client.state().document()!=1&&System.nanoTime()<until)Thread.sleep(10);
                if(client.state().document()!=1)throw new AssertionError("State did not arrive");
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                SwingUtilities.invokeAndWait(()->{
                    VideoControls controls=new VideoControls(client);
                    try{
                        var field=VideoControls.class.getDeclaredField("window");field.setAccessible(true);JFrame window=(JFrame)field.get(controls);
                        if(window.isVisible())throw new AssertionError("Test window must remain unshown");
                        window.pack();window.validate();inspect(window.getContentPane());
                        System.out.println("CONTROLS_LAYOUT_PASS "+window.getWidth()+"x"+window.getHeight()+" buttons="+buttons+" labels="+labels+" visible=false");
                        window.dispose();
                    }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                });
            }
        }
        System.exit(0);
    }
    static void inspect(Component value){
        if(!value.isVisible())return;
        if(value instanceof AbstractButton button){
            buttons++;Dimension preferred=button.getPreferredSize();
            if(button.getWidth()<preferred.width||button.getHeight()<preferred.height)throw new AssertionError("Clipped button: "+button.getText());
        }
        if(value instanceof JLabel label&&label.getText()!=null&&!label.getText().isBlank()){
            labels++;
            if(label.getWidth()<label.getPreferredSize().width)throw new AssertionError("Clipped label: "+label.getText());
        }
        if(value instanceof Container container)for(Component child:container.getComponents())inspect(child);
    }
}
