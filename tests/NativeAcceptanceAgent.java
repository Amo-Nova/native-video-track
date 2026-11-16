package local.bitwig.video.test;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;

/** Test-only application API driver; no OS input, no production deployment or network listener. */
public final class NativeAcceptanceAgent {
    static Class<?> bridge,timeline;static ClassLoader host;
    public static void main(String[] args)throws Exception{
        var vm=VirtualMachine.attach(args[0]);try{vm.loadAgentPath(args[1],args[2]);}finally{vm.detach();}
    }
    public static void nativeMain(String argument,Class<?> loadedBridge)throws Exception{
        Path config=Path.of(argument).toRealPath();
        if(!config.getParent().getFileName().toString().equals("tests")||!config.getParent().getParent().getFileName().toString().equals("Bitwig_Video_Track"))throw new SecurityException("Expected isolated test workspace");
        Properties p=new Properties();try(var in=Files.newBufferedReader(config)){p.load(in);}
        bridge=loadedBridge;
        if(bridge==null)throw new IllegalStateException("Video MOD is not loaded");
        host=bridge.getClassLoader();timeline=Class.forName("local.bitwig.video.VideoTimeline",true,host);
        Path output=config.getParent().resolve("results/native-"+p.getProperty("name","snapshot")+".txt");
        Thread worker=new Thread(()->{
            try{
                String action=p.getProperty("action","snapshot");
                if(action.equals("seek-profile")){writeResult(output,seekProfile(config,p));return;}
                if(action.startsWith("stress-")){writeResult(output,stress(config,p));return;}
                Object result=ui(()->{
                    Object doc=call(bridge,"currentDocument");
                    if(action.equals("bootstrap")) {
                        Class<?> installed=Class.forName("local.bitwig.video.install.InstalledRuntime",true,host);
                        return "Persistent integration "+installed.getField("VERSION").get(null)+"; host="+installed.getProtectionDomain().getCodeSource().getLocation()+"; activeDocument="+(doc!=null)+"; BML_MOD_CONFIG_present="+(System.getenv("BML_MOD_CONFIG")!=null)+"; JAVA_TOOL_OPTIONS_present="+(System.getenv("JAVA_TOOL_OPTIONS")!=null);
                    }
                    if(doc==null&&action.equals("new")) {
                        if(!((Collection<?>)call(call(app(),"VNw"),"getValue")).isEmpty())throw new SecurityException("Unexpected inactive project tabs");
                        Object activate=Class.forName("com.bitwig.flt.app.lfZ",true,host).getField("beI").get(null);
                        app().getClass().getMethod("beI",Set.class).invoke(app(),Set.of(activate));
                        return "New acceptance project requested from empty dashboard";
                    }
                    if(doc==null)throw new IllegalStateException("No active document");
                    Path folder=(Path)call(timeline,"root",doc);
                    Path allowed=config.getParent().resolve("native-owned-temp.txt");
                    boolean owned=folder.normalize().startsWith(config.getParent().toAbsolutePath().normalize())||folder.getFileName().toString().equals("29d93347-cb64-4446-87f3-bf92c2bffaa0")||(Files.exists(allowed)&&folder.toString().equals(Files.readString(allowed)));
                    if(action.equals("saveas")&&((String)call(doc,"iEE")).isEmpty()){
                        int count=0;for(Object track:(Iterable<?>)call(call(doc,"EVg"),"bcT")){try{count+=((List<?>)call(call(call(track,"LXi"),"byf"),"beI")).size();}catch(NoSuchMethodException ignored){}}
                        if(count==0){owned=true;Files.writeString(allowed,folder.toString());}
                    }
                    if(!action.equals("snapshot")&&!action.equals("drop-inspect")&&!action.equals("import")&&!action.equals("open")&&!owned)throw new SecurityException("Not an owned acceptance project");
                    if(!Set.of("snapshot","drop-inspect","open","save","saveas").contains(action)&&hasExternalVideo(doc,config.getParent()))
                        throw new SecurityException("Manual media detected in the acceptance project; automatic editing stopped");
                    if(action.equals("drop-inspect"))return inspectDrop(config,doc,false,p);
                    if(action.equals("drop"))return inspectDrop(config,doc,true,p);
                    if(action.equals("new")){
                        if((boolean)call(doc,"IDq"))throw new IllegalStateException("Save existing acceptance project first");
                        Object activate=Class.forName("com.bitwig.flt.app.lfZ",true,host).getField("beI").get(null);
                        app().getClass().getMethod("beI",Set.class).invoke(app(),Set.of(activate));
                        Object created=call(bridge,"currentDocument");
                        if(created==doc)return "New acceptance project requested; activation is asynchronous";
                        if(!((List<?>)call(timeline,"entries",created)).isEmpty())throw new IllegalStateException("Expected empty new project");
                        Files.writeString(allowed,call(timeline,"root",created).toString());return snapshot(created);
                    }else if(action.equals("marker-add")){
                        Class<?> markers=Class.forName("local.bitwig.video.VideoMarkers",true,host);
                        Object old=call(bridge,"snapshot");Class<?> snap=old.getClass();
                        Object origin=snap.getConstructors()[0].newInstance(doc,call(old,"config"),call(old,"playback"),Double.parseDouble(p.getProperty("beat")),false,System.nanoTime());
                        call(markers,"add",origin,p.getProperty("media"),!p.getProperty("offset").equals("0"));
                    }else if(action.equals("marker-lock")){
                        Class<?> markers=Class.forName("local.bitwig.video.VideoMarkers",true,host);call(markers,"refresh",doc);
                        Object selected=((List<?>)call(markers,"state")).get(Integer.parseInt(p.getProperty("index")));
                        call(markers,"setLocked",doc,call(selected,"id"),!p.getProperty("offset").equals("0"));
                    }else if(action.equals("tempo")||action.equals("tempomap")){
                        java.util.function.Consumer<Object> edit=api->{try{
                            Object tempo=call(call(api,"getTransport"),"getTempo");
                            if(action.equals("tempo"))call(tempo,"setValue",Double.parseDouble(p.getProperty("tempo")));
                            else{
                                Object unit=Class.forName("com.bitwig.extension.api.project.parameter.Unit",true,host).getField("BPM").get(null);
                                Object events=call(call(api,"getFactory"),"createDoubleAutomationEvents",unit);
                                for(String point:p.getProperty("points").split(",")){String[] pair=point.split(":");call(events,"addPoint",Double.parseDouble(pair[0]),Double.parseDouble(pair[1]));}
                                call(tempo,"setAutomation",events);
                            }
                        }catch(Exception e){throw new RuntimeException(e);}};call(timeline,"transaction",doc,"Acceptance "+action,edit);
                    }else if(action.equals("import")||action.equals("queue-imports")){
                        // Initially allowed only in an empty project; later imports only in this test directory.
                        List<?> videos=(List<?>)call(timeline,"entries",doc);
                        Object tracks=call(call(doc,"EVg"),"bcT");int count=0;
                        for(Object track:(Iterable<?>)tracks){try{Object clips=call(call(track,"LXi"),"byf");count+=((List<?>)call(clips,"beI")).size();}catch(NoSuchMethodException ignored){}}
                        if(count!=0&&!owned)throw new SecurityException("Nonempty user project: import refused");
                        if(count==0&&((String)call(doc,"iEE")).isEmpty())Files.writeString(allowed,folder.toString());
                        List<Path> sources=new ArrayList<>();for(String name:p.getProperty("media").split("\\|")){
                            Path source=config.getParent().resolve("media").resolve(name).normalize();
                            if(!source.startsWith(config.getParent().resolve("media")))throw new SecurityException("Test fixture only");sources.add(source);
                        }
                        double at=Double.parseDouble(p.getProperty("beat","0"));
                        if(action.equals("queue-imports"))for(Path source:sources){call(timeline,"importFiles",doc,List.of(source),at);at+=40;}
                        else call(timeline,"importFiles",doc,sources,at);
                    }else if(action.equals("legacy")){
                        Path source=config.getParent().resolve("media/sync-delayed-aac.mp4");double beat=16000;
                        Map<String,Object> oldMetadata=Map.of("file",source.toString(),"duration",12.0,"originBeat",beat,"originSeconds",call(call(doc,"XzC"),"BBg",beat));
                        Object gson=Class.forName("com.google.gson.Gson",true,host).getConstructor().newInstance();String encoded="patchbay-video-v1:"+call(gson,"toJson",oldMetadata);
                        java.util.function.Consumer<Object> edit=api->{try{
                            Object clip=call(timeline,"createClip",api,null,12.0,12.0);call(clip,"setTime",beat+2);call(clip,"setDuration",12.0);call(clip,"setPlayStartOffset",2.0);call(clip,"setIsLoopEnabled",false);call(clip,"setTitle","LEGACY · sync-delayed-aac.mp4");
                            call(call(clip,"getTarget"),"Gef",encoded);Object track=call(timeline,"findTrack",doc,api,"Video",false);call(call(track,"clipTimeline"),"addEvent",clip);
                        }catch(Exception e){throw new RuntimeException(e);}};call(timeline,"transaction",doc,"Acceptance legacy fixture",edit);
                    }else if(action.equals("save")||action.equals("saveas")){
                        String existing=(String)call(doc,"iEE");Path target=action.equals("saveas")?config.getParent().resolve(p.getProperty("media").endsWith(".bwproject")?p.getProperty("media"):"projects/stage3-saveas/Stage3 Collected.bwproject"):existing.isEmpty()?config.getParent().resolve("projects/stage3-native/Stage3 Native.bwproject"):Path.of(existing);target=target.toAbsolutePath().normalize();if(!target.startsWith(config.getParent()))throw new SecurityException("Test project only");Files.createDirectories(target.getParent());
                        call(app(),"beI",doc,target.toString());
                    }else if(action.equals("move")||action.equals("trim")){
                        Object entry=((List<?>)call(timeline,"entries",doc)).get(Integer.parseInt(p.getProperty("index","0")));
                        Object clip=call(timeline,"wrap",call(entry,"clip"));
                        java.util.function.Consumer<Object> edit=api->{try{
                            call(clip,"setTime",Double.parseDouble(p.getProperty("beat","4")));
                            if(action.equals("trim")){call(clip,"setPlayStartOffset",Double.parseDouble(p.getProperty("offset","2")));call(clip,"setDuration",Double.parseDouble(p.getProperty("duration","12")));}
                        }catch(Exception e){throw new RuntimeException(e);}};
                        call(timeline,"transaction",doc,"Acceptance "+action,edit);
                    }else if(action.equals("separate")||action.equals("bind")||action.equals("select")||action.equals("split")||action.equals("lock-time")||action.equals("copy")){
                        Object entry=((List<?>)call(timeline,"entries",doc)).get(Integer.parseInt(p.getProperty("index","0"))),nativeClip=call(entry,"clip");
                        Object editor=call(call(doc,"PqZ"),"DYQ"),selection=call(editor,"XUO");
                        Method setSelection=selection.getClass().getMethod("beI",Collection.class);setSelection.invoke(selection,List.of(nativeClip));
                        if(action.equals("separate"))call(call(Class.forName("drf",true,host),"EO"),"asyncExec",(Runnable)()->{try{call(timeline,"separate");}catch(Exception e){throw new RuntimeException(e);}});
                        if(action.equals("bind"))call(call(Class.forName("drf",true,host),"EO"),"asyncExec",(Runnable)()->{try{call(timeline,"bindAudio");}catch(Exception e){throw new RuntimeException(e);}});
                        if(action.equals("lock-time"))call(timeline,"lockSelected");
                        if(action.equals("copy")){
                            java.util.function.Consumer<Object> edit=api->{try{Object clone=call(nativeClip,"waK"),clip=call(timeline,"wrap",clone);call(clip,"setTime",Double.parseDouble(p.getProperty("beat")));Object track=Class.forName("com.bitwig.flt.document.api.document.ApiTrack",true,host).getConstructors()[0].newInstance(call(entry,"track"));call(call(track,"clipTimeline"),"addEvent",clip);}catch(Exception e){throw new RuntimeException(e);}};
                            call(timeline,"transaction",doc,"Acceptance copy",edit);
                        }
                        if(action.equals("split")){
                            java.util.function.Consumer<Object> edit=api->{try{call(call(call(nativeClip,"byf"),"byf"),"beI",nativeClip,Double.parseDouble(p.getProperty("beat")),editor,null);}catch(Exception e){throw new RuntimeException(e);}};
                            call(timeline,"transaction",doc,"Acceptance native split",edit);
                        }
                    }else if(action.equals("relink")){
                        Object entry=((List<?>)call(timeline,"entries",doc)).get(Integer.parseInt(p.getProperty("index","0")));
                        call(timeline,"relink",doc,call(call(entry,"media"),"file"),config.getParent().resolve("media/clip-b-24fps.mp4"));
                    }else if(action.equals("seek")||action.equals("play")||action.equals("stop")){
                        Object transport=call(doc,"jPe");
                        if(action.equals("stop"))call(transport,"JJn");else {call(transport,"sCA",Double.parseDouble(p.getProperty("beat")));if(action.equals("play"))call(transport,"Gtm");}
                    }else if(action.equals("quit")){
                        for(Object tab:(Collection<?>)call(call(app(),"VNw"),"getValue")){
                            Object opened=call(tab,"Anf");Path openedRoot=(Path)call(timeline,"root",opened);
                            if(hasExternalVideo(opened,config.getParent()))throw new SecurityException("Manual media in an open project; automatic quit refused");
                            boolean test=openedRoot.normalize().startsWith(config.getParent())||((String)call(opened,"iEE")).isEmpty();
                            if(!test||(boolean)call(opened,"IDq"))throw new SecurityException("Quit only saved acceptance projects / unchanged blank tabs");
                        }
                        if((boolean)call(doc,"IDq"))throw new IllegalStateException("Save still in progress");
                        Method quit=app().getClass().getMethod("beI",Class.forName("dwF",true,host));quit.invoke(app(),((List<?>)call(app(),"t1i")).getFirst());return "Normal application quit requested after saved acceptance project";
                    }else if(action.equals("open")){
                        Path target=config.getParent().resolve(p.getProperty("media")).normalize();
                        if(!target.startsWith(config.getParent())||!target.toString().endsWith(".bwproject"))throw new SecurityException("Test project only");
                        Method open=app().getClass().getMethod("beI",String.class,Class.forName("com.bitwig.base.async.Pv",true,host));open.invoke(app(),target.toString(),null);
                    }else if(action.equals("undo")){call(doc,"cov");
                    }else if(action.equals("redo")){call(doc,"vce");
                    }else if(action.equals("collect")){call(timeline,"retryCollection");
                    }else if(action.equals("engine")){doc.getClass().getMethod("sCA",Class.forName("com.bitwig.base.async.Pv",true,host)).invoke(doc,new Object[]{null});
                    }else if(action.equals("settings")){call(bridge,"showControls");
                    }else if(action.equals("panel")){call(bridge,"showPreview");
                    }else if(action.equals("placement")){call(Class.forName("local.bitwig.video.PreviewPlacement",true,host),"select",p.getProperty("media"));
                    }else if(action.equals("preview-hide")){call(bridge,"hidePreview");
                    }else if(action.equals("preview-reset")){call(Class.forName("local.bitwig.video.PreviewPlacement",true,host),"reset");
                    }else if(action.equals("preview-size")){call(Class.forName("local.bitwig.video.VideoLayout",true,host),"resize",Double.parseDouble(p.getProperty("duration")));
                    }else if(action.equals("float-size")||action.equals("float-close")||action.equals("float-command")||action.equals("float-recover")){
                        Object dock=field(bridge,"dock");
                        ((Queue<Runnable>)field(dock,"tasks")).add(()->{try{
                            Object floating=field(dock,"floating"),window=field(floating,"window"),user=Class.forName("com.sun.jna.platform.win32.User32",true,host).getField("INSTANCE").get(null);
                            if(action.equals("float-size")){
                                call(user,"SetWindowPos",window,null,Integer.parseInt(p.getProperty("beat")),Integer.parseInt(p.getProperty("offset")),Integer.parseInt(p.getProperty("duration")),Integer.parseInt(p.getProperty("tempo")),0x4|0x10|0x100);
                                call(floating,"remember");
                            }else if(action.equals("float-recover")){
                                call(Class.forName("local.bitwig.video.PreviewPlacement",true,host),"floating",-50000,-50000,800,500);call(floating,"restore");call(floating,"remember");
                            }else{
                                Object w=Class.forName("com.sun.jna.platform.win32.WinDef$WPARAM",true,host).getConstructor(long.class).newInstance(action.equals("float-command")?Long.parseLong(p.getProperty("index")):0L),l=Class.forName("com.sun.jna.platform.win32.WinDef$LPARAM",true,host).getConstructor(long.class).newInstance(0L);
                                call(user,"PostMessage",window,action.equals("float-command")?0x111:0x10,w,l);
                            }
                        }catch(Exception e){throw new RuntimeException(e);}});
                    }else if(!action.equals("snapshot"))throw new IllegalArgumentException("Unknown test action");
                    return snapshot(doc);
                });
                writeResult(output,result.toString());
            }catch(Throwable e){try{var s=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(s));writeResult(output,s.toString());}catch(Exception ignored){}}
        },"Video-native-acceptance-test");worker.setDaemon(true);worker.start();
    }
    static void writeResult(Path output,String value)throws Exception{Path pending=output.resolveSibling(output.getFileName()+".partial");Files.writeString(pending,value);Files.move(pending,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    static boolean hasExternalVideo(Object doc,Path tests)throws Exception{
        for(Object entry:(List<?>)call(timeline,"timedEntries",doc)){
            Path source=Path.of((String)call(call(entry,"media"),"file")).toAbsolutePath().normalize();
            if(!source.startsWith(tests.toAbsolutePath().normalize()))return true;
        }
        return false;
    }
    static String inspectDrop(Path config,Object doc,boolean commit,Properties p)throws Exception{
        Class<?> gridClass=Class.forName("com.bitwig.flt.widget.core.timeline.Jpp",true,host),hooks=Class.forName("local.bitwig.video.VideoHooks",true,host);
        List<Object> rows=new ArrayList<>();
        for(Object window:(Iterable<?>)call(app(),"t1i"))for(Object grid:(List<?>)callReturning(List.class,window,"Gef",gridClass,true)){
            if(call(grid,"lq_")!=doc)continue;
            Object display=call(call(grid,"kJ_"),"MgQ");
            Object data=Class.forName("epK",true,host).getConstructor(Class.forName("com.bitwig.windowing_system.Display",true,host)).newInstance(display);
            for(String name:p.getProperty("media").split("\\|")){
                Path source=config.getParent().resolve("media").resolve(name).normalize();
                if(!source.startsWith(config.getParent().resolve("media")))throw new SecurityException("Fixture only");
                Class.forName("epD",true,host).getMethod("beI",Object.class).invoke(data,source.toFile());
            }
            Deque<Object> pending=new ArrayDeque<>();pending.add(grid);Object chosen=null;
            while(!pending.isEmpty()&&rows.size()<60){
                Object widget=pending.removeFirst();pending.addAll((Collection<?>)call(widget,"jn_"));
                long width=(long)call(widget,"rtK"),height=(long)call(widget,"cBV");
                if(!(boolean)call(widget,"vTn")||width<8||height<8)continue;
                Object handler=call(hooks,"videoDrop",widget,width/2.0,height/2.0,data);
                rows.add(Map.of("widget",widget.getClass().getName(),"handler",handler==null?"null":handler.getClass().getName(),"width",width,"height",height));
                if(widget!=grid&&handler!=null&&handler.getClass().getName().endsWith("$VideoDrop"))chosen=handler;
            }
            if(commit){
                if(chosen==null)throw new AssertionError("Track/clip child did not yield video handler");
                // Use the real handler's commit path; duplicate callbacks must insert once.
                double x=(double)call(grid,"r_",Double.parseDouble(p.getProperty("beat")));
                call(chosen,"commit",x);call(chosen,"commit",x);
            }
            call(data,"Gef");
        }
        if(rows.isEmpty())throw new AssertionError("No visible arranger");
        Object gson=Class.forName("com.google.gson.Gson",true,host).getConstructor().newInstance();return (String)call(gson,"toJson",rows);
    }
    static String seekProfile(Path config,Properties p)throws Exception{
        Object engine=ui(()->{
            Object doc=call(bridge,"currentDocument");if(!((Path)call(timeline,"root",doc)).startsWith(config.getParent()))throw new SecurityException("Owned test project only");
            call(call(doc,"jPe"),"JJn");Object e=((List<?>)call(timeline,"entries",doc)).get(0);call(call(doc,"jPe"),"sCA",(double)call(call(e,"clip"),"ie")+2);return field(bridge,"engine");
        });
        Thread.sleep(700);
        ScheduledExecutorService executor=(ScheduledExecutorService)field(engine,"worker");
        return executor.submit(()->{
            Object connection=field(engine,"connection");if(connection==null)throw new IllegalStateException("Open Video panel first");
            String file=String.valueOf(field(engine,"loaded"));Map<String,Object> report=new LinkedHashMap<>();report.put("profile",p.getProperty("media"));
            List<Object> rows=new ArrayList<>();Map<String,Object> original=new LinkedHashMap<>();
            try{
                for(String option:List.of("options/vd-lavc-threads","options/hr-seek-framedrop","options/video-sync","options/demuxer-seekable-cache","hwdec-current","video-codec"))report.put(option,String.valueOf(call(connection,"get",option)));
                if(p.getProperty("media").startsWith("threads")){
                    String option="options/vd-lavc-threads";original.put(option,call(call(connection,"get",option),"getAsInt"));
                    command(connection,"set_property",option,Integer.parseInt(p.getProperty("media").substring(7)));call(connection,"load",Path.of(file));
                }
                command(connection,"set_property","pause",true);
                for(double target:new double[]{2.4,2.45,2.46,2.462,2.463,2.466,2.467,2.48,2.49,2.46,2.45,2.44,3.9,7.9,11.9,4.01,2.462}){
                    long start=System.nanoTime();while(!(boolean)call(connection,"seek",target))Thread.sleep(2);
                    while((boolean)call(connection,"seeking")){call(connection,"checkSeek");Thread.sleep(2);}
                    double actual=(double)call(call(connection,"get","time-pos"),"getAsDouble");rows.add(Map.of("target",target,"actual",actual,"ms",(System.nanoTime()-start)/1e6));
                }
                report.put("rows",rows);Object gson=Class.forName("com.google.gson.Gson",true,host).getConstructor().newInstance();return (String)call(gson,"toJson",report);
            }finally{for(var entry:original.entrySet())command(connection,"set_property",entry.getKey(),entry.getValue());call(engine,"show");}
        }).get(35,TimeUnit.SECONDS);
    }
    static Object command(Object connection,Object... values)throws Exception{return ((CompletableFuture<?>)call(connection,"request",(Object)values)).get(4,TimeUnit.SECONDS);}
    static String stress(Path config,Properties p)throws Exception{
        String mode=p.getProperty("action").substring(7);int index=Integer.parseInt(p.getProperty("index","0"));
        Object[] objects=(Object[])ui(()->{
            Object doc=call(bridge,"currentDocument");Path root=(Path)call(timeline,"root",doc);
            if(!root.normalize().startsWith(config.getParent()))throw new SecurityException("Stress only owned test projects");
            Object entry=((List<?>)call(timeline,"entries",doc)).get(index),nativeClip=call(entry,"clip"),clip=call(timeline,"wrap",nativeClip);
            Object engine=field(bridge,"engine"),dock=field(engine,"dock");
            if(!(boolean)call(dock,"visible"))call(bridge,"showPreview");
            call(call(doc,"jPe"),"JJn");return new Object[]{doc,entry,nativeClip,clip,engine};
        });
        Object doc=objects[0],nativeClip=objects[2],clip=objects[3],engine=objects[4];
        double[] geometry=(double[])ui(()->new double[]{(double)call(clip,"getTime"),(double)call(clip,"getDuration"),(double)call(clip,"getPlayStartOffset")});
        double start=geometry[0],length=geometry[1],offset=geometry[2];
        ui(()->{call(call(doc,"jPe"),"sCA",start+Math.min(2,length/4));return null;});
        long until=System.nanoTime()+8_000_000_000L;Object connection=null;boolean ready=false;
        while(System.nanoTime()<until){
            connection=field(engine,"connection");Object sample=call(bridge,"snapshot");
            String wanted=String.valueOf(call(call(sample,"playback"),"file"));
            if(connection!=null&&field(engine,"loadedDocument")==doc&&!wanted.isEmpty()
                &&wanted.equals(String.valueOf(field(engine,"loaded")))&&(boolean)call(field(engine,"dock"),"visible")
                &&!(boolean)call(connection,"seeking")){ready=true;break;}
            Thread.sleep(25);
        }
        if(!ready)throw new AssertionError("Wait for the current project's visible Video panel and decoder before stress");
        Thread.sleep(200);
        long seeksBefore=(long)call(connection,"completedSeeks");List<Double> latency=new ArrayList<>(),errors=new ArrayList<>();
        long reuseBefore=0,stepsBefore=0,retargetBefore=0;try{reuseBefore=(long)call(connection,"reusedFrames");stepsBefore=(long)call(connection,"steppedFrames");retargetBefore=(long)call(connection,"retargetedSeeks");}catch(NoSuchMethodException ignored){}
        Map<String,Object> report=new LinkedHashMap<>();report.put("mode",mode);report.put("title",ui(()->call(clip,"getTitle")));
        for(String property:List.of("hwdec-current","video-codec","video-params","container-fps","current-vo","decoder-frame-drop-count","frame-drop-count"))try{report.put("before-"+property,String.valueOf(call(connection,"get",property)));}catch(Exception ignored){}
        if(mode.startsWith("play")){
            if(mode.equals("play-drag"))for(int i=0;i<40;i++){
                final double beat=start+2+i*.004;ui(()->{call(call(doc,"jPe"),"sCA",beat);return null;});Thread.sleep(5);
            }
            ui(()->{if(!mode.equals("play-drag"))call(call(doc,"jPe"),"sCA",start+.1);call(call(doc,"jPe"),"Gtm");return null;});
            Thread.sleep(700);
            for(int i=0;i<35;i++){
                long before=System.nanoTime();Object s=call(bridge,"snapshot");double actual=(double)call(call(connection,"get","time-pos"),"getAsDouble");long after=System.nanoTime();
                double expected=(double)call(s,"seconds")-(double)call(call(s,"playback"),"offset")+((before+after)/2-(long)call(s,"nanos"))/1e9;
                if((boolean)call(s,"playing"))errors.add(actual-expected);Thread.sleep(100);
            }
            ui(()->{call(call(doc,"jPe"),"JJn");return null;});
        }else if(mode.equals("split")){
            for(int i=1;i<=12;i++){final double beat=start+length*i/14;long before=System.nanoTime();
                ui(()->{Object target=null;for(Object e:(List<?>)call(timeline,"entries",doc)){Object c=call(e,"clip");double b=(double)call(c,"ie"),d=(double)call(c,"ckt");if(b<beat-1e-6&&b+d>beat+1e-6){target=c;break;}}
                    if(target==null)throw new AssertionError("No clip at split");Object chosen=target,editor=call(call(doc,"PqZ"),"DYQ");
                    edit(doc,"Compatibility split",()->{call(call(call(chosen,"byf"),"byf"),"beI",chosen,beat,editor,null);return null;});call(call(doc,"jPe"),"sCA",beat+.01);return null;});
                latency.add((System.nanoTime()-before)/1e6);Thread.sleep(20);
            }
            report.put("splitState",ui(()->snapshot(doc)));
            for(int i=0;i<12;i++)ui(()->{call(doc,"cov");return null;});
        }else{
            for(int i=0;i<180;i++){double delta0=(i<90?i:179-i)*(mode.equals("sweep")?.025:.001);
                final double delta=i==179&&(mode.equals("seek-random")||mode.equals("sweep"))?Double.parseDouble(p.getProperty("offset")):delta0;long before=System.nanoTime();
                ui(()->{
                    if(mode.startsWith("seek")||mode.equals("sweep"))call(call(doc,"jPe"),"sCA",start+Math.min(2,length/4)+delta);
                    else edit(doc,"Compatibility "+mode,()->{call(clip,"setTime",start+delta);if(mode.equals("trim")){call(clip,"setPlayStartOffset",offset+delta);call(clip,"setDuration",length-delta);}return null;});
                    return null;
                });latency.add((System.nanoTime()-before)/1e6);if(i<179)Thread.sleep(10);
            }
        }
        long released=System.nanoTime();double actual=Double.NaN,expected=Double.NaN;
        double finalBeat=mode.equals("split")?start+length*12/14+.01:start+Math.min(2,length/4);
        if(mode.equals("seek-random")||mode.equals("sweep"))finalBeat+=Double.parseDouble(p.getProperty("offset"));
        final double requestedBeat=finalBeat;
        double requestedSeconds=mode.startsWith("play")?Double.NaN:(double)ui(()->call(call(doc,"XzC"),"BBg",requestedBeat));
        if(Double.isFinite(requestedSeconds))report.put("requestedProjectSeconds",requestedSeconds);
        double previousDecoded=Double.NaN;
        long requiredSample=(long)ui(()->{call(bridge,"sample");return call(call(bridge,"snapshot"),"nanos");});
        while(System.nanoTime()-released<4_000_000_000L){
            Thread.sleep(3);Object s=call(bridge,"snapshot");expected=(double)call(s,"seconds")-(double)call(call(s,"playback"),"offset");actual=(double)call(call(connection,"get","time-pos"),"getAsDouble");
            double issued=(double)field(engine,"lastDesired");
            boolean cursorArrived=Double.isNaN(requestedSeconds)||Math.abs((double)call(s,"seconds")-requestedSeconds)<.000001;
            Object prior=field(engine,"prior");boolean consumed=cursorArrived&&prior!=null&&(long)call(prior,"nanos")>=requiredSample;
            boolean decoderBusy=(boolean)call(call(connection,"get","seeking"),"getAsBoolean");
            if(consumed&&!decoderBusy&&!(boolean)call(connection,"seeking")&&actual==previousDecoded&&Math.abs(issued-expected)<.0002&&Math.abs(actual-expected)<.055)break;
            previousDecoded=decoderBusy?Double.NaN:actual;
        }
        report.put("finalExpected",expected);report.put("finalDecoded",actual);report.put("settleMs",(System.nanoTime()-released)/1e6);report.put("completedSeeks",(long)call(connection,"completedSeeks")-seeksBefore);
        report.put("uiLatencyMs",latency);report.put("playbackFrameErrorsSeconds",errors);
        final Object player=connection;final double endpoint=expected;
        double reference=((ScheduledExecutorService)field(engine,"worker")).submit(()->{
            while(!(boolean)call(player,"seek",endpoint))Thread.sleep(2);
            long deadline=System.nanoTime()+8_000_000_000L;
            while((boolean)call(player,"seeking")&&System.nanoTime()<deadline){call(player,"checkSeek");Thread.sleep(2);}
            double previous=Double.NaN;
            while(System.nanoTime()<deadline){
                boolean busy=(boolean)call(call(player,"get","seeking"),"getAsBoolean");
                double value=(double)call(call(player,"get","time-pos"),"getAsDouble");
                if(!busy&&value==previous)return value;previous=busy?Double.NaN:value;Thread.sleep(3);
            }throw new AssertionError("Reference frame did not settle");
        }).get(10,TimeUnit.SECONDS);
        report.put("rawExactReference",reference);try{report.put("reusedFrames",(long)call(connection,"reusedFrames")-reuseBefore);report.put("steppedFrames",(long)call(connection,"steppedFrames")-stepsBefore);report.put("retargetedSeeks",(long)call(connection,"retargetedSeeks")-retargetBefore);}catch(NoSuchMethodException ignored){}
        for(String property:List.of("hwdec-current","decoder-frame-drop-count","frame-drop-count"))try{report.put("after-"+property,String.valueOf(call(connection,"get",property)));}catch(Exception ignored){}
        report.put("state",ui(()->snapshot(doc)));
        boolean playOk=(!mode.startsWith("play")||errors.size()==35)&&errors.stream().allMatch(e->Math.abs(e)<.080);
        report.put("result",Math.abs(actual-expected)<.055&&Math.abs(actual-reference)<.000001&&((Number)report.get("settleMs")).doubleValue()<4000&&playOk?"PASS":"FAIL");
        Object gson=Class.forName("com.google.gson.Gson",true,host).getConstructor().newInstance();return (String)call(gson,"toJson",report);
    }
    static void edit(Object doc,String name,Checked work)throws Exception{
        java.util.function.Consumer<Object> task=api->{try{work.run();}catch(Exception e){throw new RuntimeException(e);}};call(timeline,"transaction",doc,name,task);
    }
    static Object app()throws Exception{return call(Class.forName("com.bitwig.flt.app.Hvv",true,host),"mEL");}
    static String snapshot(Object doc)throws Exception{
        Map<String,Object> state=new LinkedHashMap<>();state.put("root",String.valueOf(call(timeline,"root",doc)));state.put("status",call(timeline,"status"));
        state.put("projectFile",call(doc,"iEE"));state.put("undo",call(doc,"GrU"));state.put("modified",call(doc,"IDq"));
        Class<?> markers=Class.forName("local.bitwig.video.VideoMarkers",true,host);call(markers,"refresh",doc);state.put("markers",call(markers,"state"));
        try{Class<?> absolute=Class.forName("local.bitwig.video.AbsoluteTime",true,host);state.put("absoluteFailures",field(absolute,"failures"));state.put("absoluteFlushes",field(absolute,"flushes"));Object manager=Class.forName("dgR",true,host).getDeclaredMethod("beI",Class.forName("com.bitwig.ramona.core.jyE",true,host)).invoke(null,doc);state.put("undoRoot",manager==null?"none":call(manager,"BBg").getClass().getName());}catch(ClassNotFoundException ignored){}
        state.put("engineActive",call(doc,"unz"));state.put("playerStatus",call(bridge,"playerStatus"));state.put("playerTime",call(bridge,"playerTime"));state.put("playing",call(call(doc,"jPe"),"QDQ"));
        state.put("projectSeconds",call(call(bridge,"snapshot"),"seconds"));state.put("tempo",call(call(call(doc,"jPe"),"FHC"),"GwM"));
        Map<String,Object> map=new LinkedHashMap<>();for(double beat:new double[]{0,2,4,6,8,10,12,16,20,24,32})map.put(Double.toString(beat),call(call(doc,"XzC"),"BBg",beat));state.put("beatSeconds",map);
        Object engine=field(bridge,"engine");state.put("playerLoaded",field(engine,"loaded"));state.put("playerDuration",field(engine,"duration"));
        Object connection=field(engine,"connection");if(connection!=null)state.put("playerPosition",call(connection,"predictedPosition",System.nanoTime()));
        if(connection!=null)state.put("playerPid",((Process)field(connection,"process")).pid());
        try{
            Class<?> placement=Class.forName("local.bitwig.video.PreviewPlacement",true,host);
            state.put("previewPosition",call(call(placement,"position"),"key"));state.put("previewShown",call(placement,"shown"));
            state.put("previewLayout",call(Class.forName("local.bitwig.video.VideoLayout",true,host),"inspect"));
            Object dock=field(bridge,"dock");state.put("previewWindow",call(dock,"inspect"));
            Object floating=field(dock,"floating");if(floating!=null){
                Object window=field(floating,"window"),user=Class.forName("com.sun.jna.platform.win32.User32",true,host).getField("INSTANCE").get(null),rect=Class.forName("com.sun.jna.platform.win32.WinDef$RECT",true,host).getConstructor().newInstance();
                call(user,"GetWindowRect",window,rect);state.put("floatingVisible",call(user,"IsWindowVisible",window));
                state.put("floatingRect",List.of(field(rect,"left"),field(rect,"top"),field(rect,"right"),field(rect,"bottom")));
            }
        }catch(ClassNotFoundException ignored){}
        List<Object> selectionInfo=new ArrayList<>();for(Object c:(List<?>)call(call(call(call(doc,"PqZ"),"DYQ"),"XUO"),"MgQ"))selectionInfo.add(c.getClass().getName()+"@"+System.identityHashCode(c));state.put("selection",selectionInfo);
        List<Object> windows=new ArrayList<>();for(Object w:(Iterable<?>)call(app(),"t1i")){Object v=call(Class.forName("bml.panels.PanelActions",true,host),"projectView",w);windows.add(w.getClass().getName()+" view="+(v==null?"null":v.getClass().getName()));}state.put("windows",windows);
        List<Object> clips=new ArrayList<>(),timedClips=new ArrayList<>();
        for(Object entry:(List<?>)call(timeline,"timedEntries",doc)){
            Object nativeClip=call(entry,"clip"),clip=call(timeline,"wrap",nativeClip);
            Map<String,Object> item=new LinkedHashMap<>();item.put("identity",System.identityHashCode(nativeClip));item.put("type",nativeClip.getClass().getName());item.put("title",call(clip,"getTitle"));
            for(String property:List.of("getTime","getDuration","getPlayStartOffset","isLoopEnabled"))item.put(property,call(clip,property));
            item.put("media",call(entry,"media"));item.put("sourceStart",call(timeline,"sourceStart",doc,nativeClip,call(entry,"media")));List<Object> notes=new ArrayList<>();
            Object transport=call(doc,"XzC");double begin=(double)call(transport,"BBg",call(clip,"getTime"));
            item.put("beginSeconds",begin);item.put("endSeconds",call(transport,"BBg",(double)call(clip,"getTime")+(double)call(clip,"getDuration")));
            Object empty=Class.forName("local.bitwig.video.VideoConfig",true,host).getDeclaredField("EMPTY");
            Field ef=(Field)empty;ef.setAccessible(true);
            Object resolved=call(timeline,"resolve",doc,begin+0.1,ef.get(null));item.put("resolvedAtStart",resolved);
            for(Object note:(List<?>)call(call(call(clip,"getContent"),"getEventTimeline"),"getEvents")){
                Map<String,Object> n=new LinkedHashMap<>();n.put("type",note.getClass().getName());
                for(String property:List.of("getTime","getDuration","getAudioFile","getSampleRate","getChannelCount","getWarpMode","getAudioFileDurationInSeconds","getPlayStartOffset")){
                    try{n.put(property,String.valueOf(call(note,property)));}catch(NoSuchMethodException ignored){}
                }
                try{
                    Object nativeNote=call(note,"getTarget"),context=call(nativeClip,"beI",0),tempoMap=call(context,"lZ_");
                    double noteBeat=(double)call(note,"getTime"),anchor=(double)call(context,"sCA",noteBeat),local=(double)call(clip,"getPlayStartOffset")-noteBeat;
                    n.put("nativeAnchorBeat",anchor);n.put("rawSourceOffset",call(nativeNote,"odI"));n.put("nativeSourceAtClipStart",call(nativeNote,"beI",local,tempoMap,anchor));
                    double nextBeat=(double)call(transport,"MgQ",begin+1);
                    n.put("nativeSourceAfterOneSecond",call(nativeNote,"beI",local+nextBeat-(double)call(clip,"getTime"),tempoMap,anchor));
                    n.put("nativeSourceAtEventEnd",call(nativeNote,"beI",(double)call(note,"getDuration"),tempoMap,anchor));
                }catch(NoSuchMethodException ignored){}
                notes.add(n);
            }item.put("notes",notes);timedClips.add(item);if(!(boolean)call(call(entry,"media"),"audioOnly"))clips.add(item);
        }state.put("clips",clips);state.put("timedClips",timedClips);
        List<Object> nativeTracks=new ArrayList<>();for(Object track:(Iterable<?>)call(call(doc,"EVg"),"bcT")){
            try{Object list=call(call(track,"LXi"),"byf");List<Object> ct=new ArrayList<>();for(Object nativeClip:(List<?>)call(list,"beI")){
                Object c=call(timeline,"wrap",nativeClip);Map<String,Object> info=new LinkedHashMap<>();for(String prop:List.of("getTitle","getTime","getDuration","getPlayStartOffset"))info.put(prop,call(c,prop));ct.add(info);
            }nativeTracks.add(ct);}catch(NoSuchMethodException ignored){}
        }state.put("allNativeTracks",nativeTracks);
        Object gson=Class.forName("com.google.gson.Gson",true,host).getConstructor().newInstance();return (String)call(gson,"toJson",state);
    }
    interface Checked {Object run()throws Exception;}
    static Object field(Object object,String name)throws Exception{Class<?> c=object instanceof Class<?> type?type:object.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);}
    static Object ui(Checked action)throws Exception{
        CompletableFuture<Object> result=new CompletableFuture<>();
        Object dispatcher=call(Class.forName("drf",true,host),"EO");
        call(dispatcher,"asyncExec",(Runnable)()->{try{result.complete(action.run());}catch(Throwable e){result.completeExceptionally(e);}});
        return result.get(15,TimeUnit.SECONDS);
    }
    static Object call(Object object,String name,Object... args)throws Exception{
        return callReturning(null,object,name,args);
    }
    static Object callReturning(Class<?> resultType,Object object,String name,Object... args)throws Exception{
        Class<?> type=object instanceof Class<?> c?c:object.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;
            if(resultType!=null&&!resultType.isAssignableFrom(m.getReturnType()))continue;
            Class<?>[] params=m.getParameterTypes();boolean good=true;
            for(int i=0;i<args.length;i++){
                if(args[i]==null&&params[i].isPrimitive()){good=false;break;}
                Class<?> expected=params[i];if(expected==double.class)expected=Double.class;if(expected==boolean.class)expected=Boolean.class;if(expected==int.class)expected=Integer.class;if(expected==long.class)expected=Long.class;
                if(args[i]!=null&&!expected.isInstance(args[i])){good=false;break;}
            }
            if(good){m.setAccessible(true);try{return m.invoke(object instanceof Class<?>?null:object,args);}catch(InvocationTargetException e){throw e;}}
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
}
