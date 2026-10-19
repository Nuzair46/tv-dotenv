// Offline only. Preserve shader precision through a packed 16-bit readback;
// do not run this renderer on the TV or quantize through browser screenshots.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const renderDir=path.join(root,'build/wallpaper');
fs.mkdirSync(renderDir,{recursive:true});
if(process.argv.includes('--close-renderer')){
 const info=await(await fetch('http://127.0.0.1:9227/json/version')).json();
 const connection=new WebSocket(info.webSocketDebuggerUrl);await once(connection,'open');
 connection.send(JSON.stringify({id:1,method:'Browser.close'}));await once(connection,'close');
 console.log('Temporary renderer closed');process.exit(0);
}
const output=path.join(renderDir,'neural.mp4');
const preview=process.argv.includes('--preview');
if(!preview&&fs.existsSync(output))throw Error('Refusing to overwrite final render');
const vertex=fs.readFileSync(path.join(root,'assets/wallpaper/vertex.glsl'),'utf8');
let fragment=fs.readFileSync(path.join(root,'assets/wallpaper/fragment.glsl'),'utf8').replace('precision mediump float;','precision highp float;');
fragment=fragment.replace('gl_FragColor = vec4(finalColor, glow);',`
    float srgb=clamp((finalColor.r*glow+(8.0/255.0)*(1.0-glow))*(0.65+0.35*smoothstep(0.0,0.6,vUv.x)),0.0,1.0);
    // Browser artwork is sRGB. Convert its transfer function, not only RGB->YUV.
    float linear=srgb<=0.04045?srgb/12.92:pow((srgb+0.055)/1.055,2.4);
    float rec709=linear<0.018?4.5*linear:1.099*pow(linear,0.45)-0.099;
    // Static interleaved-gradient dither: one 8-bit luma code peak-to-peak,
    // useful even if a hardware output path reduces Main10 to 8-bit.
    float ditherValue=fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(0.06711056,0.00583715))))-0.5;
    float encodedWord=floor(clamp(rec709+ditherValue/219.0,0.0,1.0)*65535.0+0.5);
    gl_FragColor=vec4(mod(encodedWord,256.0)/255.0,floor(encodedWord/256.0)/255.0,0.0,1.0);
`);
const setup=`(()=>{
 const gpu=document.createElement('canvas');gpu.width=1920;gpu.height=1080;
 const gl=gpu.getContext('webgl',{alpha:false,antialias:false,preserveDrawingBuffer:true});if(!gl)throw Error('No WebGL');
 const compile=(src,type)=>{const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
 const p=gl.createProgram();gl.attachShader(p,compile(${JSON.stringify(vertex)},gl.VERTEX_SHADER));gl.attachShader(p,compile(${JSON.stringify(fragment)},gl.FRAGMENT_SHADER));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));gl.useProgram(p);
 const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);const a=gl.getAttribLocation(p,'a_position');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
 gl.disable(gl.BLEND);gl.disable(gl.DITHER);gl.viewport(0,0,1920,1080);
 const loc=n=>gl.getUniformLocation(p,n),pixels=new Uint8Array(1920*1080*4),gray=new Uint8Array(1920*1080*2);
 window.preciseFrame=t=>{
  gl.uniform1f(loc('u_time'),3.25+1.0*(1.0-Math.cos(2*Math.PI*t/24)));
  gl.uniform1f(loc('u_ratio'),1920/1080);gl.uniform2f(loc('u_pointer_position'),.5,.5);
  gl.uniform1f(loc('u_pointer_strength'),.68);gl.uniform1f(loc('u_intensity'),.90);gl.uniform1f(loc('u_detail'),.8);
  for(const n of ['u_color_a','u_color_b','u_accent_color'])gl.uniform3f(loc(n),1,1,1);gl.uniform1f(loc('u_accent_strength'),0);
  gl.drawArrays(gl.TRIANGLES,0,6);gl.readPixels(0,0,1920,1080,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
  for(let y=0;y<1080;y++)for(let x=0;x<1920;x++){const s=((1079-y)*1920+x)*4,d=(y*1920+x)*2;gray[d]=pixels[s];gray[d+1]=pixels[s+1];}
  let parts=[];for(let i=0;i<gray.length;i+=32768)parts.push(String.fromCharCode(...gray.subarray(i,i+32768)));return btoa(parts.join(''));
 };return gl.getParameter(gl.RENDERER);
})()`;
const pages=await(await fetch('http://127.0.0.1:9227/json/list')).json();
const ws=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);await once(ws,'open');
let seq=0;const pending=new Map();
ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(pending.has(m.id)){const [res,rej]=pending.get(m.id);pending.delete(m.id);m.error?rej(Error(JSON.stringify(m.error))):res(m.result);}});
async function evaluate(expression){const result=await new Promise((res,rej)=>{let id=++seq;pending.set(id,[res,rej]);ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true}}));});if(result.exceptionDetails)throw Error(JSON.stringify(result.exceptionDetails));return result.result.value;}
try{
 console.log('Renderer:',await evaluate(setup));
 const first=Buffer.from(await evaluate('preciseFrame(0)'),'base64');
 if(first.length!==1920*1080*2)throw Error('Invalid frame');
 fs.writeFileSync(path.join(renderDir,'precise-first.gray16le'),first);
 if(preview){console.log('16-bit preview saved');}
 else{
  const ff=spawn('ffmpeg',['-hide_banner','-loglevel','warning','-f','rawvideo','-pixel_format','gray16le','-video_size','1920x1080','-framerate','30','-color_range','pc','-i','-',
   '-vf','scale=in_range=pc:out_range=tv:out_color_matrix=bt709,format=yuv420p10le','-an','-c:v','libx265','-preset','medium','-crf','10',
   '-x265-params','colorprim=bt709:transfer=bt709:colormatrix=bt709:range=limited:pools=4:aq-mode=3:cutree=0:sao=0:log-level=error',
   '-tag:v','hvc1','-color_range','tv','-colorspace','bt709','-color_primaries','bt709','-color_trc','bt709','-movflags','+faststart',output],{stdio:['pipe','inherit','inherit']});
  const done=once(ff,'exit');ff.stdin.on('error',e=>console.error(e.message));
  for(let n=0;n<720;n++){
   const frame=n===0?first:Buffer.from(await evaluate('preciseFrame('+n/30+')'),'base64');
   if(!ff.stdin.write(frame))await once(ff.stdin,'drain');
   if(n%30===0)console.log('Rendered',n,'/ 720');
  }
  ff.stdin.end();const [code]=await done;if(code!==0)throw Error('Encoder exit '+code);console.log(output);
 }
}finally{ws.close();}
