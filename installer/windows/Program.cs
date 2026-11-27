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
        readonly ComboBox studio=new ComboBox{Dock=DockStyle.Top,DropDownStyle=ComboBoxStyle.DropDownList};
        readonly Label pathLabel=new Label{Dock=DockStyle.Top,Height=64,AutoEllipsis=true};
        readonly Button next=new Button{Text="Seguir",AutoSize=true,MinimumSize=new Size(115,36)};
        readonly Button back=new Button{Text="Voltar",AutoSize=true,MinimumSize=new Size(100,36)};
        readonly Button browse=new Button{Text="Selecionar Outra Pasta…",AutoSize=true};
        readonly Button logs=new Button{Text="Mostrar Logs",AutoSize=true};
        readonly CheckBox openAfter=new CheckBox{Text="Abrir o Jaspersoft Studio ao finalizar",Checked=true,AutoSize=true,Dock=DockStyle.Top};
        readonly TextBox status=new TextBox{Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Vertical,Dock=DockStyle.Fill};
        readonly Panel body=new Panel{Dock=DockStyle.Fill,Padding=new Padding(28,16,28,16)};
        readonly Label heading=new Label{Dock=DockStyle.Top,Height=54,Font=new Font("Segoe UI",18,FontStyle.Bold),Padding=new Padding(24,10,0,0)};
        readonly Label phase=new Label{Dock=DockStyle.Top,Height=55};
        readonly ProgressBar progress=new ProgressBar{Dock=DockStyle.Top,Height=20,Style=ProgressBarStyle.Marquee,MarqueeAnimationSpeed=30};
        readonly string[] ids={"themes","translation","connection-order"};
        Process active;bool busy;readonly bool openMode;int page;Installation target;
        sealed class Installation { internal string Path,Version;public override string ToString(){string version=Version.Replace(".final","");return version;} }
        internal MainWindow(string package,bool openMode){
            this.openMode=openMode;payload=package;stateRoot=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Salutem","JS","o");
            using(var iconStream=Assembly.GetExecutingAssembly().GetManifestResourceStream("JasperIcon"))using(var brand=new Icon(iconStream)){Icon=(Icon)brand.Clone();}ShowIcon=true;ShowInTaskbar=true;
            Text="2.8.1 — Pacote de Atualizações do JasperStudio";Size=new Size(820,590);MinimumSize=new Size(720,520);StartPosition=FormStartPosition.CenterScreen;Font=new Font("Segoe UI",10);AutoScaleMode=AutoScaleMode.Dpi;BackColor=Color.FromArgb(243,245,249);
            var footer=new FlowLayoutPanel{Dock=DockStyle.Bottom,Height=66,Padding=new Padding(22,12,22,10),FlowDirection=FlowDirection.RightToLeft};footer.Controls.AddRange(new Control[]{next,back,logs});Controls.Add(body);Controls.Add(heading);Controls.Add(footer);
            next.BackColor=Color.FromArgb(31,111,235);next.ForeColor=Color.White;next.FlatStyle=FlatStyle.Flat;
            foreach(string directory in Discover())AddInstallation(directory,false);
            browse.Click+=delegate{using(var dialog=new FolderBrowserDialog{Description="Selecione a pasta que contém Jaspersoft Studio.exe",ShowNewFolderButton=false})if(dialog.ShowDialog(this)==DialogResult.OK)AddInstallation(dialog.SelectedPath,true);};
            studio.SelectedIndexChanged+=delegate{UpdateSelection();};
            next.Click+=delegate{if(page==0)ShowPage(1);else if(page==1)RunWizard();else if(page==3)Finish();else if(page==4)Close();};
            back.Click+=delegate{ShowPage(page==1?0:1);};
            logs.Click+=delegate{Directory.CreateDirectory(stateRoot);Process.Start(new ProcessStartInfo("explorer.exe",Payload.Quote(stateRoot)){UseShellExecute=true});};
            FormClosing+=delegate(object sender,FormClosingEventArgs e){if(busy){e.Cancel=true;phase.Text="Aguarde a operação terminar. A instalação mantém backup e recuperação em caso de falha.";}};
            ShowPage(0);AcceptButton=next;
        }
        internal static string VersionAt(string directory){try{string plugins=Path.Combine(directory,"plugins");foreach(string file in Directory.GetFiles(plugins,"com.jaspersoft.studio_*.jar")){using(var zip=ZipFile.OpenRead(file)){var entry=zip.GetEntry("META-INF/MANIFEST.MF");using(var reader=new StreamReader(entry.Open())){var match=System.Text.RegularExpressions.Regex.Match(reader.ReadToEnd(),@"(?m)^Bundle-Version: ([^\r\n]+)");if(match.Success)return match.Groups[1].Value;}}}}catch(IOException){}catch(UnauthorizedAccessException){}return "Não Identificada";}
        internal static string[] Discover(){
            var found=new HashSet<string>(StringComparer.OrdinalIgnoreCase);Action<string> add=p=>{if(!String.IsNullOrWhiteSpace(p)&&File.Exists(Path.Combine(p,"Jaspersoft Studio.exe"))&&File.Exists(Path.Combine(p,"Jaspersoft Studio.ini")))found.Add(Path.GetFullPath(p));};
            foreach(string root in new[]{Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs")}.Where(Directory.Exists))try{foreach(string vendor in Directory.GetDirectories(root).Where(p=>Path.GetFileName(p).IndexOf("Jasper",StringComparison.OrdinalIgnoreCase)>=0||Path.GetFileName(p).Equals("TIBCO",StringComparison.OrdinalIgnoreCase))){add(vendor);foreach(string child in Directory.GetDirectories(vendor))add(child);}}catch(UnauthorizedAccessException){}catch(IOException){}
            foreach(var hive in new[]{Microsoft.Win32.RegistryHive.LocalMachine,Microsoft.Win32.RegistryHive.CurrentUser})foreach(var view in new[]{Microsoft.Win32.RegistryView.Registry64,Microsoft.Win32.RegistryView.Registry32})try{using(var registry=Microsoft.Win32.RegistryKey.OpenBaseKey(hive,view))using(var uninstall=registry.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")){if(uninstall==null)continue;foreach(string key in uninstall.GetSubKeyNames())using(var item=uninstall.OpenSubKey(key)){if(item==null)continue;string name=Convert.ToString(item.GetValue("DisplayName"));if(name.IndexOf("Jaspersoft Studio",StringComparison.OrdinalIgnoreCase)<0)continue;add(Convert.ToString(item.GetValue("InstallLocation")));}}}catch(System.Security.SecurityException){}catch(UnauthorizedAccessException){}
            return found.OrderBy(p=>p,StringComparer.OrdinalIgnoreCase).ToArray();
        }
        void AddInstallation(string path,bool select){
            try{path=Path.GetFullPath(path.Trim());if(!File.Exists(Path.Combine(path,"Jaspersoft Studio.exe")))throw new IOException("Executável não encontrado nessa pasta.");
                foreach(Installation item in studio.Items)if(item.Path.Equals(path,StringComparison.OrdinalIgnoreCase)){if(select)studio.SelectedItem=item;return;}
                var added=new Installation{Path=path,Version=VersionAt(path)};studio.Items.Add(added);if(select||studio.SelectedIndex<0)studio.SelectedItem=added;
            }catch(Exception error){MessageBox.Show(this,error.Message,"Selecionar Instalação",MessageBoxButtons.OK,MessageBoxIcon.Information);}
        }
        void UpdateSelection(){target=studio.SelectedItem as Installation;pathLabel.Text=target==null?"Nenhuma instalação encontrada. Selecione uma pasta.":"Pasta da instalação:\r\n"+target.Path;if(page==1)next.Enabled=target!=null;}
        void TextPage(string text){body.Controls.Add(new Label{Dock=DockStyle.Fill,Text=text,Padding=new Padding(0,12,0,0)});}
        void ShowPage(int value){
            page=value;body.Controls.Clear();next.Visible=true;next.Enabled=true;back.Visible=value==1||value==4;logs.Visible=value==4;next.Text="Seguir";
            if(value==0){heading.Text=openMode?"Abrir o Jaspersoft Studio":"Novidades desta atualização";TextPage("Pacote de Atualizações do JasperStudio — 2.8.1\r\n\r\n• Tema, tradução e organização das conexões em uma única instalação.\r\n• Pesquisa nos exploradores e nos servidores.\r\n• Importação de conexões do Gestão e cadastro de contas.\r\n• Componentes específicos para cada versão do Studio.\r\n\r\nNa próxima tela, escolha uma instalação. Salve seus relatórios e feche essa versão do Studio antes de atualizar.");}
            else if(value==1){heading.Text="Selecione a versão";var panel=new FlowLayoutPanel{Dock=DockStyle.Top,Height=54};panel.Controls.Add(browse);body.Controls.Add(panel);body.Controls.Add(pathLabel);body.Controls.Add(studio);body.Controls.Add(new Label{Dock=DockStyle.Top,Height=40,Text="Jaspersoft Studio encontrado neste computador:"});next.Text=openMode?"Verificar e Abrir":"Verificar";UpdateSelection();}
            else if(value==2){heading.Text="Atualizando o JasperStudio";body.Controls.Add(status);body.Controls.Add(progress);body.Controls.Add(phase);next.Visible=back.Visible=logs.Visible=false;progress.Style=ProgressBarStyle.Marquee;progress.MarqueeAnimationSpeed=30;}
            else if(value==3){heading.Text="Instalação concluída";openAfter.Checked=true;body.Controls.Add(openAfter);body.Controls.Add(new Label{Dock=DockStyle.Top,Height=110,Text="O pacote foi instalado e os arquivos foram conferidos com sucesso.\r\n\r\nJaspersoft Studio "+target.Version.Replace(".final","")+"\r\n"+target.Path});next.Text="Finalizar";}
            else{heading.Text="Não foi possível concluir";body.Controls.Add(status);next.Text="Fechar";}
        }
        internal static void CheckedStep(string action,Func<string,Dictionary<string,object>> execute,Action<string> report){
            report(action);var result=execute(action);if(!result.ContainsKey("ok")||!(bool)result["ok"])throw new IOException(Convert.ToString(result["message"]));
        }
        internal static void InstallationFlow(Func<string,Dictionary<string,object>> execute,Action<string> report){CheckedStep("Inspect",execute,report);CheckedStep("Install",execute,report);CheckedStep("Verify",execute,report);}
        internal static void FinishFlow(bool open,Action launch){if(open)launch();}
        async void RunWizard(){
            if(busy||target==null)return;string path=target.Path;busy=true;status.Clear();ShowPage(2);
            try{
                await System.Threading.Tasks.Task.Run(()=>{
                    Func<string,Dictionary<string,object>> run=action=>Execute(action,path,ids,VersionAt(path)!="6.20.6.final");
                    Action<string> report=action=>BeginInvoke((Action)(()=>{string text=action=="Inspect"?"Verificando a instalação e preparando o pacote…":action=="Install"?"Instalando as atualizações e guardando o backup…":action=="Verify"?"Conferindo os arquivos instalados…":"Abrindo o Jaspersoft Studio…";phase.Text=text;status.AppendText(text+"\r\n");}));
                    if(openMode){CheckedStep("Inspect",run,report);CheckedStep("Launch",run,report);}else InstallationFlow(run,report);
                });
                busy=false;if(openMode){Close();return;}progress.Style=ProgressBarStyle.Blocks;progress.Value=100;ShowPage(3);
            }catch(Exception error){busy=false;status.AppendText("\r\n"+error.Message+"\r\n\r\nNenhuma próxima etapa será executada. Consulte os logs para os detalhes e a recuperação da transação.");ShowPage(4);}
            finally{active=null;busy=false;}
        }
        async void Finish(){
            if(busy)return;bool shouldOpen=openAfter.Checked;busy=true;next.Enabled=false;
            try{await System.Threading.Tasks.Task.Run(()=>FinishFlow(shouldOpen,()=>{var result=Execute("Launch",target.Path,ids,VersionAt(target.Path)!="6.20.6.final");if(!(bool)result["ok"])throw new IOException(Convert.ToString(result["message"]));}));busy=false;Close();}
            catch(Exception error){busy=false;status.Text="A instalação foi concluída, mas não foi possível solicitar a abertura do Studio.\r\n"+error.Message;ShowPage(4);}
        }
        internal Dictionary<string,object> Execute(string action,string target,string[] selected,bool experimentalMode){
            Payload.Verify(payload);string state=Path.Combine(stateRoot,Guid.NewGuid().ToString("N").Substring(0,12));Directory.CreateDirectory(state);string request=Path.Combine(state,"request.json");File.WriteAllText(request,Payload.Json.Serialize(new{action=action,studio=target,components=selected,experimental=experimentalMode}),new UTF8Encoding(false));
            string ps=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"WindowsPowerShell","v1.0","powershell.exe");var start=new ProcessStartInfo(ps,"-NoProfile -ExecutionPolicy Bypass -STA -File "+Payload.Quote(Path.Combine(payload,"Unified.ps1"))+" -RequestFile "+Payload.Quote(request)){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=payload};
            using(var process=new Process{StartInfo=start}){process.OutputDataReceived+=delegate{};process.ErrorDataReceived+=delegate{};active=process;process.Start();process.BeginOutputReadLine();process.BeginErrorReadLine();process.WaitForExit();string resultFile=Path.Combine(state,"result.json");if(!File.Exists(resultFile))throw new IOException("Sem resposta; código "+process.ExitCode+". Logs: "+state);var result=Payload.Json.Deserialize<Dictionary<string,object>>(File.ReadAllText(resultFile,Encoding.UTF8));if(process.ExitCode!=0&&(bool)result["ok"])throw new IOException("Resposta inconsistente.");return result;}
        }
        internal void AssertConstruction(){
            if(page!=0||studio.DropDownStyle!=ComboBoxStyle.DropDownList||!openAfter.Checked||ids.Length!=3||Icon==null||!ShowIcon||!ShowInTaskbar)throw new Exception("Estado inicial ou ícone inválido.");
            if(new Installation{Version="6.21.0.final",Path="C:\\teste"}.ToString()!="6.21.0"||new Installation{Version="6.16.0.final"}.ToString()!="6.16.0")throw new Exception("Rótulos das versões inválidos.");
            var calls=new List<string>();Func<string,Dictionary<string,object>> ok=a=>{calls.Add(a);return new Dictionary<string,object>{{"ok",true}};};
            InstallationFlow(ok,a=>{});if(String.Join(",",calls)!="Inspect,Install,Verify")throw new Exception("Sequência inválida.");
            foreach(string failure in new[]{"Inspect","Install","Verify"}){calls.Clear();try{InstallationFlow(a=>{calls.Add(a);return new Dictionary<string,object>{{"ok",a!=failure},{"message","Falha simulada"}};},a=>{});throw new Exception("Falha ignorada.");}catch(IOException){}if(calls.Last()!=failure)throw new Exception("Etapa executada após falha.");}
            int launched=0;FinishFlow(false,()=>launched++);if(launched!=0)throw new Exception("Abriu com opção desmarcada.");FinishFlow(true,()=>launched++);if(launched!=1)throw new Exception("Não abriu com opção marcada.");
            var saved=target;ShowPage(1);if(studio.Parent!=body)throw new Exception("Seleção não exibida.");ShowPage(2);if(progress.Parent!=body||status.Parent!=body)throw new Exception("Progresso ausente.");target=new Installation{Version="6.21.0.final",Path="C:\\teste"};ShowPage(3);openAfter.Checked=false;ShowPage(3);if(!openAfter.Checked||openAfter.Parent!=body)throw new Exception("Opção de abertura ausente.");ShowPage(4);if(status.Parent!=body)throw new Exception("Tela de falha ausente.");target=saved;ShowPage(0);
            bool rejected=false;try{Payload.SafePath(payload,"../escape");}catch(IOException){rejected=true;}if(!rejected)throw new Exception("Escape aceito.");
        }
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
