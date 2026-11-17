import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses the actual Launcher sources; does not start a host or a UI. */
public final class MacPackageTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Map<String,Object> release(String name,String hash){
        return Map.of("draft",false,"prerelease",true,"published_at","2026-11-17T00:00:00Z",
            "html_url","https://github.com/Amo-Nova/native-video-track/releases/tag/v0.5.0-dev.14-macos",
            "assets",List.of(Map.of("name",name,"browser_download_url","https://example.invalid/"+name,"digest","sha256:"+hash)));
    }
    public static void main(String[] args)throws Exception{
        Path archive=Path.of(args[0]).toAbsolutePath(),root=Path.of(args[1]).toAbsolutePath();
        require(!Files.exists(root),"Fixture root already exists");Files.createDirectories(root);
        System.setProperty("launcher.data",root.toString());System.setProperty("java.awt.headless","true");
        String mac=archive.getFileName().toString(),source="NativeVideoTrack-Mac-Source-0.5.0-dev.14-macos.zip";
        require(!Mods.ASSET.matcher(mac).matches()&&!Mods.ASSET.matcher(source).matches(),"Mac/source archive enters automatic catalog");
        String feed=args.length>2?Files.readString(Path.of(args[2]),StandardCharsets.UTF_8):Mods.Json.write(List.of(
            release("video-preview-0.5.0-dev.3.zip","90380554113fd100c845830549e04e17b69a8dbd5d2d29b183a8b5d898871125"),
            release(mac,Mods.sha256(archive)),release(source,"0".repeat(64))));
        var catalog=Mods.parseCatalog(feed,"Amo-Nova/native-video-track");
        require(catalog.size()==1&&catalog.get("video-preview").version().equals("0.5.0-dev.3"),"Windows catalog entry replaced or fake source MOD added");
        Path mods=root.resolve("mods");var checked=Mods.check(archive,mods);var info=Mods.infoOf(checked.manifest());
        require(checked.id().equals("video-preview")&&checked.version().equals("0.5.0-dev.14"),"Mac package identity mismatch");
        require(info.builtFor().equals("6.1.7"),"Wrong host version");
        var target=Mods.Json.object(checked.manifest().get("built_against"));
        require("macos".equals(target.get("platform"))&&"universal".equals(target.get("architecture")),"Mac target declaration changed");
        Path settings=root.resolve("config/mods/video-preview/settings.properties"),media=root.resolve("projects/fixture/Video/sentinel");
        Files.createDirectories(settings.getParent());Files.writeString(settings,"preview.visible=false\n");
        Files.createDirectories(media.getParent());Files.writeString(media,"synthetic media");
        String settingsHash=Mods.sha256(settings),mediaHash=Mods.sha256(media);
        Mods.install(checked,mods);String helperHash=Mods.sha256(mods.resolve("video-preview/helper.jar"));
        require(helperHash.equals("0182747df9d71afb289395e3cd9b851d327796fabb6a826d0239c97621f260d6"),"Contributed helper changed during install");
        Mods.install(Mods.check(archive,mods),mods);
        require(helperHash.equals(Mods.sha256(mods.resolve("video-preview/helper.jar"))),"Reinstall changed helper");
        require(settingsHash.equals(Mods.sha256(settings))&&mediaHash.equals(Mods.sha256(media)),"Install modified external state");
        require(Files.readString(mods.resolve("video-preview/CREDITS.txt")).contains("https://github.com/AUPhui"),"Contributor credit absent from installed package");
        require(Files.readString(mods.resolve("video-preview/LICENSE")).contains("MIT License"),"MIT license absent");
        require(!Files.exists(mods.resolve("video-preview/player/mpv"))&&!Files.exists(mods.resolve("video-preview/player/mpv.exe")),"Player binary was bundled");
        System.out.println("MAC_LAUNCHER_PACKAGE_OK archive, manual install, reinstall, credits, external state, Windows catalog preserved");
    }
}
