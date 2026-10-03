const fs=require('fs');const {JSDOM,VirtualConsole}=require('jsdom');const {createCanvas}=require('@napi-rs/canvas');
const assets='app/src/main/assets/';const out='app/build/qa/';fs.mkdirSync(out,{recursive:true});const errors=[];
function load(name,language="en"){let clock=0,next=1;const timers=new Map();const vc=new VirtualConsole();vc.on('jsdomError',e=>errors.push(name+': '+e.message));const d=new JSDOM(fs.readFileSync(assets+name+'.html','utf8'),{runScripts:'dangerously',url:'https://local.test/'+name+'.html?lang='+language,pretendToBeVisual:true,virtualConsole:vc,beforeParse(w){w.matchMedia=()=>({matches:false,addListener(){},removeListener(){}});w.HTMLCanvasElement.prototype.getContext=function(){if(!this._canvas)this._canvas=createCanvas(this.width||800,this.height||500);if(this._canvas.width!==this.width)this._canvas.width=this.width||800;if(this._canvas.height!==this.height)this._canvas.height=this.height||500;return this._canvas.getContext('2d')};w.HTMLCanvasElement.prototype.toDataURL=function(){return this._canvas.toDataURL()};w.HTMLElement.prototype.getBoundingClientRect=function(){return{width:900,height:560,left:0,top:0,right:900,bottom:560}};Object.defineProperty(w.HTMLElement.prototype,'clientWidth',{get(){return 900}});Object.defineProperty(w.HTMLElement.prototype,'clientHeight',{get(){return 560}});w.HTMLElement.prototype.setPointerCapture=function(){};w.Date.now=()=>clock;w.performance.now=()=>clock;w.setTimeout=(f,ms=0)=>{const id=next++;timers.set(id,{f,at:clock+ms});return id};w.setInterval=(f,ms)=>{const id=next++;timers.set(id,{f,at:clock+ms,ms});return id};w.clearTimeout=w.clearInterval=id=>timers.delete(id);w.requestAnimationFrame=f=>w.setTimeout(()=>f(clock),16);w.cancelAnimationFrame=w.clearTimeout;w.PointerEvent=w.MouseEvent;}});
return {w:d.window,tick(ms){let count=0;const stop=clock+ms;while(count++<20000){const entry=[...timers].filter(([,t])=>t.at<=stop).sort((a,b)=>a[1].at-b[1].at)[0];if(!entry)break;const[id,t]=entry;clock=t.at;if(t.ms)t.at+=t.ms;else timers.delete(id);try{t.f()}catch(e){errors.push(name+': '+String(e))}}clock=stop},close(){d.window.close();timers.clear()}}}
const lab=load('lab');lab.tick(32);
const catalogChecks=lab.w.eval(`(()=>{
 const cards=[...document.querySelectorAll('.labCard')];
 if(cards.length!==68)throw Error('Expected 68 lesson cards');
 if(cards.some(c=>!LAB_TOPICS[c.dataset.topic] || !LAB_EN[c.dataset.lab]))throw Error('Missing lesson mapping');
 catalogSubject='physics';applyLabCatalog();
 if(cards.filter(c=>c.style.display!=='none').length!==17)throw Error('Physics filter');
 document.getElementById('labTopic').value='electricity';applyLabCatalog();
 if(cards.filter(c=>c.style.display!=='none').map(c=>c.dataset.lab).join()!=='circuit')throw Error('Lesson topic filter');
 document.getElementById('labTopic').value='all';catalogSubject='all';search.value='Bayes';applyLabCatalog();
 if(cards.filter(c=>c.style.display!=='none').map(c=>c.dataset.lab).join()!=='bayes')throw Error('English catalog search');
 search.value='';applyLabCatalog();cards.find(c=>c.dataset.lab==='lens').click();
 if(window.vuraCatalogOpen || CUR.exp.physics.id!=='lens')throw Error('Opening catalog item');
 return {cards:cards.length,subjectFilter:true,topicFilter:true,search:true,navigation:true};
})()`);
const labs=lab.w.eval(`(()=>{window.vuraPaused=true;const results=[];for(const[key,items]of Object.entries(REG))for(const item of items){for(const level of ['value','min','max']){try{setTab(key,false);selectExp(key,item.id,false);const ex=CUR.exp[key];for(const c of ex.controls||[])if(c.type==='range'){ex.p[c.id]=c[level];ex.change&&ex.change(ex,c.id)}const cv=CVS[key];const{w,h}=fitCanvas(cv);const ctx=cv.getContext('2d');for(let i=0;i<30;i++)ex.frame(ex,ctx,w,h,.016,i*.016);results.push({id:ex.id,level,ok:true})}catch(e){results.push({id:item.id,level,ok:false,error:String(e)})}}}return results})()`);
lab.w.eval("showLabCatalog(false);setTab('physics',false);selectExp('physics','lens',false);window.vuraPaused=false");lab.tick(32);const snapshot=lab.w.vuraSnapshot();fs.writeFileSync(out+'discovery-lens-canvas.png',Buffer.from(snapshot.split(',')[1],'base64'));lab.close();
const labFa=load('lab','fa');labFa.tick(32);
const catalogFa=labFa.w.eval(`(()=>{
 if(document.getElementById('labCatalog').dir!=='rtl')throw Error('Persian catalog direction');
 search.value='شیمی';applyLabCatalog();
 const shown=[...document.querySelectorAll('.labCard')].filter(c=>c.style.display!=='none');
 if(shown.length!==17 || shown.some(c=>c.dataset.subject!=='chem'))throw Error('Persian subject search');
 return {direction:'rtl',search:true};
})()`);labFa.close();
const arc=load('arcade');const ids=arc.w.eval('GAMES.map(g=>g.id)');const games=[];
for(const id of ids){const before=errors.length;try{arc.w.eval(`openGame(${JSON.stringify(id)})`);arc.tick(6000);for(const node of [...arc.w.document.querySelectorAll('.half .opt,.half .pad')].slice(0,6)){node.dispatchEvent(new arc.w.PointerEvent('pointerdown',{bubbles:true}));node.dispatchEvent(new arc.w.PointerEvent('pointerup',{bubbles:true}))}arc.tick(16000);arc.w.eval('endMatch(0);openGame(curId)');arc.tick(100);arc.w.eval('exitGame()');arc.tick(16000)}catch(e){errors.push(id+': '+String(e))}games.push({id,ok:before===errors.length})}
arc.w.eval("openGame('pong')");arc.tick(2000);fs.writeFileSync(out+'arcade-pong-canvas.png',arc.w.document.querySelector('canvas')._canvas.toBuffer('image/png'));arc.close();
const report={catalog:catalogChecks,catalogFa,runtime:'jsdom 26.1 + native Canvas; not an Android WebView or full browser layout test',labs:labs.length,labFailures:labs.filter(x=>!x.ok),games:games.length,gameFailures:games.filter(x=>!x.ok),errors};fs.writeFileSync(out+'web-qa.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));process.exitCode=report.labFailures.length||report.gameFailures.length||errors.length?1:0;
