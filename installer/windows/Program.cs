using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace Salutem.Installer {
    internal static class Payload {
        internal static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 16777216 };
        internal static string Digest(Stream input) {
            using (var sha = SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(input)).Replace("-", "").ToLowerInvariant();
        }
        internal static string DigestFile(string path) { using (var s = File.OpenRead(path)) return Digest(s); }
        internal static string Quote(string value) {
            // Windows CommandLineToArgvW quoting, including trailing backslashes.
            return "\"" + System.Text.RegularExpressions.Regex.Replace(value, "(\\\\*)\"", "$1$1\\\"")
                .TrimEnd('\\') + new string('\\', value.Reverse().TakeWhile(c => c == '\\').Count() * 2) + "\"";
        }
        internal static string SafePath(string root, string relative) {
            if (String.IsNullOrWhiteSpace(relative) || relative.Contains(":") || Path.IsPathRooted(relative)) throw new IOException("Caminho inválido no pacote.");
            string target = Path.GetFullPath(Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar)));
            if (!target.StartsWith(Path.GetFullPath(root).TrimEnd('\\') + "\\", StringComparison.OrdinalIgnoreCase)) throw new IOException("Arquivo fora do pacote.");
            return target;
        }
        internal static string Unpack(string destination) {
            var assembly = Assembly.GetExecutingAssembly(); string expected;
            using (var s = new StreamReader(assembly.GetManifestResourceStream("PayloadHash"))) expected = s.ReadToEnd().Trim();
            using (var s = assembly.GetManifestResourceStream("Payload")) if (Digest(s) != expected) throw new IOException("Integridade do pacote interno inválida.");
            string root = destination ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Salutem", "JS", "p", expected.Substring(0, 16));
            if (Directory.Exists(root)) { if (destination != null) throw new IOException("O diretório de teste deve ser novo."); Verify(root); return root; }
            string stage = root + "." + Guid.NewGuid().ToString("N").Substring(0, 8); Directory.CreateDirectory(stage);
            using (var s = assembly.GetManifestResourceStream("Payload"))
            using (var zip = new ZipArchive(s, ZipArchiveMode.Read)) {
                var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var entry in zip.Entries) {
                    string target = SafePath(stage, entry.FullName);
                    if (!seen.Add(target)) throw new IOException("Entrada duplicada no pacote.");
                    if (entry.FullName.EndsWith("/")) { Directory.CreateDirectory(target); continue; }
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    using (var input = entry.Open()) using (var output = new FileStream(target, FileMode.CreateNew)) input.CopyTo(output);
                }
            }
            Verify(stage); Directory.CreateDirectory(Path.GetDirectoryName(root)); Directory.Move(stage, root); return root;
        }
        internal static void Verify(string root) {
            var manifest = Json.Deserialize<Dictionary<string, string>>(File.ReadAllText(Path.Combine(root, "payload-files.json"), Encoding.UTF8));
            foreach (var file in manifest) if (DigestFile(SafePath(root, file.Key)) != file.Value) throw new IOException("Arquivo alterado ou incompleto: " + file.Key);
            var actual = Directory.GetFiles(root, "*", SearchOption.AllDirectories).Select(p => p.Substring(root.Length + 1).Replace('\\','/'));
            if (actual.Any(p => p != "payload-files.json" && !manifest.ContainsKey(p))) throw new IOException("Arquivo inesperado no pacote extraído.");
        }
    }

    internal sealed class MainWindow : Form {
        readonly string payload,stateRoot;
        readonly ComboBox studio=new ComboBox{Dock=DockStyle.Fill,DropDownStyle=ComboBoxStyle.DropDown};
        readonly CheckedListBox installations=new CheckedListBox{Dock=DockStyle.Fill,CheckOnClick=true,HorizontalScrollbar=true};
        readonly Button inspect=new Button{Text="Verificar Selecionados",AutoSize=true};
        readonly Button install=new Button{Text="Instalar Pacote Completo",AutoSize=true,Enabled=false};
        readonly Button launch=new Button{Text="Abrir Studio",AutoSize=true,Enabled=false};
        readonly Button cancel=new Button{Text="Cancelar Verificação",AutoSize=true,Enabled=false};
        readonly Button browse=new Button{Text="Adicionar Pasta…",AutoSize=true};
        readonly TextBox status=new TextBox{Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Vertical,Dock=DockStyle.Fill};
        readonly string[] ids={"themes","translation","connection-order"};
        Process active;bool busy,cancelled;string activeAction;
        sealed class Installation { internal string Path,Version,State;public override string ToString(){return Version+" — "+State+" — "+Path;} }
        internal MainWindow(string package,bool openMode){
            payload=package;stateRoot=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Salutem","JS","o");
            Text="2.8.0 — Pacote Unificado de Personalização do Jaspersoft Studio";Size=new Size(1020,740);MinimumSize=new Size(820,640);StartPosition=FormStartPosition.CenterScreen;Font=new Font("Segoe UI",10);AutoScaleMode=AutoScaleMode.Dpi;BackColor=Color.FromArgb(243,245,249);
            var layout=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(18),ColumnCount=1,RowCount=6};
            foreach(float height in new float[]{76,42,190,48})layout.RowStyles.Add(new RowStyle(SizeType.Absolute,height));layout.RowStyles.Add(new RowStyle(SizeType.Percent,100));layout.RowStyles.Add(new RowStyle(SizeType.Absolute,42));
            layout.Controls.Add(new Label{Dock=DockStyle.Fill,Text="Marque uma ou mais instalações. Tema, tradução e conexões serão instalados juntos.\r\nCada versão recebe seus próprios binários. Perfis parciais continuam identificados para testes."},0,0);
            var row=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=2};row.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));row.Controls.Add(studio,0,0);row.Controls.Add(browse,1,0);layout.Controls.Add(row,0,1);layout.Controls.Add(installations,0,2);
            var actions=new FlowLayoutPanel{Dock=DockStyle.Fill};var logs=new Button{Text="Abrir Logs",AutoSize=true};actions.Controls.AddRange(new Control[]{inspect,install,launch,cancel,logs});layout.Controls.Add(actions,0,3);layout.Controls.Add(status,0,4);
            layout.Controls.Add(new Label{Dock=DockStyle.Fill,Text="Salve e feche os Studios antes de instalar. Cada instalação possui backup e recuperação próprios.\r\nNenhum relatório é modificado. Instalações não marcadas permanecem intactas."},0,5);Controls.Add(layout);
            install.BackColor=Color.FromArgb(31,111,235);install.ForeColor=Color.White;install.FlatStyle=FlatStyle.Flat;
            foreach(string directory in Discover())AddInstallation(directory,false);
            browse.Click+=delegate{using(var dialog=new FolderBrowserDialog{Description="Pasta do Jaspersoft Studio",ShowNewFolderButton=false})if(dialog.ShowDialog(this)==DialogResult.OK){studio.Text=dialog.SelectedPath;AddInstallation(dialog.SelectedPath,true);}};
            studio.KeyDown+=delegate(object sender,KeyEventArgs e){if(e.KeyCode==Keys.Enter){e.SuppressKeyPress=true;AddInstallation(studio.Text,true);}};
            installations.ItemCheck+=delegate{if(IsHandleCreated)BeginInvoke((Action)(()=>{if(!busy)install.Enabled=launch.Enabled=false;}));};
            inspect.Click+=delegate{Run("Inspect");};install.Click+=delegate{Run("Install");};launch.Click+=delegate{Run("Launch");};
            logs.Click+=delegate{Directory.CreateDirectory(stateRoot);Process.Start(new ProcessStartInfo("explorer.exe",Payload.Quote(stateRoot)){UseShellExecute=true});};
            cancel.Click+=delegate{cancelled=true;try{if(activeAction=="Inspect"&&active!=null&&!active.HasExited)active.Kill();}catch(InvalidOperationException){}cancel.Enabled=false;};
            FormClosing+=delegate(object sender,FormClosingEventArgs e){if(busy){e.Cancel=true;MessageBox.Show(this,"Aguarde a transação concluir ou cancele a verificação.",Text);}};
            status.Text=openMode?"Selecione uma instalação, verifique e clique em Abrir Studio.":"Selecione as instalações e clique em Verificar Selecionados.";AcceptButton=inspect;
        }
        internal static string VersionAt(string directory){try{string plugins=Path.Combine(directory,"plugins");foreach(string file in Directory.GetFiles(plugins,"com.jaspersoft.studio_*.jar")){using(var zip=ZipFile.OpenRead(file)){var entry=zip.GetEntry("META-INF/MANIFEST.MF");using(var reader=new StreamReader(entry.Open())){var match=System.Text.RegularExpressions.Regex.Match(reader.ReadToEnd(),@"(?m)^Bundle-Version: ([^\r\n]+)");if(match.Success)return match.Groups[1].Value;}}}}catch(IOException){}catch(UnauthorizedAccessException){}return "Não Identificada";}
        internal static string[] Discover(){
            var found=new HashSet<string>(StringComparer.OrdinalIgnoreCase);Action<string> add=p=>{if(!String.IsNullOrWhiteSpace(p)&&File.Exists(Path.Combine(p,"Jaspersoft Studio.exe"))&&File.Exists(Path.Combine(p,"Jaspersoft Studio.ini")))found.Add(Path.GetFullPath(p));};
            foreach(string root in new[]{Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs")}.Where(Directory.Exists))try{foreach(string vendor in Directory.GetDirectories(root).Where(p=>Path.GetFileName(p).IndexOf("Jasper",StringComparison.OrdinalIgnoreCase)>=0||Path.GetFileName(p).Equals("TIBCO",StringComparison.OrdinalIgnoreCase))){add(vendor);foreach(string child in Directory.GetDirectories(vendor))add(child);}}catch(UnauthorizedAccessException){}catch(IOException){}
            foreach(var hive in new[]{Microsoft.Win32.RegistryHive.LocalMachine,Microsoft.Win32.RegistryHive.CurrentUser})foreach(var view in new[]{Microsoft.Win32.RegistryView.Registry64,Microsoft.Win32.RegistryView.Registry32})try{using(var registry=Microsoft.Win32.RegistryKey.OpenBaseKey(hive,view))using(var uninstall=registry.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")){if(uninstall==null)continue;foreach(string key in uninstall.GetSubKeyNames())using(var item=uninstall.OpenSubKey(key)){if(item==null)continue;string name=Convert.ToString(item.GetValue("DisplayName"));if(name.IndexOf("Jaspersoft Studio",StringComparison.OrdinalIgnoreCase)<0)continue;add(Convert.ToString(item.GetValue("InstallLocation")));}}}catch(System.Security.SecurityException){}catch(UnauthorizedAccessException){}
            return found.OrderBy(p=>p,StringComparer.OrdinalIgnoreCase).ToArray();
        }
        void AddInstallation(string path,bool check){try{path=Path.GetFullPath(path.Trim());if(!File.Exists(Path.Combine(path,"Jaspersoft Studio.exe")))throw new IOException("Executável não encontrado.");foreach(Installation existing in installations.Items)if(existing.Path.Equals(path,StringComparison.OrdinalIgnoreCase)){if(check)installations.SetItemChecked(installations.Items.IndexOf(existing),true);return;}string version=VersionAt(path);installations.Items.Add(new Installation{Path=path,Version=version,State=version=="6.20.6.final"?"Referência — verificar componentes":"Testes / suporte parcial — verificar componentes"},check);studio.Items.Add(path);}catch(Exception error){status.Text=error.Message;}}
        bool ExperimentalFor(string target){return VersionAt(target)!="6.20.6.final";}
        async void Run(string action){
            if(busy)return;var selected=installations.CheckedItems.Cast<Installation>().ToArray();if(selected.Length==0){status.Text="Marque pelo menos uma instalação.";return;}if(action=="Launch"&&selected.Length!=1){status.Text="Para abrir, marque uma única instalação.";return;}
            if(action=="Install"&&MessageBox.Show(this,"Instalar o pacote completo nas instalações marcadas?\r\n"+String.Join("\r\n",selected.Select(p=>p.Path))+"\r\nPerfis parciais permanecem para testes; feche os Studios antes de continuar.",Text,MessageBoxButtons.OKCancel,MessageBoxIcon.Question)!=DialogResult.OK)return;
            busy=true;cancelled=false;activeAction=action;inspect.Enabled=install.Enabled=launch.Enabled=browse.Enabled=studio.Enabled=installations.Enabled=false;cancel.Enabled=action=="Inspect";status.Clear();bool all=true;
            try{foreach(var target in selected){if(cancelled)throw new OperationCanceledException();try{var result=await System.Threading.Tasks.Task.Run(()=>Execute(action,target.Path,ids,ExperimentalFor(target.Path)));bool ok=(bool)result["ok"];all&=ok;status.AppendText(target.Version+" — "+target.Path+"\r\n"+(string)result["message"]+"\r\n\r\n");}catch(Exception error){all=false;status.AppendText(target.Path+" — "+error.Message+"\r\n");}if(cancelled)throw new OperationCanceledException();}install.Enabled=all;launch.Enabled=all&&selected.Length==1;}catch(OperationCanceledException){status.AppendText("Verificação cancelada; nenhuma instalação iniciada pela verificação.");}finally{active=null;busy=false;cancel.Enabled=false;inspect.Enabled=browse.Enabled=studio.Enabled=installations.Enabled=true;}
        }
        internal Dictionary<string,object> Execute(string action,string target,string[] selected,bool experimentalMode){
            Payload.Verify(payload);string state=Path.Combine(stateRoot,Guid.NewGuid().ToString("N").Substring(0,12));Directory.CreateDirectory(state);string request=Path.Combine(state,"request.json");File.WriteAllText(request,Payload.Json.Serialize(new{action=action,studio=target,components=selected,experimental=experimentalMode}),new UTF8Encoding(false));
            string ps=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"WindowsPowerShell","v1.0","powershell.exe");var start=new ProcessStartInfo(ps,"-NoProfile -ExecutionPolicy Bypass -STA -File "+Payload.Quote(Path.Combine(payload,"Unified.ps1"))+" -RequestFile "+Payload.Quote(request)){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=payload};
            using(var process=new Process{StartInfo=start}){process.OutputDataReceived+=delegate{};process.ErrorDataReceived+=delegate{};active=process;process.Start();process.BeginOutputReadLine();process.BeginErrorReadLine();process.WaitForExit();if(cancelled)throw new OperationCanceledException();string resultFile=Path.Combine(state,"result.json");if(!File.Exists(resultFile))throw new IOException("Sem resposta; código "+process.ExitCode+". Logs: "+state);var result=Payload.Json.Deserialize<Dictionary<string,object>>(File.ReadAllText(resultFile,Encoding.UTF8));if(process.ExitCode!=0&&(bool)result["ok"])throw new IOException("Resposta inconsistente.");return result;}
        }
        internal void AssertConstruction(){if(install.Enabled||launch.Enabled||ids.Length!=3)throw new Exception("Ações liberadas sem verificação.");bool rejected=false;try{Payload.SafePath(payload,"../escape");}catch(IOException){rejected=true;}if(!rejected)throw new Exception("Escape aceito.");if(!Payload.Quote("C:\\Pasta com espaço\\").EndsWith("\\\\\""))throw new Exception("Escape inválido.");}
    }
    internal static class Program {
        [STAThread]
        static int Main(string[] args) {
            bool test = args.Length == 2 && args[0] == "--self-test";
            try {
                AppContext.SetSwitch("Switch.System.IO.UseLegacyPathHandling", false);
                AppContext.SetSwitch("Switch.System.IO.BlockLongPaths", false);
                Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
                string package = Payload.Unpack(test ? args[1] : null);
                string report=null;for(int i=0;i<args.Length-1;i++)if(args[i]=="--report")report=args[i+1];
                if(args.Contains("--detect")){if(report==null)throw new ArgumentException("Informe --report.");File.WriteAllText(report,Payload.Json.Serialize(MainWindow.Discover().Select(p=>new{path=p,version=MainWindow.VersionAt(p)}).ToArray()),new UTF8Encoding(false));return 0;}
                using (var window = new MainWindow(package, args.Contains("--open"))) {
                    if (test) { window.AssertConstruction(); File.WriteAllText(Path.Combine(Path.GetDirectoryName(package), "self-test.json"), "{\"ok\":true,\"scope\":\"payload-integrity-and-form-construction-only\"}"); return 0; }
                    if(args.Length>=2&&(args[0]=="--inspect"||args[0]=="--install"||args[0]=="--verify")){if(report==null)throw new ArgumentException("Informe --report.");var result=window.Execute(args[0]=="--inspect"?"Inspect":args[0]=="--verify"?"Verify":"Install",args[1],new[]{"themes","translation","connection-order"},MainWindow.VersionAt(args[1])!="6.20.6.final");File.WriteAllText(report,Payload.Json.Serialize(result),new UTF8Encoding(false));return (bool)result["ok"]?0:1;}
                    Application.Run(window);
                }
                return 0;
            } catch (Exception error) { if (test) File.WriteAllText(Path.Combine(Path.GetDirectoryName(args[1]), "self-test-error.txt"), error.ToString()); else MessageBox.Show(error.Message, "Pacote de Atualizações do JasperStudio", MessageBoxButtons.OK, MessageBoxIcon.Error); return 1; }
        }
    }
}
