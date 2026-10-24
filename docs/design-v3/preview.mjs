// 仅监听本机，只允许样稿运行所需文件。不会提供仓库、私人草稿或配置。
import {createServer} from 'node:http'
import {readFile} from 'node:fs/promises'
const files={'/':'index.html','/index.html':'index.html','/styles.css':'styles.css','/prototype.js':'prototype.js','/data.mjs':'data.mjs','/projects.json':'projects.json'}
const types={html:'text/html',css:'text/css',js:'text/javascript',mjs:'text/javascript',json:'application/json'}
const server=createServer(async(req,res)=>{try{
  if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return}
  const name=files[new URL(req.url,'http://127.0.0.1').pathname]
  if(!name){res.writeHead(404);res.end('Not found');return}
  const content=await readFile(new URL(name,import.meta.url))
  res.writeHead(200,{'Content-Type':types[name.split('.').at(-1)]+'; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'})
  res.end(req.method==='HEAD'?undefined:content)
}catch{res.writeHead(500);res.end('Preview file unavailable')}})
server.on('error',error=>{console.error('无法启动样稿服务：'+error.code);process.exitCode=1})
server.listen(5188,'127.0.0.1',()=>console.log('V3 design preview: http://127.0.0.1:5188'))
