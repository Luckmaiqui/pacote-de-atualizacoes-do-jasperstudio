(async () => {
 'use strict';
 if(location.origin !== 'https://gestao.sistemasalutem.com.br') {alert('Acesse o Gestão e realize o login.');return;}
 if(window.__salutemExportRunning){alert('Uma leitura já está em andamento.');return;}
 const cards=()=>[...document.querySelectorAll('.management-client-card')];
 if(!cards().length){alert('Acesse o Gestão e realize o login na lista de clientes.');return;}
 window.__salutemExportRunning=true;
 const rows=[], warnings=[], seen=new Set(), pages=new Set();
 const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const signature=()=>cards().map(c=>c.querySelector('.font-semibold.tracking-tight.text-base')?.textContent?.trim()||'').join('|');
 const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
 async function turn(b){const previous=signature();b.click();for(let i=0;i<100;i++){await sleep(150);const now=signature();if(now && now!==previous){await sleep(350);return;}}throw new Error('A página não terminou de carregar. Nenhum arquivo parcial foi exportado.');}
 try {
  for(let n=0;n<100;n++){const prev=button('Anterior');if(!prev || prev.disabled)break;await turn(prev);if(n===99)throw new Error('Limite de páginas excedido.');}
  for(let n=0;n<100;n++){
   const sig=signature();if(!sig || pages.has(sig))throw new Error('Paginação repetida ou vazia.');pages.add(sig);
   for(const card of cards()){
    const title=card.querySelector('.font-semibold.tracking-tight.text-base')?.textContent?.trim()||'';
    const titleMatch=title.match(/^\(([0-9]+)\)\s*|^([0-9]+)\s*[-–—]\s*|^([0-9]+)\s+/);
    const titleCode=titleMatch && (titleMatch[1]||titleMatch[2]||titleMatch[3]);
    const ids=[...new Set([...card.querySelectorAll('span,div')].map(e=>e.textContent?.trim().match(/^ID:\s*([0-9]{1,8})$/)?.[1]).filter(Boolean))];
    const explicitCode=ids.length===1?ids[0]:null;
    if(ids.length>1 || (explicitCode && titleCode && Number(explicitCode)!==Number(titleCode))){warnings.push('Identificação divergente: '+(title||'Cliente sem título')+'. Revise o código no Gestão.');continue;}
    const code=explicitCode||titleCode;
    if(!code){warnings.push('Sem código numérico: '+(title||'Cliente sem título')+'. Cadastro não importado; revise o ID no Gestão.');continue;}
    function add(kind,label,endpoint){const key=code+'|'+kind+'|'+label+'|'+endpoint;if(!seen.has(key)){seen.add(key);rows.push({code,client:title,label,kind,endpoint});}}
    for(const copy of card.querySelectorAll('button[aria-label="Copiar host e porta do banco de dados"]')){
     const box=copy.closest('.management-access-data-row')?.parentElement;
     const parts=[...(box?.querySelectorAll('.management-access-data-row')||[])];
     const value=prefix=>{const row=parts.find(r=>r.querySelector('strong')?.textContent.trim().startsWith(prefix));return (row?.querySelector('span')?.textContent||'').replace(new RegExp('^\\s*'+prefix+'\\s*'), '').trim();};
     add('DATABASE',value('Nome:'),value('Host:'));
    }
    for(const copy of card.querySelectorAll('button[aria-label="Copiar URL Jasper"]')){
     const a=copy.closest('.management-access-data-row')?.querySelector('a[href]');if(a)add('SERVER','JasperServer',a.getAttribute('href').trim());
    }
   }
   const next=button('Próxima');if(!next || next.disabled)break;if(n===99)throw new Error('Limite de páginas excedido.');await turn(next);
  }
  const data=JSON.stringify({format:'salutem-gestao-v1',source:'https://gestao.sistemasalutem.com.br/management',warnings,connections:rows},null,2);
  if(typeof window.salutemGestaoImport==='function')window.salutemGestaoImport(data);
  else {const url=URL.createObjectURL(new Blob([data],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='conexoes-gestao.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);alert(rows.length+' conexões lidas. '+warnings.length+' avisos para revisão. Importe conexoes-gestao.json no JasperStudio.');}
 }catch(e){alert('Não foi possível exportar: '+e.message);}finally{delete window.__salutemExportRunning;}
})();
