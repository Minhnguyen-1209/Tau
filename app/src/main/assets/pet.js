ST.pet=function(){petGame(0)};ST.girl=function(){petGame(1)};ST.img=function(){petGame(2)};
function petGame(kind){var girl=kind==1;
stFrame(kind==2?'Bạn nhỏ của bạn':girl?'Lam, cô bạn nhỏ':'Mầm, bạn nhỏ của bạn','<canvas id="c" width="400" height="520" style="touch-action:none"></canvas><div class="tray" id="tr"></div><div class="msg" style="font-size:13px;font-weight:normal;color:#6B7280">Vuốt lên đầu để xoa · chạm bụng để cù · kéo đồ ăn vào miệng (hoặc chạm một lần)</div>');
var c=document.getElementById('c'),x=c.getContext('2d');
var FOODS=[['🍙','Cơm nắm',22,4,'Ngon quá!'],['🍎','Táo',14,4,'Giòn quá~'],['🍓','Dâu',10,7,'Ngọt ghê!'],['🍪','Bánh quy',16,8,'Mmm, thích nhất!'],['🥛','Sữa',12,4,'Ực ực… ấm bụng']];
var D;try{D=JSON.parse(localStorage.getItem('tau_pet'+(kind==2?'_i':girl?'_g':''))||'null')}catch(e){}if(!D)D={s:60,j:60,t:Date.now()};
var hrs=Math.max(0,(Date.now()-D.t)/3600000);D.s=Math.max(15,D.s-hrs*8);D.j=Math.max(20,D.j-hrs*5);
function sv(){D.t=Date.now();try{localStorage.setItem('tau_pet'+(kind==2?'_i':girl?'_g':''),JSON.stringify(D))}catch(e){}}
function Sp(t,k,d){return{p:t,v:0,t:t,k:k||.16,d:d||.74}}function st(s){s.v+=(s.t-s.p)*s.k;s.v*=s.d;s.p+=s.v}
var sy=Sp(1),sx=Sp(1),rot=Sp(0),hop=Sp(0),ear=Sp(0,.2,.7);
var ex='idle',exUntil=0,sleeping=false,lastAct=Date.now(),nextBlink=Date.now()+2000,blinkUntil=0,bubble=null,P=[],pk=null,stroking=false,sd=0,lp=null,look=[0,0],pokes=[],feeds=[],drag=null,eating=null,nextChat=Date.now()+25000,nextHun=Date.now()+30000,nextDec=Date.now()+60000,nextSave=Date.now()+10000;
function say(t,ms){bubble={t:t,until:Date.now()+(ms||2800)}}
function set(e,ms){ex=e;exUntil=Date.now()+ms}
function heart(px,py){P.push({k:'h',x:px,y:py,vx:(Math.random()-.5)*1.2,vy:-1.3-Math.random(),l:1})}
function crumb(px,py){for(var i=0;i<6;i++)P.push({k:'c',x:px,y:py,vx:(Math.random()-.5)*3,vy:-1-Math.random()*2,l:1})}
function wake(){if(sleeping){sleeping=false;set('surprise',700);say('Ơ… mình dậy rồi!',1800);sy.v+=.06}lastAct=Date.now()}
var tr=document.getElementById('tr');
FOODS.forEach(function(f,i){var b=document.createElement('button');b.textContent=f[0];b.title=f[1];
b.addEventListener('pointerdown',function(e){e.preventDefault();wake();var p=pos(e);drag={i:i,x:p[0],y:p[1],x0:p[0],y0:p[1],moved:0}});tr.appendChild(b)});
function pos(e){var r=c.getBoundingClientRect();return[(e.clientX-r.left)/r.width*400,(e.clientY-r.top)/r.height*520]}
function inHead(p){return Math.abs(p[0]-200)<130&&p[1]>150&&p[1]<305}
function inBelly(p){return Math.abs(p[0]-200)<140&&p[1]>=305&&p[1]<435}
var MY=kind==2?318:girl?274:332;
function mouthD(p){return Math.hypot(p[0]-200,p[1]-MY)}
c.addEventListener('pointerdown',function(e){var p=pos(e);try{c.setPointerCapture(e.pointerId)}catch(x){}wake();lp=p;
if(inHead(p)){stroking=true;sd=0;set('happy',900)}
else if(inBelly(p)){hk='poke';var n=Date.now();pokes=pokes.filter(function(t){return n-t<4000});pokes.push(n);sy.v-=.09;sx.v+=.05;set('happy',900);beep(650+Math.random()*250,.07);buzz(10);heart(200+(Math.random()-.5)*80,300);if(pokes.length>7){say('Hihi, nhột quá!',1800)}}
else{pk=p}});
c.addEventListener('pointermove',function(e){var p=pos(e);pk=p;if(stroking&&lp){if(!inHead(p)){return}var d=Math.hypot(p[0]-lp[0],p[1]-lp[1]);sd+=d;lastAct=Date.now();rot.t=Math.max(-.12,Math.min(.12,(p[0]-200)/900));ear.t=1;sy.t=.95;sx.t=1.03;set('happy',700);
if(sd>70){sd=0;heart(p[0]+(Math.random()-.5)*40,p[1]-30);D.j=Math.min(100,D.j+1.5);beep(180+Math.random()*40,.12);buzz(6);if(D.j>90&&Math.random()<.15)say('Thích quá… cảm ơn bạn',2200)}}lp=p});
function upC(){if(stroking){stroking=false;rot.t=0;ear.t=0;sy.t=1;sx.t=1;sv()}lp=null}
c.addEventListener('pointerup',upC);c.addEventListener('pointercancel',upC);
function wmove(e){if(!drag)return;var p=pos(e);drag.moved+=Math.hypot(p[0]-drag.x,p[1]-drag.y);drag.x=p[0];drag.y=p[1];pk=p}
function wup(e){if(!drag)return;var d=drag;drag=null;var tap=d.moved<12;if(tap||mouthD([d.x,d.y])<85)feed(d.i,tap?null:[d.x,d.y]);else{P.push({k:'f',e:FOODS[d.i][0],x:d.x,y:d.y,vx:0,vy:0,l:1})}}
window.addEventListener('pointermove',wmove);window.addEventListener('pointerup',wup);
function feed(i,from){var n=Date.now();feeds=feeds.filter(function(t){return n-t<150000});
if(D.s>=96||feeds.length>=6){set('no',1600);rot.v+=.08;say('Mình no rồi, cảm ơn nha 😊',2600);beep(250,.1);return}
feeds.push(n);eating={i:i,start:n,end:n+1500,ch:0,x:from?from[0]:200,y:from?from[1]:430};set('eat',1500)}
var hk='poke';function finishEat(){hk='eat';var f=FOODS[eating.i];D.s=Math.min(100,D.s+f[2]);D.j=Math.min(100,D.j+f[3]);eating=null;set('happy',1800);say(f[4],2400);for(var k=0;k<3;k++)heart(200+(Math.random()-.5)*120,260);beep(740,.12);buzz(20);sv()}
function bar(px,py,v,ic){x.font='18px sans-serif';x.textAlign='left';x.textBaseline='middle';x.fillStyle='#1F2937';x.fillText(ic,px,py);x.fillStyle='#EFE6DE';rr(px+26,py-5,90,10,5);x.fill();x.fillStyle=v<30?'#EF4444':'#F26B1D';rr(px+26,py-5,Math.max(4,90*v/100),10,5);x.fill()}
function rr(a,b,w,h,r){x.beginPath();x.moveTo(a+r,b);x.arcTo(a+w,b,a+w,b+h,r);x.arcTo(a+w,b+h,a,b+h,r);x.arcTo(a,b+h,a,b,r);x.arcTo(a,b,a+w,b,r);x.closePath()}
function ell(a,b,rx,ry,r){x.beginPath();x.ellipse(a,b,rx,ry,r||0,0,7)}
function face(now,eatOpen){var E=sleeping?'sleep':ex,bl=now<blinkUntil;
var lx=look[0],ly=look[1];
[-48,48].forEach(function(ox){var X=ox,Y=-130;
 if(E=='happy'||E=='eat'&&eatOpen<.3||E=='full'){x.strokeStyle='#2B2230';x.lineWidth=5;x.lineCap='round';x.beginPath();x.arc(X,Y+6,15,Math.PI*1.12,Math.PI*1.88);x.stroke()}
 else if(E=='sleep'||E=='no'||bl){x.strokeStyle='#2B2230';x.lineWidth=5;x.lineCap='round';x.beginPath();if(E=='no'){x.moveTo(X-12,Y-6);x.lineTo(X+12,Y+4)}else x.arc(X,Y-6,14,Math.PI*.12,Math.PI*.88);x.stroke()}
 else{var big=E=='surprise'?1.25:1;x.fillStyle='#2B2230';ell(X+lx*.4,Y+ly*.4,14*big,18*big);x.fill();x.fillStyle='#fff';ell(X+lx*.4-5,Y+ly*.4-7,5,5.5);x.fill();ell(X+lx*.4+5,Y+ly*.4+6,2.2,2.2);x.fill()}});
x.fillStyle=(E=='happy'||E=='eat'||stroking)?'rgba(255,120,120,.6)':'rgba(255,140,140,.38)';ell(-82,-98,17,10);x.fill();ell(82,-98,17,10);x.fill();
x.strokeStyle='#2B2230';x.lineWidth=4;x.lineCap='round';
if(E=='eat'){var o=Math.abs(Math.sin(now/110))*.9;x.fillStyle='#8A3B4A';ell(0,-92,13,4+o*14);x.fill()}
else if(E=='surprise'||drag&&mouthD([drag.x,drag.y])<110){x.fillStyle='#8A3B4A';ell(0,-92,12,15);x.fill();if(drag){x.fillStyle='#F28B9B';ell(0,-86,8,6);x.fill()}}
else if(E=='sleep'){x.beginPath();x.arc(0,-98,5,0,Math.PI);x.stroke()}
else if(E=='no'){x.beginPath();x.moveTo(-9,-96);x.lineTo(9,-96);x.stroke()}
else if(E=='happy'){x.fillStyle='#8A3B4A';x.beginPath();x.arc(0,-102,14,.05*Math.PI,.95*Math.PI);x.closePath();x.fill()}
else{x.beginPath();x.arc(-6,-100,6,.1*Math.PI,.9*Math.PI);x.stroke();x.beginPath();x.arc(6,-100,6,.1*Math.PI,.9*Math.PI);x.stroke()}}
function drawChar(now){if(kind==2){drawImg(now);return}if(girl){drawGirl(now);return}var br=Math.sin(now/(sleeping?900:620))*(sleeping?.022:.014),ew=Math.sin(now/300)*.04;
x.save();x.translate(200,432+hop.p);x.rotate(rot.p);x.scale(sx.p-br*.5,sy.p+br);
var body='#FFE6D0',line='#E4BE9C',pink='#FFB8B8';x.lineJoin='round';
[-1,1].forEach(function(s){x.save();x.translate(s*88,-188);x.rotate(s*(.35+ear.p*.3+ew*s));x.fillStyle=body;x.strokeStyle=line;x.lineWidth=4;x.beginPath();x.moveTo(-26,10);x.quadraticCurveTo(0,-70,28,8);x.closePath();x.fill();x.stroke();x.fillStyle=pink;x.beginPath();x.moveTo(-14,4);x.quadraticCurveTo(0,-38,15,3);x.closePath();x.fill();x.restore()});
[-1,1].forEach(function(s){x.fillStyle=body;x.strokeStyle=line;x.lineWidth=4;ell(s*56,-10,34,19);x.fill();x.stroke()});
var wave=(ex=='happy'||stroking)?Math.sin(now/110)*.25:0;
[-1,1].forEach(function(s){x.save();x.translate(s*122,-78);x.rotate(s*(.5+wave*s)+(eating?-s*.6:0));x.fillStyle=body;x.strokeStyle=line;x.lineWidth=4;ell(0,0,19,30);x.fill();x.stroke();x.restore()});
x.fillStyle=body;x.strokeStyle=line;x.lineWidth=5;ell(0,-112,128,108);x.fill();x.stroke();
x.fillStyle='#FFF6EC';ell(0,-70,66,48);x.fill();
x.save();x.translate(0,-218);x.rotate(Math.sin(now/700)*.08+rot.p*-1.5);x.strokeStyle='#5FA866';x.lineWidth=5;x.lineCap='round';x.beginPath();x.moveTo(0,4);x.quadraticCurveTo(4,-22,8,-38);x.stroke();x.fillStyle='#7BC47F';x.strokeStyle='#5FA866';x.lineWidth=3;
x.save();x.translate(8,-40);x.rotate(-.9);ell(-14,0,18,9);x.fill();x.stroke();x.restore();x.save();x.translate(8,-40);x.rotate(.5);ell(16,0,18,9);x.fill();x.stroke();x.restore();x.restore();
var eo=eating?Math.abs(Math.sin((now-eating.start)/110)):0;face(now,eo);x.restore()}

function drawGirl(now){var br=Math.sin(now/(sleeping?900:620))*(sleeping?.02:.012),E=sleeping?'sleep':ex,bl=now<blinkUntil,lx=look[0],ly=look[1];
var HR='#5CB8E6',HD='#3F95CC',HL='#A6DEF7',SK='#FFE3D3',SL='#E8B7A0',SW='#F4C451',SWL='#D4A23A',PN='#4A5A8C';
x.save();x.translate(200,432+hop.p);x.rotate(rot.p);x.scale(sx.p-br*.5,sy.p+br);x.lineJoin='round';x.lineCap='round';
// tóc sau
x.fillStyle=HD;x.beginPath();x.moveTo(-122,-215);x.quadraticCurveTo(-125,-320,0,-318);x.quadraticCurveTo(125,-320,122,-215);x.lineTo(124,-140);x.quadraticCurveTo(122,-118,98,-120);x.lineTo(-98,-120);x.quadraticCurveTo(-122,-118,-124,-140);x.closePath();x.fill();
// chân và giày
[-1,1].forEach(function(s){x.fillStyle=SK;x.strokeStyle=SL;x.lineWidth=3;x.fillRect(s*26-9,-34,18,24);x.fillStyle='#fff';x.strokeStyle='#C8CEDD';x.lineWidth=3;ell(s*28,-8,25,12);x.fill();x.stroke()});
// quần
x.fillStyle=PN;x.beginPath();x.moveTo(-50,-60);x.lineTo(50,-60);x.lineTo(54,-34);x.lineTo(2,-34);x.lineTo(-2,-34);x.lineTo(-54,-34);x.closePath();x.fill();
// thân áo
x.fillStyle=SW;x.strokeStyle=SWL;x.lineWidth=4;x.beginPath();x.moveTo(-48,-118);x.quadraticCurveTo(-56,-120,-56,-100);x.lineTo(-54,-56);x.quadraticCurveTo(0,-46,54,-56);x.lineTo(56,-100);x.quadraticCurveTo(56,-120,48,-118);x.quadraticCurveTo(0,-108,-48,-118);x.closePath();x.fill();x.stroke();
x.strokeStyle=SWL;x.lineWidth=3;x.beginPath();x.moveTo(0,-110);x.lineTo(0,-60);x.stroke();
// tay
var wave=(ex=='happy'||stroking)?Math.sin(now/110)*.3:0;
[-1,1].forEach(function(s){x.save();x.translate(s*54,-108);x.rotate(s*(.28+wave*s)+(eating?-s*1.1:0));x.fillStyle=SW;x.strokeStyle=SWL;x.lineWidth=4;x.beginPath();x.ellipse(s*8,22,15,30,0,0,7);x.fill();x.stroke();x.fillStyle=SK;x.strokeStyle=SL;x.lineWidth=3;ell(s*8,52,12,11);x.fill();x.stroke();x.restore()});
// đầu
x.fillStyle=SK;x.strokeStyle=SL;x.lineWidth=4;ell(0,-198,108,94);x.fill();x.stroke();
// mắt
[-1,1].forEach(function(s){var X=s*40,Y=-190;
 if(E=='happy'||(E=='eat'&&Math.abs(Math.sin((now-eating.start)/110))<.3)){x.strokeStyle='#5A3A22';x.lineWidth=5;x.beginPath();x.arc(X,Y+8,15,Math.PI*1.1,Math.PI*1.9);x.stroke()}
 else if(E=='sleep'||bl){x.strokeStyle='#5A3A22';x.lineWidth=4.5;x.beginPath();x.arc(X,Y-4,14,Math.PI*.12,Math.PI*.88);x.stroke()}
 else if(E=='no'){x.strokeStyle='#5A3A22';x.lineWidth=5;x.beginPath();x.moveTo(X-13,Y-6);x.lineTo(X+11,Y+4);x.moveTo(X-13,Y+10);x.lineTo(X+11,Y+4);x.stroke()}
 else{var big=E=='surprise'?1.2:1,ox=lx*.35,oy=ly*.35;x.fillStyle='#5A3A22';ell(X,Y,19*big,24*big);x.fill();x.fillStyle='#E8A93A';ell(X+ox,Y+3+oy,15*big,19*big);x.fill();x.fillStyle='#F7CF7A';ell(X+ox,Y+12+oy,12*big,8*big);x.fill();x.fillStyle='#3A2412';ell(X+ox,Y+2+oy,8*big,11*big);x.fill();x.fillStyle='#fff';ell(X+ox-6,Y-7+oy,6,6.5);x.fill();ell(X+ox+6,Y+9+oy,2.6,2.6);x.fill()}});
x.fillStyle=(E=='happy'||E=='eat'||stroking)?'rgba(255,110,120,.6)':'rgba(255,130,140,.38)';ell(-66,-163,17,10);x.fill();ell(66,-163,17,10);x.fill();
// miệng
x.strokeStyle='#8A4A4A';x.lineWidth=3.5;
if(E=='eat'){var o=Math.abs(Math.sin((now-eating.start)/110))*.9;x.fillStyle='#A04755';ell(0,-156,12,4+o*13);x.fill()}
else if(E=='surprise'||drag&&mouthD([drag.x,drag.y])<110){x.fillStyle='#A04755';ell(0,-154,11,14);x.fill();x.fillStyle='#F28B9B';ell(0,-148,7,5);x.fill()}
else if(E=='sleep'){x.beginPath();x.arc(0,-160,4,0,Math.PI);x.stroke()}
else if(E=='no'){x.beginPath();x.moveTo(-8,-158);x.lineTo(8,-158);x.stroke()}
else if(E=='happy'){x.fillStyle='#A04755';x.beginPath();x.arc(0,-162,13,.05*Math.PI,.95*Math.PI);x.closePath();x.fill();x.fillStyle='#fff';x.fillRect(-6,-162,12,4)}
else{x.beginPath();x.arc(-5,-160,5,.1*Math.PI,.9*Math.PI);x.stroke();x.beginPath();x.arc(5,-160,5,.1*Math.PI,.9*Math.PI);x.stroke()}
// mái và tóc mai
var sw=Math.sin(now/900)*3+ear.p*4;
x.fillStyle=HR;x.strokeStyle=HD;x.lineWidth=3;
x.beginPath();x.moveTo(-118,-205);x.bezierCurveTo(-122,-330,122,-330,118,-205);x.lineTo(104,-222);x.lineTo(84,-236+sw*.3);x.lineTo(66,-206);x.lineTo(40,-240);x.lineTo(16,-204);x.lineTo(-8,-240);x.lineTo(-30,-204);x.lineTo(-56,-238);x.lineTo(-74,-206);x.lineTo(-98,-232);x.closePath();x.fill();x.stroke();
[-1,1].forEach(function(s){x.beginPath();x.moveTo(s*108,-222);x.quadraticCurveTo(s*132,-190,s*118,-134+sw*s);x.quadraticCurveTo(s*100,-128,s*92,-150);x.quadraticCurveTo(s*96,-190,s*100,-218);x.closePath();x.fill();x.stroke()});
x.strokeStyle=HL;x.lineWidth=5;x.beginPath();x.arc(-30,-262,52,Math.PI*1.08,Math.PI*1.4);x.stroke();
// ahoge
x.strokeStyle=HR;x.lineWidth=7;x.beginPath();x.moveTo(-2,-296);x.quadraticCurveTo(-20,-336+sw,6,-338);x.stroke();
// kẹp tóc
x.save();x.translate(-80,-250);x.rotate(-.5);x.fillStyle='#fff';x.strokeStyle='#B8C6E0';x.lineWidth=2;rr(-16,-5,32,10,4);x.fill();x.stroke();x.restore();
x.save();x.translate(-70,-236);x.rotate(.5);x.fillStyle='#FFD86B';x.strokeStyle='#D4A23A';x.lineWidth=2;rr(-16,-5,32,10,4);x.fill();x.stroke();x.restore();
x.restore()}

var night=false,IM={},tk0=0;try{var hr=new Date().getHours();night=hr>=21||hr<5;var nv=localStorage.getItem('tau_petnight');if(nv!==null)night=nv=='1'}catch(e){}
var KM={w:{idle:'w31',idle2:'w22',idle3:'w23',pat:['w02','w03','w32'],poke:'w10',poke2:'w11',surprise:'w01',eat:'w30',love:'w33',no:'w20',sleep:'w13',hungry:'w21'},d:{idle:'d00',idle2:'d22',idle3:'d20',pat:['d02','d03'],poke:'d12',poke2:'d22',surprise:'d01',eat:'d21',love:'d23',no:'d11',sleep:'d10',hungry:'d13'}};
function im(k){if(!IM[k]){var i=new Image();i.src=PETIMG[k];IM[k]=i}return IM[k]}
function tileKey(now){var m=KM[night?'d':'w'];
if(sleeping)return m.sleep;if(eating)return m.eat;if(stroking)return m.pat[Math.floor(now/900)%m.pat.length];
if(ex=='no')return m.no;if(ex=='surprise')return m.surprise;
if(ex=='happy')return hk=='eat'?m.love:(pokes.length>5?m.poke2:m.poke);
if(D.s<30)return m.hungry;
var ph=Math.floor(now/6000)%4;return ph==1?m.idle2:ph==3&&D.j>60?m.idle3:m.idle}
function drawImg(now){var k=tileKey(now),i=im(k);if(!i.complete||!i.naturalWidth)return;var br=Math.sin(now/(sleeping?900:620))*(sleeping?.018:.01),W=330,H=W*i.naturalHeight/i.naturalWidth;
x.save();x.translate(200,442+hop.p);x.rotate(rot.p*.35);x.scale(sx.p-br*.5,sy.p+br);x.shadowColor='rgba(120,90,60,.25)';x.shadowBlur=14;x.drawImage(i,-W/2,-H,W,H);x.restore()}
var nb=document.createElement('button');nb.className='b w';nb.style.cssText='width:100%;margin-top:6px';function nt(){nb.textContent=night?'☀️ Chuyển sang cảnh ban ngày':'🌙 Chuyển sang cảnh ban đêm'}nt();nb.onclick=function(){night=!night;try{localStorage.setItem('tau_petnight',night?'1':'0')}catch(e){}nt()};if(kind==2)c.parentNode.appendChild(nb);
function bg(){x.fillStyle='#FFF4EA';x.fillRect(0,0,400,520);var g=x.createRadialGradient(200,440,10,200,440,190);g.addColorStop(0,'#F6DFCB');g.addColorStop(1,'rgba(246,223,203,0)');x.fillStyle=g;ell(200,442,190,34);x.fill()}
function bubbleDraw(){if(!bubble)return;var n=Date.now();if(n>bubble.until){bubble=null;return}var a=Math.min(1,(bubble.until-n)/300);x.globalAlpha=a;x.font='bold 16px sans-serif';x.textAlign='center';x.textBaseline='middle';var w=x.measureText(bubble.t).width+28;x.fillStyle='#fff';x.strokeStyle='#E4BE9C';x.lineWidth=2;rr(200-w/2,58,w,40,16);x.fill();x.stroke();x.beginPath();x.moveTo(190,97);x.lineTo(200,112);x.lineTo(212,97);x.closePath();x.fill();x.fillStyle='#1F2937';x.fillText(bubble.t,200,79);x.globalAlpha=1}
function parts(){P.forEach(function(p){p.x+=p.vx;p.y+=p.vy;if(p.k=='h'){p.vy*=.99;p.l-=.012;x.globalAlpha=Math.max(0,p.l);x.fillStyle='#F472B6';x.font='26px sans-serif';x.textAlign='center';x.fillText('♥',p.x,p.y)}
else if(p.k=='c'){p.vy+=.18;p.l-=.03;x.globalAlpha=Math.max(0,p.l);x.fillStyle='#D9A15E';ell(p.x,p.y,3,3);x.fill()}
else if(p.k=='z'){p.l-=.01;p.x+=Math.sin(p.l*8)*.3;x.globalAlpha=Math.max(0,p.l);x.fillStyle='#6B7280';x.font='bold '+(16+(1-p.l)*14)+'px sans-serif';x.textAlign='center';x.fillText('Z',p.x,p.y)}
else if(p.k=='f'){p.vy+=.5;p.l-=.02;x.globalAlpha=Math.max(0,p.l);x.font='44px serif';x.textAlign='center';x.textBaseline='middle';x.fillText(p.e,p.x,Math.min(p.y,470));if(p.y>470)p.vy=0}
x.globalAlpha=1});P=P.filter(function(p){return p.l>0})}
loop(function(){var now=Date.now();
[sy,sx,rot,hop,ear].forEach(st);
if(!stroking){sy.t=1;sx.t=1}
if(now>exUntil&&!eating)ex='idle';
if(!sleeping&&now-lastAct>50000&&!eating){sleeping=true;say('Zzz…',1500)}
if(sleeping&&Math.random()<.012)P.push({k:'z',x:270,y:230,vx:.3,vy:-.5,l:1});
if(!sleeping&&now>nextBlink){blinkUntil=now+130;nextBlink=now+2200+Math.random()*3500}
var tgt=pk&&!sleeping?[Math.max(-7,Math.min(7,(pk[0]-200)/22)),Math.max(-5,Math.min(5,(pk[1]-250)/30))]:[0,0];look[0]+=(tgt[0]-look[0])*.12;look[1]+=(tgt[1]-look[1])*.12;
if(eating){var el=now-eating.start;if(Math.floor(el/300)>eating.ch){eating.ch++;sy.v-=.05;sx.v+=.03;beep(260+Math.random()*120,.04);crumb(200,MY+13);buzz(5)}if(now>=eating.end)finishEat()}
if(!sleeping&&!eating&&!stroking&&!drag){if(now>nextChat){nextChat=now+30000+Math.random()*25000;say(['Hôm nay bạn học được gì rồi?','Nhớ uống nước nhé 💧','Nghỉ mắt một chút nhé','Có bạn ở đây vui ghê ♥','Duỗi vai một cái nào~'][Math.random()*5|0],3200)}
 if(D.s<35&&now>nextHun){nextHun=now+30000;say('Bụng réo rồi…',2600);beep(120,.25);sy.v-=.04}}
if(now>nextDec){nextDec=now+60000;D.s=Math.max(15,D.s-.8);D.j=Math.max(20,D.j-.5)}
if(now>nextSave){nextSave=now+10000;sv()}
bg();
bar(14,22,D.s,'🍙');bar(150,22,D.j,'💗');
drawChar(now);
if(eating){var f=FOODS[eating.i],t=Math.min(1,(now-eating.start)/350),fx=eating.x+(200-eating.x)*t,fy=eating.y+(MY-eating.y)*t,shr=Math.max(.2,1-(now-eating.start)/1500);if(t<1||now-eating.start<1500){x.font=(44*shr)+'px serif';x.textAlign='center';x.textBaseline='middle';x.fillText(f[0],fx,fy)}}
if(drag){x.font='48px serif';x.textAlign='center';x.textBaseline='middle';x.fillText(FOODS[drag.i][0],drag.x,drag.y-6)}
if(stroking&&lp&&kind!=2){x.save();x.translate(lp[0],lp[1]-24);x.rotate(Math.sin(Date.now()/110)*.2);x.font='54px sans-serif';x.textAlign='center';x.textBaseline='middle';x.fillText('✋',0,0);x.restore()}
parts();bubbleDraw()});
var _s=stop;stop=function(){_s&&_s();sv();window.removeEventListener('pointermove',wmove);window.removeEventListener('pointerup',wup)};
say(kind==2?'Chào bạn, hôm nay thế nào? 💙':girl?'Chào cậu! Mình là Lam 💙':'Chào bạn! Mình là Mầm 🌱',3000)}
