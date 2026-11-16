package local.bitwig.video;

import java.util.*;

/** Shared hit rectangles and paint positions; narrow side panels keep every command accessible. */
final class VideoToolbar {
    record Item(int id,String label,int x,int y,int width,int height){}
    static List<Item> items(int width){
        List<Item> items=new ArrayList<>();int x=8,y=6;
        String[] labels={"位置："+PreviewPlacement.position().label+" ▾","选择 / 设置","插入标记","上一标记","下一标记","标记列表","−1帧","+1帧"};
        int[] widths={148,110,92,92,92,92,60,60};
        for(int i=0;i<labels.length;i++){
            int w=Math.min(widths[i],Math.max(48,width-16));
            if(x>8&&x+w>width-8){x=8;y+=30;}
            items.add(new Item(i,labels[i],x,y,w,26));x+=w+4;
        }
        return items;
    }
    static int height(int width){List<Item> items=items(width);return items.getLast().y()+32;}
    static void run(int id){switch(id){
        case 0 -> BitwigBridge.positionMenu();
        case 1,5 -> BitwigBridge.showControls();
        case 2 -> VideoMarkers.add();
        case 3 -> VideoMarkers.previous();
        case 4 -> VideoMarkers.next();
        case 6 -> VideoMarkers.step(-1);
        case 7 -> VideoMarkers.step(1);
    }}
}
