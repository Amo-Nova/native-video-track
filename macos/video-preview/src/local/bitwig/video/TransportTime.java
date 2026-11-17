package local.bitwig.video;

/** Stop holds the play cursor. A later blue-marker edit becomes the preview cursor. */
final class TransportTime {
    private Object document;
    private double start, position, stopped;
    private boolean wasPlaying;
    double update(Object doc,double playSeconds,double startSeconds,boolean playing) {
        if(doc!=document){document=doc;stopped=playSeconds;start=startSeconds;position=playSeconds;wasPlaying=false;}
        if(!playing){
            if(Math.abs(startSeconds-start)>0.000001)stopped=startSeconds;
            else if(wasPlaying||Math.abs(playSeconds-position)>0.000001)stopped=playSeconds;
        }
        start=startSeconds;position=playSeconds;wasPlaying=playing;
        return playing?playSeconds:stopped;
    }
}
