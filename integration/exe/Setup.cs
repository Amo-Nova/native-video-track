using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: AssemblyTitle("Native Video Track Setup")]
[assembly: AssemblyDescription("Local video integration installer for Bitwig Studio 6.1.7")]
[assembly: AssemblyCompany("Amo Nova")]
[assembly: AssemblyProduct("Native Video Track")]
[assembly: AssemblyVersion("1.0.0.3")]
[assembly: AssemblyFileVersion("1.0.0.3")]

namespace NativeVideoTrack {
    internal sealed class Options {
        public string Action="", Root="", DataRoot="", Report="";
        public bool Headless, Worker, SkipAssociation;
        public static Options Parse(string[] args) {
            Options o=new Options();
            for(int i=0;i<args.Length;i++) {
                switch(args[i]) {
                    case "--headless":o.Headless=true;break;
                    case "--worker":o.Worker=true;o.Headless=true;break;
                    case "--skip-association":o.SkipAssociation=true;break;
                    case "--action":o.Action=Next(args,ref i);break;
                    case "--root":o.Root=Next(args,ref i);break;
                    case "--data-root":o.DataRoot=Next(args,ref i);break;
                    case "--report":o.Report=Next(args,ref i);break;
                    default:throw new ArgumentException("Unknown option: "+args[i]);
                }
            }
            if(o.Root.Length==0)o.Root=DetectHost();
            if(o.DataRoot.Length==0)o.DataRoot=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"BitwigVideo");
            o.Root=Path.GetFullPath(o.Root).TrimEnd(Path.DirectorySeparatorChar);
            o.DataRoot=Path.GetFullPath(o.DataRoot);
            if(o.Headless && !new[]{"Install","Restore","Status","Verify"}.Contains(o.Action))throw new ArgumentException("Headless mode requires --action Install, Restore, Status or Verify.");
            if(o.Worker && o.Report.Length==0)throw new ArgumentException("Worker requires a result path.");
            return o;
        }
        private static string Next(string[] args,ref int i) {
            if(++i>=args.Length)throw new ArgumentException("Missing option value.");return args[i];
        }
        public static string DetectHost() {
            string folder=Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            string standard=Path.Combine(folder,"Bitwig Studio");
            if(File.Exists(Path.Combine(standard,"Bitwig Studio.exe")))return standard;
            try {
                foreach(string p in Directory.GetDirectories(folder,"Bitwig Studio*"))
                    if(File.Exists(Path.Combine(p,"Bitwig Studio.exe")) && File.Exists(Path.Combine(p,"bin","bitwig.jar")))return p;
            }catch(IOException){}catch(UnauthorizedAccessException){}
            return standard;
        }
    }
    public sealed class Receipt {
        public string installer=BuildInfo.Version, action, root, timestamp=DateTime.UtcNow.ToString("o"), output="", error="", warning="", log="";
        public int exitCode;
        public bool ok;
        public Dictionary<string,object> status;
    }
    internal sealed class ProcessResult { public int Exit; public string Output, Error; }
    internal sealed class Payload : IDisposable {
        private readonly string parent, directory;
        public string Root {get{return directory;}}
        public Payload() {
            parent=Path.GetFullPath(Path.Combine(Path.GetTempPath(),"NativeVideoTrack"));
            directory=Path.GetFullPath(Path.Combine(parent,Guid.NewGuid().ToString("N")));
            AssertNoReparse(parent);
            Directory.CreateDirectory(directory);
            try {
                using(Stream stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("NativeVideoTrack.Payload.zip")) {
                    if(stream==null)throw new InvalidDataException("Installer payload is missing.");
                    using(SHA256 hash=SHA256.Create())
                        if(Hex(hash.ComputeHash(stream))!=BuildInfo.PayloadSha256)throw new InvalidDataException("Installer payload checksum failed. Copy the original EXE again.");
                    stream.Position=0;
                    using(ZipArchive zip=new ZipArchive(stream,ZipArchiveMode.Read,true)) {
                        HashSet<string> seen=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                        foreach(ZipArchiveEntry entry in zip.Entries) {
                            string name=entry.FullName;
                            if(String.IsNullOrEmpty(name)||name.Contains(":")||name.Contains("\\")||name.Split('/').Contains("..")||!seen.Add(name))throw new InvalidDataException("Invalid payload entry.");
                            string target=Path.GetFullPath(Path.Combine(directory,name.Replace('/',Path.DirectorySeparatorChar)));
                            if(!target.StartsWith(directory+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new InvalidDataException("Payload path escapes its folder.");
                            Directory.CreateDirectory(Path.GetDirectoryName(target));
                            using(Stream input=entry.Open())using(FileStream output=new FileStream(target,FileMode.CreateNew,FileAccess.Write,FileShare.None))input.CopyTo(output);
                        }
                    }
                }
            } catch { Dispose();throw; }
        }
        private static string Hex(byte[] bytes){return BitConverter.ToString(bytes).Replace("-","").ToLowerInvariant();}
        private static void AssertNoReparse(string path) {
            for(string next=path;!String.IsNullOrEmpty(next);next=Path.GetDirectoryName(next))
                if((Directory.Exists(next)||File.Exists(next)) && (File.GetAttributes(next)&FileAttributes.ReparsePoint)!=0)throw new IOException("Redirected temporary folder is not supported.");
        }
        public void Dispose() {
            try {
                string target=Path.GetFullPath(directory);
                if(!target.StartsWith(parent+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)||Path.GetFileName(target).Length!=32)return;
                AssertNoReparse(target);
                if(Directory.Exists(target))RemoveOwnedTree(target);
            }catch(IOException){}catch(UnauthorizedAccessException){}
        }
        private static void RemoveOwnedTree(string root) {
            foreach(string path in Directory.GetFileSystemEntries(root)) {
                FileAttributes a=File.GetAttributes(path);
                if((a&FileAttributes.ReparsePoint)!=0)throw new IOException("Unexpected link in temporary payload.");
                if((a&FileAttributes.Directory)!=0)RemoveOwnedTree(path);else File.Delete(path);
            }
            Directory.Delete(root,false);
        }
    }
    internal static class Installer {
        internal static readonly JavaScriptSerializer Json=new JavaScriptSerializer(){MaxJsonLength=4*1024*1024};
        public static bool IsAdmin() {
            using(WindowsIdentity identity=WindowsIdentity.GetCurrent())return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
        }
        private static bool CanModifyHost(string root) {
            string bin=Path.Combine(root,"bin"), jar=Path.Combine(bin,"bitwig.jar");
            if(!File.Exists(jar))return true; // Let the backend report a missing host without elevation.
            try {
                for(string next=bin;!String.IsNullOrEmpty(next);next=Path.GetDirectoryName(next))
                    if((File.GetAttributes(next)&FileAttributes.ReparsePoint)!=0)return true; // Backend rejects redirected paths.
                if((File.GetAttributes(jar)&FileAttributes.ReparsePoint)!=0)return true;
                // Check access without altering jar bytes; probe only the directory being installed into.
                using(FileStream file=new FileStream(jar,FileMode.Open,FileAccess.ReadWrite,FileShare.ReadWrite)){}
                string probe=Path.Combine(bin,".native-video-track-access-"+Guid.NewGuid().ToString("N")+".tmp");
                using(FileStream file=new FileStream(probe,FileMode.CreateNew,FileAccess.Write,FileShare.None,1,FileOptions.DeleteOnClose)){}
                return true;
            }catch(UnauthorizedAccessException){return false;}
            catch(IOException){return false;}
        }
        // Quote a single Windows argv value; no shell or PowerShell expression interpolation.
        public static string Quote(string value) {
            StringBuilder b=new StringBuilder("\"");int slashes=0;
            foreach(char c in value) {
                if(c=='\\'){slashes++;continue;}
                if(c=='"'){b.Append('\\',slashes*2+1);b.Append(c);slashes=0;continue;}
                b.Append('\\',slashes);slashes=0;b.Append(c);
            }
            b.Append('\\',slashes*2);return b.Append('"').ToString();
        }
        private static ProcessResult PowerShell(string script,params string[] arguments) {
            List<string> args=new List<string>{"-NoLogo","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",script};args.AddRange(arguments);
            ProcessStartInfo start=new ProcessStartInfo {
                FileName=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),@"WindowsPowerShell\v1.0\powershell.exe"),
                Arguments=String.Join(" ",args.Select(Quote)),UseShellExecute=false,CreateNoWindow=true,
                RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=Path.GetDirectoryName(script)
            };
            using(Process process=new Process{StartInfo=start}) {
                process.Start();Task<string> output=process.StandardOutput.ReadToEndAsync(), error=process.StandardError.ReadToEndAsync();
                process.WaitForExit();Task.WaitAll(output,error);
                return new ProcessResult{Exit=process.ExitCode,Output=output.Result,Error=error.Result};
            }
        }
        public static Receipt Backend(Options o) {
            Receipt r=new Receipt{action=o.Action,root=o.Root};
            try {
                using(Payload payload=new Payload()) {
                    if(o.Action=="Verify") {r.ok=true;r.output="Embedded package SHA-256 verified: "+BuildInfo.PayloadSha256;return r;}
                    ProcessResult p=PowerShell(Path.Combine(payload.Root,"Install-BitwigVideo.ps1"),"-Action",o.Action,"-BitwigRoot",o.Root,"-DataRoot",o.DataRoot,"-SkipProjectAssociation");
                    r.exitCode=p.Exit;r.ok=p.Exit==0;r.output=p.Output.Trim();r.error=p.Error.Trim();
                    if(r.ok && o.Action=="Status")r.status=Json.Deserialize<Dictionary<string,object>>(p.Output);
                }
            }catch(Exception e){r.ok=false;r.exitCode=1;r.error=e.Message;}
            return r;
        }
        public static Receipt Execute(Options o) {
            Receipt r;
            bool mutate=o.Action=="Install"||o.Action=="Restore";
            if(mutate && !IsAdmin() && !CanModifyHost(o.Root)) {
                string workerReport=LogPath(o.DataRoot,"worker");
                try {
                    string[] args={"--worker","--action",o.Action,"--root",o.Root,"--data-root",o.DataRoot,"--report",workerReport};
                    ProcessStartInfo start=new ProcessStartInfo {
                        FileName=Assembly.GetExecutingAssembly().Location,Arguments=String.Join(" ",args.Select(Quote)),
                        UseShellExecute=true,Verb="runas",WindowStyle=ProcessWindowStyle.Hidden
                    };
                    using(Process p=Process.Start(start)){p.WaitForExit();}
                    if(!File.Exists(workerReport))throw new IOException("The administrator operation did not return a result. Check installation status before retrying.");
                    r=Json.Deserialize<Receipt>(File.ReadAllText(workerReport,Encoding.UTF8));
                    if(r.action!=o.Action||!String.Equals(r.root,o.Root,StringComparison.OrdinalIgnoreCase))throw new IOException("Unexpected worker result.");
                }catch(Win32Exception e){r=new Receipt{action=o.Action,root=o.Root,exitCode=e.NativeErrorCode,ok=false,error=e.NativeErrorCode==1223?"Administrator access was cancelled. No installation was started.":e.Message};}
                catch(Exception e){r=new Receipt{action=o.Action,root=o.Root,exitCode=1,ok=false,error=e.Message};}
            }else r=Backend(o);
            if(r.ok && mutate && !o.SkipAssociation) {
                try {
                    using(Payload payload=new Payload()) {
                        ProcessResult p=PowerShell(Path.Combine(payload.Root,"Complete-UserAssociation.ps1"),"-Action",o.Action,"-BitwigRoot",o.Root,"-DataRoot",o.DataRoot);
                        if(p.Exit!=0)r.warning="The component operation succeeded; the current user's project-file association could not be updated. "+p.Error.Trim();
                        else r.output+="\n"+p.Output.Trim();
                    }
                }catch(Exception e){r.warning="The component operation succeeded; project-file association: "+e.Message;}
            }
            return r;
        }
        public static string LogPath(string dataRoot,string prefix) {
            string folder=Path.Combine(dataRoot,"installer-logs");Directory.CreateDirectory(folder);
            return Path.Combine(folder,prefix+"-"+DateTime.Now.ToString("yyyyMMdd-HHmmss")+"-"+Guid.NewGuid().ToString("N")+".json");
        }
        public static void Save(Receipt result,string path) {
            path=Path.GetFullPath(path);Directory.CreateDirectory(Path.GetDirectoryName(path));result.log=path;
            string temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";
            File.WriteAllText(temp,Json.Serialize(result),new UTF8Encoding(false));
            if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path);
        }
        public static string ReadCredits() {
            using(Stream s=Assembly.GetExecutingAssembly().GetManifestResourceStream("NativeVideoTrack.Credits.txt"))
            using(StreamReader reader=new StreamReader(s,Encoding.UTF8))return reader.ReadToEnd();
        }
    }
    internal sealed class SetupForm : Form {
        private readonly TextBox location=new TextBox(), details=new TextBox();
        private readonly Label status=new Label();
        private readonly Button install=new Button(), restore=new Button(), check=new Button(), browse=new Button();
        private readonly ProgressBar progress=new ProgressBar();
        private readonly Options defaults;
        private bool busy;
        private string lastLog="";
        private readonly Color ink=Color.FromArgb(35,38,43), muted=Color.FromArgb(94,101,111), accent=Color.FromArgb(177,73,18);
        public SetupForm(Options options) {
            defaults=options;Text="Native Video Track — 安装与恢复";StartPosition=FormStartPosition.CenterScreen;
            AutoScaleMode=AutoScaleMode.Dpi;Font=new Font("Microsoft YaHei UI",9.5f);ForeColor=ink;BackColor=Color.FromArgb(248,248,246);
            ClientSize=new Size(700,574);MinimumSize=new Size(716,613);
            TableLayoutPanel layout=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(28,22,28,20),ColumnCount=1,RowCount=10};
            int[] heights={42,30,34,40,48,48,22,34,0,38};
            foreach(int h in heights)layout.RowStyles.Add(h==0?new RowStyle(SizeType.Percent,100):new RowStyle(SizeType.Absolute,h));
            Label title=new Label{Text="Native Video Track",Font=new Font("Segoe UI",24,FontStyle.Bold),AutoSize=true};
            Label subtitle=new Label{Text="Bitwig Studio 视频组件  /  Windows x64 · 6.1.7",AutoSize=true,ForeColor=muted};
            Label pathLabel=new Label{Text="Bitwig 安装目录",AutoSize=true,Anchor=AnchorStyles.Bottom|AnchorStyles.Left};
            TableLayoutPanel pathRow=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=2};pathRow.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));pathRow.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,84));
            location.Text=options.Root;location.Dock=DockStyle.Fill;location.Margin=new Padding(0,4,10,4);
            browse.Text="浏览…";browse.Dock=DockStyle.Fill;browse.Margin=new Padding(0,2,0,4);browse.Click+=delegate{using(FolderBrowserDialog d=new FolderBrowserDialog{Description="选择包含 Bitwig Studio.exe 的安装目录",SelectedPath=location.Text})if(d.ShowDialog(this)==DialogResult.OK){location.Text=d.SelectedPath;status.Text="目录已改变，请检查状态。";}};
            pathRow.Controls.Add(location,0,0);pathRow.Controls.Add(browse,1,0);
            Label hint=new Label{Text="安装前请保存工程并正常关闭 Bitwig。安装完成后，继续使用原来的 Bitwig 图标。",Dock=DockStyle.Fill,Padding=new Padding(0,8,0,0),ForeColor=muted};
            FlowLayoutPanel actions=new FlowLayoutPanel{Dock=DockStyle.Fill,WrapContents=false,Margin=new Padding(0)};
            SetButton(install,"安装 / 修复",164);install.BackColor=accent;install.ForeColor=Color.White;install.FlatStyle=FlatStyle.Flat;install.FlatAppearance.BorderSize=0;
            SetButton(check,"检查状态",128);SetButton(restore,"恢复原版",128);
            install.Click+=async delegate{await Run("Install");};check.Click+=async delegate{await Run("Status");};
            restore.Click+=async delegate{
                if(MessageBox.Show(this,"将恢复安装前的 Bitwig 程序，移除本组件。\n工程、视频和布局设置会保留。","恢复原版",MessageBoxButtons.OKCancel,MessageBoxIcon.Information)==DialogResult.OK)await Run("Restore");
            };
            actions.Controls.AddRange(new Control[]{install,check,restore});
            progress.Dock=DockStyle.Fill;progress.Margin=new Padding(0,8,0,8);progress.Style=ProgressBarStyle.Marquee;progress.MarqueeAnimationSpeed=0;progress.Visible=false;
            status.Text="准备检查安装状态…";status.Dock=DockStyle.Fill;status.Font=new Font(Font,FontStyle.Bold);status.Padding=new Padding(0,5,0,0);
            details.Multiline=true;details.ReadOnly=true;details.ScrollBars=ScrollBars.Vertical;details.Dock=DockStyle.Fill;details.BackColor=Color.White;details.Font=new Font("Microsoft YaHei UI",9);details.BorderStyle=BorderStyle.FixedSingle;
            FlowLayoutPanel footer=new FlowLayoutPanel{Dock=DockStyle.Fill,WrapContents=false,Padding=new Padding(0,8,0,0)};
            LinkLabel credits=new LinkLabel{Text="技术来源与致谢",AutoSize=true,Margin=new Padding(0,0,20,0),LinkColor=muted};
            credits.LinkClicked+=delegate{ShowCredits();};
            LinkLabel log=new LinkLabel{Text="查看本次日志",AutoSize=true,LinkColor=muted,Margin=new Padding(0,0,20,0)};
            log.LinkClicked+=delegate{if(lastLog.Length>0&&File.Exists(lastLog))Process.Start(new ProcessStartInfo("notepad.exe",Installer.Quote(lastLog)){UseShellExecute=true});};
            Label version=new Label{Text="安装器 "+BuildInfo.Version+" · 非官方组件",AutoSize=true,ForeColor=muted};
            footer.Controls.AddRange(new Control[]{credits,log,version});
            Control[] rows={title,subtitle,pathLabel,pathRow,hint,actions,progress,status,details,footer};for(int i=0;i<rows.Length;i++)layout.Controls.Add(rows[i],0,i);Controls.Add(layout);
            Shown+=async delegate{await Run("Status");};
            FormClosing+=delegate(object sender,FormClosingEventArgs e){if(busy){e.Cancel=true;status.Text="操作仍在进行，请等待结果后关闭。";}};
        }
        private static void SetButton(Button button,string label,int width){button.Text=label;button.Size=new Size(width,38);button.Margin=new Padding(0,4,12,4);}
        private async Task Run(string action) {
            if(busy)return;
            string root;
            try{root=Path.GetFullPath(location.Text.Trim()).TrimEnd(Path.DirectorySeparatorChar);}catch(Exception e){status.Text="安装目录无效";details.Text=e.Message;return;}
            busy=true;foreach(Control c in new Control[]{install,restore,check,browse,location})c.Enabled=false;
            progress.Visible=true;progress.MarqueeAnimationSpeed=25;status.ForeColor=ink;
            status.Text=action=="Status"?"正在检查版本和组件…":action=="Install"?"正在安装 / 修复，请稍候…":"正在恢复原版，请稍候…";
            details.Text="";
            try {
                Options o=new Options{Action=action,Root=root,DataRoot=defaults.DataRoot,SkipAssociation=defaults.SkipAssociation};
                Receipt r=await Task.Run(()=>Installer.Execute(o));
                lastLog=Installer.LogPath(o.DataRoot,action.ToLowerInvariant());Installer.Save(r,lastLog);
                ShowResult(r);
            }catch(Exception e){status.Text="操作未完成";status.ForeColor=Color.Firebrick;details.Text=e.Message;}
            finally{busy=false;foreach(Control c in new Control[]{install,restore,check,browse,location})c.Enabled=true;progress.Visible=false;progress.MarqueeAnimationSpeed=0;}
        }
        private void ShowResult(Receipt r) {
            if(!r.ok) {
                status.Text="操作未完成 · 可查看原因后重试";status.ForeColor=Color.Firebrick;
                string explanation="";
                if(r.error.Contains("close Bitwig normally"))explanation="请先保存工程并正常关闭 Bitwig，再点击重试。\r\n\r\n";
                else if(r.error.Contains("updated or changed"))explanation="这个 Bitwig 版本或安装文件与当前组件不匹配，请使用对应版本的组件。\r\n\r\n";
                else if(r.error.Contains("installation not found"))explanation="没有找到 Bitwig，请选择包含 Bitwig Studio.exe 的安装目录。\r\n\r\n";
                else if(r.exitCode==1223)explanation="已取消管理员授权，未开始安装。\r\n\r\n";
                details.Text=explanation+r.error+"\r\n"+r.output;return;
            }
            status.ForeColor=Color.FromArgb(33,112,70);
            if(r.action=="Status" && r.status!=null) {
                string value=Convert.ToString(r.status["status"]);bool healthy=Convert.ToBoolean(r.status["healthy"]),pending=Convert.ToBoolean(r.status["pending"]);
                if(value=="Original") {status.Text="已找到兼容的 Bitwig · 可以安装";details.Text="点击“安装 / 修复”即可添加视频组件。";}
                else if(value=="Installed"&&healthy&&!pending){status.Text="视频组件已安装 · 状态正常";details.Text="直接从原来的 Bitwig 图标启动，即可使用视频功能。\r\n重复安装可检查和修复组件；恢复原版会保留工程、媒体及用户设置。";}
                else if(value=="Installed"){status.Text="检测到待修复的组件";status.ForeColor=Color.DarkGoldenrod;details.Text="点击“安装 / 修复”完成缺失组件或中断的安装。";}
                else{status.Text="版本或安装文件不匹配";status.ForeColor=Color.Firebrick;details.Text="当前安装器适配 Windows x64 Bitwig 6.1.7 的已验证构建，无法覆盖未知版本。";}
                details.Text+="\r\n\r\n"+r.root;
            }else if(r.action=="Install") {status.Text="安装 / 修复完成";details.Text="可以关闭安装器，从原来的 Bitwig 图标启动。\r\n原版备份和独立恢复工具已保存在 Bitwig 安装目录内。\r\n\r\n"+r.output;}
            else{status.Text="已恢复原版 Bitwig";details.Text="视频组件已移除，工程、视频和用户设置已保留。\r\n\r\n"+r.output;}
            if(r.warning.Length>0){status.ForeColor=Color.DarkGoldenrod;details.Text+="\r\n\r\n"+r.warning;}
        }
        private void ShowCredits() {
            using(Form dialog=new Form{Text="技术来源与致谢",Size=new Size(680,540),StartPosition=FormStartPosition.CenterParent,Font=Font,MinimizeBox=false,MaximizeBox=false}) {
                TextBox text=new TextBox{Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Vertical,Dock=DockStyle.Fill,Text=Installer.ReadCredits().Replace("\n","\r\n"),BackColor=Color.White,Font=new Font("Segoe UI",10),BorderStyle=BorderStyle.None};
                dialog.Padding=new Padding(20);dialog.Controls.Add(text);dialog.ShowDialog(this);
            }
        }
    }
    internal static class Program {
        [STAThread] private static int Main(string[] args) {
            Options o=null;
            try {
                o=Options.Parse(args);
                if(o.Headless) {
                    Receipt r=o.Worker?Installer.Backend(o):Installer.Execute(o);
                    Installer.Save(r,o.Report.Length>0?o.Report:Installer.LogPath(o.DataRoot,"cli"));
                    return r.ok?0:r.exitCode==0?1:r.exitCode;
                }
                Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new SetupForm(o));return 0;
            }catch(Exception e) {
                if(o!=null&&o.Report.Length>0){try{Installer.Save(new Receipt{action=o.Action,root=o.Root,ok=false,exitCode=1,error=e.Message},o.Report);}catch{}}
                else if(!args.Contains("--headless")&&!args.Contains("--worker"))MessageBox.Show(e.Message,"Native Video Track",MessageBoxButtons.OK,MessageBoxIcon.Error);
                return 1;
            }
        }
    }
}
