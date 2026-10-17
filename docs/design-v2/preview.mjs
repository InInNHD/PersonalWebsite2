// 仅提供此样稿目录中的静态文件，不连接数据库。
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, resolve, relative, extname, isAbsolute } from 'node:path'
const root = dirname(fileURLToPath(import.meta.url))
const types = { '.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.svg':'image/svg+xml' }
createServer(async (req, res) => {
  try {
    if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405); res.end(); return }
    const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname)
    const target = resolve(root, pathname === '/' ? 'index.html' : '.' + pathname)
    const pathFromRoot = relative(root, target)
    if (pathFromRoot.startsWith('..') || isAbsolute(pathFromRoot) || !types[extname(target)]) { res.writeHead(404); res.end(); return }
    const content = await readFile(target)
    res.writeHead(200, { 'Content-Type':types[extname(target)], 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff' })
    res.end(req.method === 'HEAD' ? undefined : content)
  } catch { res.writeHead(404); res.end('Not found') }
}).listen(5177, '127.0.0.1', () => console.log('V2 design preview: http://127.0.0.1:5177'))
