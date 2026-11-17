package local.bitwig.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.iface.bMt;
import com.bitwig.flt.document.core.iface.zCn;
import com.bitwig.flt.document.core.master.CmH;
import com.bitwig.ramona.core.dEy;
import com.bitwig.flt.document.api.document.*;
import com.bitwig.extension.api.Color;
import com.bitwig.extension.api.project.timeline.*;
import com.bitwig.extension.api.project.track.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Video metadata travels with the native clip through edits, undo and project serialization. */
final class VideoTimeline {
    private static final Gson JSON=new Gson();
    private static final String PREFIX="patchbay-video-v1:";
    private static final String AUDIO_PREFIX="patchbay-video-audio-time-v1:";
    private static final Color COLOR=Color.fromRGB(0.22,0.57,0.75);
    private static final Set<String> EXT=Set.of("mp4","mov","mkv","avi","webm","m4v","mxf","mpeg","mpg","wmv","ts");
    private static final Map<String,Media> CACHE=new LinkedHashMap<>();
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Video-media-worker");t.setDaemon(true);return t;});
    private static final Map<String,String> RESOLVED=new ConcurrentHashMap<>();
    private static final Map<String,Path> LAST_ROOT=new ConcurrentHashMap<>();
    private static final Map<String,MediaAssets.Asset> LEGACY=new ConcurrentHashMap<>();
    private static final Set<String> COLLECTED=ConcurrentHashMap.newKeySet();
    private static final Queue<ImportRequest> IMPORTS=new ConcurrentLinkedQueue<>();
    private static volatile String importStatus="",collectStatus="";
    private static volatile boolean timelineMode;
    private static volatile AtomicBoolean jobCancel;
    private static volatile boolean collectBusy;
    private static volatile Object watchedDocument;
    private static volatile String collectionSignature="";
    private static volatile long nextCollection;
    // Optional added fields preserve old carrier clips and their editing semantics.
    record Media(String file,double duration,double originBeat,double originSeconds,MediaAssets.Asset asset,boolean bound,boolean absolute,boolean audioOnly){
        Media(String file,double duration,double originBeat,double originSeconds,MediaAssets.Asset asset,boolean bound){this(file,duration,originBeat,originSeconds,asset,bound,false,false);}
    }
    record Entry(zCn track,TKq clip,Media media){}
    record CollectionReference(MediaAssets.Asset asset,String previousRoot){}
    record ImportRequest(bMt document,List<Path> files,double seconds){}
    static boolean accepts(Path p){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);int i=n.lastIndexOf('.');return i>=0&&EXT.contains(n.substring(i+1));}
    static boolean active(){return timelineMode;}
    static void retryCollection(){collectionSignature="";COLLECTED.clear();nextCollection=0;}
    private static Path root(bMt doc){return doc instanceof CmH master&&master.PJ3()!=null?master.PJ3().beI().toPath().toAbsolutePath().normalize():null;}
    private static MediaAssets assets(){return new MediaAssets(BitwigBridge.MOD.resource("player"),BitwigBridge.MOD.resource("logs"));}
    static List<Entry> entries(bMt doc){return timedEntries(doc).stream().filter(e->!e.media().audioOnly()).toList();}
    static List<Entry> timedEntries(bMt doc){
        List<Entry> result=new ArrayList<>();if(doc==null)return result;
        for(Object item:doc.EVg().bcT())if(item instanceof zCn track&&track.LXi() instanceof LAq clips)
            for(Object value:clips.byf().beI())if(value instanceof TKq clip){Media m=media(clip);if(m!=null)result.add(new Entry(track,clip,m));}
        return result;
    }
    private static void setMedia(TKq clip,Media media){clip.Gef(media==null?"":(media.audioOnly()?AUDIO_PREFIX:PREFIX)+JSON.toJson(media));}
    private static String key(Path root,MediaAssets.Asset asset){return root+"|"+asset.id();}
    private static String file(Path root,Media m){
        if(root==null)return m.file();
        // Resolving the saved project is asynchronous. Do not briefly load a stale absolute
        // source path before its collected copy has been checked, or one missing file can
        // turn the entire player off on project reopen. No disk I/O on the clock/UI thread.
        return RESOLVED.getOrDefault(root+"|"+mediaKey(m),"");
    }
    private static String mediaKey(Media m){return m.asset()==null?"legacy:"+m.file():m.asset().id();}
    private static void remember(Path root,MediaAssets.Asset asset)throws Exception{
        RESOLVED.put(key(root,asset),MediaAssets.under(root,asset.relative()).toString());LAST_ROOT.put(asset.id(),root);COLLECTED.add(key(root,asset));
    }
    private static void fail(Throwable e){importStatus=e instanceof MediaAssets.Cancelled?e.getMessage():"未完成："+e.getMessage();if(!(e instanceof MediaAssets.Cancelled))BitwigBridge.error(e);}
    private static AtomicBoolean begin(){
        if(jobCancel!=null){importStatus="已有媒体任务正在处理；可在设置中取消。";return null;}
        AtomicBoolean token=new AtomicBoolean();jobCancel=token;return token;
    }
    private static void endJob(){jobCancel=null;drf.EO().asyncExec(VideoTimeline::pumpImports);}
    static void transaction(CmH master,String name,Consumer<ApiProject> work){
        ApiProject api=new ApiProject(master);
        if(!master.beI(name,new dEy[0])){api.dispose();throw new IllegalStateException("工程正在编辑，请稍后重试。");}
        try{api.runAsOneModification(()->work.accept(api));}finally{try{master.xgz();}finally{api.dispose();}}
    }
    static void importFiles(bMt origin,List<Path> files,double beat){
        if(files.isEmpty()||!(origin instanceof CmH)||root(origin)==null)return;
        IMPORTS.add(new ImportRequest(origin,List.copyOf(files),origin.XzC().BBg(Math.max(0,beat))));
        pumpImports();
    }
    private static void pumpImports(){
        if(jobCancel!=null)return;
        ImportRequest request;
        while((request=IMPORTS.poll())!=null){
            if(BitwigBridge.currentDocument()!=request.document()){importStatus="工程已切换，排队导入已取消。";continue;}
            AtomicBoolean cancel=begin();
            importOne(request.document(),request.files(),0,request.seconds(),cancel);return;
        }
    }
    private static void importOne(bMt origin,List<Path> files,int index,double seconds,AtomicBoolean cancel){
        Path source=files.get(index).toAbsolutePath(),destination=root(origin);
        importStatus="准备导入 · "+source.getFileName();
        WORK.execute(()->{
            try{
                var asset=assets().prepare(destination,source,s->importStatus=s,cancel::get);remember(destination,asset);
                drf.EO().asyncExec(()->{
                    try{
                        MediaAssets.check(cancel::get);
                        if(BitwigBridge.currentDocument()!=origin)throw new IllegalStateException("工程已切换，导入已取消。");
                        if(!Objects.equals(destination,root(origin))){
                            importStatus="工程保存位置已改变，正在准备新目录中的素材…";
                            importOne(origin,files,index,seconds,cancel);return;
                        }
                        insert((CmH)origin,destination,asset,origin.XzC().MgQ(seconds));
                        importStatus="已导入 · "+source.getFileName()+(asset.audioRelative().isEmpty()?"（无音轨）":"（音视频绑定）");
                        if(PreviewPlacement.autoShow())BitwigBridge.showPreview();retryCollection();
                        if(index+1<files.size()){importOne(origin,files,index+1,seconds+asset.duration(),cancel);return;}
                    }catch(Throwable e){fail(e);}endJob();
                });
            }catch(Throwable e){fail(e);endJob();}
        });
    }
    private static double insert(CmH master,Path folder,MediaAssets.Asset asset,double beat)throws Exception{
        var t=master.XzC();double start=t.BBg(beat),length=Math.max(1.0/960,aoP.beI(t.MgQ(start+asset.duration()))-beat);
        boolean bound=!asset.audioRelative().isEmpty();Path audio=bound?MediaAssets.under(folder,asset.audioRelative()):null;
        transaction(master,"Import Video",api->{
            Track track=findTrack(master,api,bound?"Video + Audio":"Video",bound);
            Clip clip=createClip(api,audio,length,asset.duration());
            clip.setTime(beat);clip.setDuration(length);clip.setTitle((bound?"AV · ":"VIDEO · ")+Path.of(asset.original()).getFileName());
            clip.setColor(COLOR);clip.setIsLoopEnabled(false);clip.setPlayStartOffset(0);
            setMedia(nativeClip(clip),new Media(asset.original(),asset.duration(),beat,start,asset,bound,true,false));track.clipTimeline().addEvent(clip);
        });
        BitwigBridge.MOD.log("Video AV clip inserted: "+asset.original()+" beat="+beat+" length="+length+" audio="+bound+" root="+folder);
        return beat+length;
    }
    private static TKq nativeClip(Clip clip){return (TKq)((ApiObject)clip).getTarget();}
    static Clip wrap(TKq clip){return clip instanceof DyF audio?new ApiAudioNoteClipEvent(audio):new ApiInstrumentNoteClipEvent((DrA)clip);}
    private static Clip createClip(ApiProject api,Path audio,double length,double duration){
        if(audio==null)return api.getFactory().createNoteClip(false);
        var clip=api.getFactory().createAudioClip(false);
        var sample=api.getFactory().createSampleReferenceForLocalFile(audio.toFile());
        var note=api.getFactory().createAudioNote(0,length,sample);
        var warp=api.getFactory().createWarpEvents();warp.addWarpEvent(0,0);warp.addWarpEvent(length,duration);
        note.setWarpEvents(warp);note.setWarpMode(WarpMode.RAW);clip.getContent().getEventTimeline().addEvent(note);return clip;
    }
    private static Track findTrack(bMt doc,ApiProject api,String name,boolean audio){
        for(Object item:doc.EVg().bcT())if(item instanceof PiT nativeTrack){
            Track track=new ApiTrack(nativeTrack);if(name.equals(track.getTitle())&&(!audio||track.getTrackType()!=TrackType.INSTRUMENT))return track;
        }
        Track track=audio?api.getFactory().createAudioTrack():api.getFactory().createInstrumentTrack();
        track.setTitle(name);track.setColor(COLOR);api.getTrackGroup().addMainTrack(track);return track;
    }
    static Media media(TKq clip){
        String text=clip.FA2();if(text==null)return null;String prefix=text.startsWith(PREFIX)?PREFIX:text.startsWith(AUDIO_PREFIX)?AUDIO_PREFIX:null;if(prefix==null)return null;Media m=CACHE.get(text);if(m!=null)return m;
        try{m=JSON.fromJson(text.substring(prefix.length()),Media.class);
            if(m==null||m.file()==null||!Double.isFinite(m.duration())||m.duration()<=0)return null;
            if(CACHE.size()>2048)CACHE.clear();CACHE.put(text,m);return m;
        }catch(RuntimeException e){return null;}
    }
    static VideoConfig resolve(bMt doc,double seconds,VideoConfig legacy){
        timelineMode=false;if(doc==null)return legacy;
        Path folder=root(doc);List<Entry> timed=timedEntries(doc);List<Entry> all=timed.stream().filter(e->!e.media().audioOnly()).toList();observeCollection(doc,folder,timed);
        if(doc instanceof CmH master){
            Set<Object> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(master.PqZ().DYQ().XUO().MgQ());
        }
        var t=doc.XzC();VideoConfig result=null;
        for(Entry entry:all){TKq clip=entry.clip();Media m=entry.media();timelineMode=true;
            double begin=t.BBg(clip.ie()),end=t.BBg(clip.ie()+clip.ckt());
            if(result==null&&!clip.WYl()&&seconds>=begin-0.00001&&seconds<end-0.00001){
                double sourceStart=sourceStart(doc,clip,m);result=new VideoConfig(1,file(folder,m),begin-sourceStart,legacy.enabled());
            }
        }
        return result!=null?result:timelineMode?VideoConfig.EMPTY:legacy;
    }
    /** RAW audio uses the clip's CURRENT placement in the tempo map, including its play offset.
     * Import-time beats diverge after a trimmed clip moves across tempo automation. */
    static double sourceStart(bMt doc,TKq clip,Media media){
        if(media.bound()&&clip instanceof DyF audio){
            for(Object lane:audio.EDy().scr())if(lane instanceof B1 events)
                for(Object value:events.beI())if(value instanceof poA note&&note.jTs()==j2X.beI){
                    var context=clip.beI(0);
                    return note.beI(clip.dC_()-note.ie(),context.lY_(),context.sCA(note.ie()));
                }
        }
        var transport=doc.XzC();
        // Collected silent/separated carriers follow the same beat geometry as their RAW counterpart.
        // Preserve the historical time model of legacy metadata until explicitly upgraded.
        return media.absolute()||media.asset()!=null?transport.BBg(clip.ie())-transport.BBg(clip.ie()-clip.dC_())
            :transport.BBg(media.originBeat()+clip.dC_())-transport.BBg(media.originBeat());
    }
    private static void observeCollection(bMt doc,Path folder,List<Entry> entries){
        if(watchedDocument!=doc){watchedDocument=doc;collectionSignature="";collectStatus="";nextCollection=0;}
        long now=System.nanoTime();if(folder==null||collectBusy||now<nextCollection)return;nextCollection=now+1_000_000_000L;
        Map<String,Media> unique=new LinkedHashMap<>();for(Entry e:entries)unique.put(mediaKey(e.media()),e.media());
        if(unique.isEmpty())return;String signature=folder+"|"+String.join(",",unique.keySet());if(signature.equals(collectionSignature))return;collectionSignature=signature;
        List<Media> media=List.copyOf(unique.values());collectBusy=true;
        WORK.execute(()->{
            try{
                Consumer<String> progress=s->{if(watchedDocument==doc)collectStatus=s;};
                List<String> failures=new ArrayList<>();int completed=0;
                for(var m:media){
                  try{
                    var asset=m.asset();
                    Path manifest=folder.resolve("Video/."+(asset==null?"legacy-"+MediaAssets.identity(m.file()):"asset-"+asset.id())+".json");
                    CollectionReference reference=null;
                    if(Files.isRegularFile(manifest))reference=JSON.fromJson(Files.readString(manifest),CollectionReference.class);
                    if(asset==null){
                        asset=reference!=null?reference.asset():LEGACY.get(m.file());
                        if(asset==null)asset=assets().collectVideo(folder,Path.of(m.file()),m.duration(),progress);
                        LEGACY.put(m.file(),asset);
                    }
                    String key=key(folder,asset);if(COLLECTED.contains(key)){
                        String resolved=RESOLVED.get(key);if(resolved!=null)RESOLVED.put(folder+"|"+mediaKey(m),resolved);
                        completed++;continue;
                    }
                    Path previous=LAST_ROOT.get(asset.id()),target=MediaAssets.under(folder,asset.relative());
                    if(previous==null&&reference!=null&&reference.previousRoot()!=null)previous=Path.of(reference.previousRoot());
                    Files.createDirectories(manifest.getParent());
                    // Persist recovery provenance before a long copy; clip serialization stays unchanged.
                    Path pending=manifest.resolveSibling(manifest.getFileName()+".partial");
                    Files.writeString(pending,JSON.toJson(new CollectionReference(asset,previous==null?null:previous.toString())));
                    Files.move(pending,manifest,StandardCopyOption.REPLACE_EXISTING);
                    Path from=previous!=null&&Files.isRegularFile(MediaAssets.under(previous,asset.relative()))?MediaAssets.under(previous,asset.relative()):Path.of(asset.original());
                    MediaAssets.copy(from,target,asset.bytes(),progress,()->false,"收集视频");
                    // Publish each usable movie independently, even if its audio needs recovery
                    // or another movie in this project is missing.
                    RESOLVED.put(folder+"|"+mediaKey(m),target.toString());
                    if(!asset.audioRelative().isEmpty()){
                        Path audio=MediaAssets.under(folder,asset.audioRelative()),prior=previous==null?audio:MediaAssets.under(previous,asset.audioRelative());
                        if(!Files.isRegularFile(audio)&&!Files.isRegularFile(prior))assets().recoverAudio(folder,asset,progress);
                        else MediaAssets.copy(prior,audio,asset.audioBytes(),progress,()->false,"收集音频");
                    }
                    remember(folder,asset);
                    completed++;
                  }catch(Throwable e){
                    failures.add(Path.of(m.file()).getFileName()+"："+e.getMessage());
                  }
                }
                if(watchedDocument==doc){
                    collectStatus=failures.isEmpty()?"素材已收集 · "+media.size()+" 个视频"
                        :"已收集 "+completed+"/"+media.size()+"；待恢复："+String.join("；",failures);
                    if(!failures.isEmpty()){nextCollection=System.nanoTime()+5_000_000_000L;collectionSignature="";}
                }
                if(failures.isEmpty())BitwigBridge.MOD.log("Project media collected: "+folder+" count="+media.size());
            }catch(Throwable e){if(watchedDocument==doc)collectStatus="素材未收集完成："+e.getMessage()+"（设置中重试）";BitwigBridge.MOD.log("Media collection incomplete",e);}
            finally{collectBusy=false;}
        });
    }
}
