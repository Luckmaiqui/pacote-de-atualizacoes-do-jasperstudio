using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Drawing;
using System.Text;
using System.Windows.Forms;
using System.Web.Script.Serialization;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

internal static class GestaoPolicy {
 internal const string Url="https://gestao.sistemasalutem.com.br/management";
 internal static bool Allowed(string source){Uri uri;return Uri.TryCreate(source,UriKind.Absolute,out uri)&&uri.Scheme=="https"&&uri.Host=="gestao.sistemasalutem.com.br"&&uri.IsDefaultPort;}
 internal static void Validate(string json){
  if(json==null||Encoding.UTF8.GetByteCount(json)>5000000)throw new InvalidDataException("Exportação ausente ou maior que 5 MB.");
  var parser=new JavaScriptSerializer{MaxJsonLength=5000000,RecursionLimit=100};var root=parser.DeserializeObject(json) as Dictionary<string,object>;
  if(root==null||!root.ContainsKey("format")||!Equals(root["format"],"salutem-gestao-v1")||!root.ContainsKey("source")||!Equals(root["source"],Url)||!root.ContainsKey("connections")||!(root["connections"] is ICollection)||((ICollection)root["connections"]).Count>10000)throw new InvalidDataException("Formato ou origem da exportação inválidos.");
 }
}
internal sealed class ReaderWindow:Form {
 readonly WebView2 web=new WebView2();readonly Button read=new Button();readonly Label state=new Label();readonly string output,collector;readonly bool selfTest;
 internal int Result=2;
 internal ReaderWindow(string output,string collector,bool selfTest,string studioExe,Color background,Color foreground){
  this.output=output;this.collector=collector;this.selfTest=selfTest;Text="Gestão — Leitura de Conexões";Width=1120;Height=800;MinimumSize=new Size(750,500);StartPosition=FormStartPosition.CenterScreen;BackColor=background;ForeColor=foreground;
  bool jasperIcon=false;if(!String.IsNullOrEmpty(studioExe)&&File.Exists(studioExe)){try{Icon=System.Drawing.Icon.ExtractAssociatedIcon(studioExe);jasperIcon=Icon!=null;}catch{}}
  var top=new Panel{Dock=DockStyle.Top,Height=72};var title=new Label{Dock=DockStyle.Top,Height=30,Text="Acesse o Gestão e Realize o Login",Padding=new Padding(8,9,0,0),Font=new Font(Font,FontStyle.Bold)};
  state.Text="Esta sessão pode ser diferente da do seu navegador. Abra a lista de clientes e clique em Ler Conexões.";state.AutoSize=false;state.Dock=DockStyle.Fill;state.Padding=new Padding(8,4,8,0);top.Controls.Add(state);top.Controls.Add(title);
  var bottom=new FlowLayoutPanel{Dock=DockStyle.Bottom,Height=50,FlowDirection=FlowDirection.RightToLeft,Padding=new Padding(8,9,8,8)};
  var close=new Button{Text="Fechar",Width=90,Height=25,FlatStyle=FlatStyle.Flat,BackColor=background,ForeColor=foreground};close.Click+=delegate{Close();};
  read.Text="Ler Conexões";read.Width=100;read.Height=25;read.Enabled=false;read.FlatStyle=FlatStyle.Flat;read.BackColor=background;read.ForeColor=foreground;bottom.Controls.Add(close);bottom.Controls.Add(read);Controls.Add(web);Controls.Add(top);Controls.Add(bottom);web.Dock=DockStyle.Fill;
  Shown+=async delegate{try{
   CoreWebView2Environment.GetAvailableBrowserVersionString();
   string profile=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Salutem",selfTest?"GestaoReader-selftest":"GestaoReader");
   var environment=await CoreWebView2Environment.CreateAsync(null,profile,null);await web.EnsureCoreWebView2Async(environment);read.Enabled=true;
   if(selfTest){
    if(!GestaoPolicy.Allowed(GestaoPolicy.Url)||!GestaoPolicy.Allowed("https://gestao.sistemasalutem.com.br/clientes")||!GestaoPolicy.Allowed("https://gestao.sistemasalutem.com.br/#/clients")||GestaoPolicy.Allowed("https://gestao.sistemasalutem.com.br.evil/management")||GestaoPolicy.Allowed("http://gestao.sistemasalutem.com.br/management"))throw new Exception("Origem não foi delimitada.");
    GestaoPolicy.Validate("{\"format\":\"salutem-gestao-v1\",\"source\":\""+GestaoPolicy.Url+"\",\"connections\":[]}");
    bool refused=false;try{GestaoPolicy.Validate("{\"format\":\"other\"}");}catch(InvalidDataException){refused=true;}if(!refused)throw new Exception("Exportação inválida aceita.");
    // Model a real SPA transition: SourceChanged/pushState does not guarantee
    // NavigationCompleted. The button must remain available independently.
    await web.CoreWebView2.ExecuteScriptAsync("history.replaceState(null,'','#clientes')");
    if(!read.Enabled||!jasperIcon||BackColor!=background||read.Parent!=bottom)throw new Exception("Interface compartilhada, ícone ou botão incorretos.");
    WriteOutput("{\"passed\":true,\"scope\":\"WebView2 initialization, SPA-independent button, Jasper icon, themed footer and origin/schema checks; no remote login\"}");Result=0;Close();return;
   }
   await web.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync("window.salutemGestaoImport=function(json){window.chrome.webview.postMessage(json);return true;};");
   web.CoreWebView2.WebMessageReceived+=delegate(object sender,CoreWebView2WebMessageReceivedEventArgs message){
    if(!GestaoPolicy.Allowed(message.Source))return;
    try{string json=message.TryGetWebMessageAsString();GestaoPolicy.Validate(json);WriteOutput(json);Result=0;Close();}catch(Exception){MessageBox.Show(this,"Não foi possível conferir a exportação. Nenhuma conexão foi importada.","Leitura do Gestão");}
   };
   // A SPA may change routes without NavigationCompleted. Never disable the
   // reader based on its route; the collector checks the client-list DOM.
   read.Enabled=true;
   web.CoreWebView2.Navigate(GestaoPolicy.Url);
  }catch(Exception error){Result=3;if(selfTest){try{WriteOutput(new JavaScriptSerializer().Serialize(new{passed=false,message=error.GetType().Name}));}catch{}}else MessageBox.Show(this,"O Microsoft Edge WebView2 Runtime não pôde ser inicializado. Utilize o coletor externo e Importar JSON.\n\n"+error.GetType().Name,"Leitor do Gestão");Close();}};
  read.Click+=async delegate{if(web.CoreWebView2==null)return;if(!GestaoPolicy.Allowed(web.CoreWebView2.Source)){MessageBox.Show(this,"Acesse o Gestão e realize o login na lista de clientes.","Login Necessário");return;}read.Enabled=false;try{await web.CoreWebView2.ExecuteScriptAsync(File.ReadAllText(collector,Encoding.UTF8));}catch(Exception){MessageBox.Show(this,"Não foi possível executar a leitura. Confira a página e tente novamente.","Leitura do Gestão");}finally{if(!IsDisposed)read.Enabled=true;}};
 }
 void WriteOutput(string text){using(var stream=new FileStream(output,FileMode.CreateNew,FileAccess.Write,FileShare.None))using(var writer=new StreamWriter(stream,new UTF8Encoding(false)))writer.Write(text);}
}
internal static class GestaoReader {
 [STAThread] static int Main(string[] args){
  try{string output=null,collector=null,studioExe=null;Color background=Color.FromArgb(9,14,24),foreground=Color.White;bool selfTest=false;for(int i=0;i<args.Length;i++){if(args[i]=="--output"&&i+1<args.Length)output=args[++i];else if(args[i]=="--collector"&&i+1<args.Length)collector=args[++i];else if(args[i]=="--studio-exe"&&i+1<args.Length)studioExe=args[++i];else if(args[i]=="--background"&&i+1<args.Length)background=ColorTranslator.FromHtml(args[++i]);else if(args[i]=="--foreground"&&i+1<args.Length)foreground=ColorTranslator.FromHtml(args[++i]);else if(args[i]=="--self-test")selfTest=true;else return 4;}
   if(String.IsNullOrEmpty(output)||File.Exists(output)||(!selfTest&&(String.IsNullOrEmpty(collector)||!File.Exists(collector))))return 4;
   Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);using(var form=new ReaderWindow(output,collector,selfTest,studioExe,background,foreground)){Application.Run(form);return form.Result;}
  }catch{return 3;}
 }
}
