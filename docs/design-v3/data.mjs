// 这八篇是新写的界面演示资料，不读取或复制个人数据库草稿正文。
// 日期和阅读时长只服务于版式评审，不作为用户真实发表记录。
export const articles = [
  { id:'request', title:'一条请求，怎样抵达数据库', kind:'笔记', tag:'Java', topic:'web', date:'2026-10-21', minutes:6, art:'flow', summary:'从浏览器中的一次点击出发，串起路由、接口和数据查询。', body:'浏览器发送请求，控制器校验参数，数据库返回结果。事务让一组修改共同成功或失败。', section:'先画出数据的路径' },
  { id:'restore', title:'一份备份，什么时候才算可靠', kind:'笔记', tag:'MySQL', topic:'web', date:'2026-10-19', minutes:8, art:'database', summary:'把导出文件重新还原成能工作的系统，才是恢复演练的终点。', body:'恢复演练使用独立数据库，比较结构、记录和图片校验值。失败时保留原文件与证据。', section:'从恢复目标开始' },
  { id:'state', title:'用状态图说清楚一次业务流转', kind:'笔记', tag:'Java', topic:'systems', date:'2026-10-17', minutes:7, art:'state', summary:'先理解状态和允许的变化，再决定接口应该做什么。', body:'幂等请求重复执行应得到一致的业务结果。状态转换需要明确前置条件。', section:'先定义合法的变化' },
  { id:'notes', title:'把零散问题，写成可以重用的笔记', kind:'心得', tag:'写作', topic:'writing', date:'2026-10-15', minutes:4, art:'paper', summary:'留下问题、尝试和判断依据，让未来的自己能接着往下走。', body:'笔记不必记录所有细节，但要保留背景、复现步骤和得出结论的证据。', section:'给未来的自己留下背景' },
  { id:'layout', title:'页面变窄之后，什么应该先留下', kind:'笔记', tag:'Vue', topic:'web', date:'2026-10-13', minutes:5, art:'layout', summary:'从阅读顺序出发安排响应式布局，先保证内容与操作。', body:'响应式布局需要照顾长标题、表格和代码块。键盘焦点与触摸入口同样重要。', section:'从内容顺序出发' },
  { id:'walk', title:'把屏幕关掉，出去走一小段', kind:'日记', tag:'日常', topic:'', date:'2026-10-11', minutes:3, art:'landscape', summary:'一段用于观察日记卡片排版的短文：留一点空白，也是一种记录。', body:'这是一篇虚构的日记排版示例，不描述作者的真实行程或生活经历。', section:'给记录留一点空白' },
  { id:'errors', title:'当一次请求没有按预期完成：从错误提示、重试入口到保留输入的界面细节', kind:'笔记', tag:'Vue', topic:'web', date:'2026-10-09', minutes:6, art:null, summary:'用一条较长的标题检验卡片与手机布局，并梳理失败后的下一步。', body:'网络超时、权限失效和参数错误需要不同的反馈。不要清空用户还没有保存的输入。', section:'先保留已经付出的工作' },
  { id:'release', title:'给一次版本交付留一张检查表', kind:'心得', tag:'交付', topic:'writing', date:'2026-10-07', minutes:5, art:'paper', summary:'版本、数据、构建产物和恢复步骤，应该能够相互对应。', body:'发布前核对版本与校验值。未验证的设备保留为限制，不用一次构建成功替代验收。', section:'让版本与证据对应' },
]
export const topics = [
  { id:'web', name:'从请求到交付', description:'把前后端、数据和恢复连成一条路径。', ids:['request','layout','restore'] },
  { id:'systems', name:'读懂业务状态', description:'从状态和约束入手，理解系统行为。', ids:['state'] },
  { id:'writing', name:'写下问题与判断', description:'让一次尝试变成下一次可用的经验。', ids:['notes','release'] },
]
export function filterArticles({q='',kind='',tag='',topic=''}={}) {
  const word=q.trim().toLocaleLowerCase()
  return articles.filter(a=>(!kind||a.kind===kind)&&(!tag||a.tag===tag)&&(!topic||topics.find(t=>t.id===topic)?.ids.includes(a.id))&&(!word||[a.title,a.summary,a.body].some(s=>s.toLocaleLowerCase().includes(word))))
    .sort((a,b)=>Number(Boolean(word)&&b.title.toLocaleLowerCase().includes(word))-Number(Boolean(word)&&a.title.toLocaleLowerCase().includes(word)))
}
export function escapeHtml(value) { return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])) }
export function highlight(text,q) {
  if(!q?.trim()) return escapeHtml(text)
  const value=String(text), word=q.trim(), index=value.toLocaleLowerCase().indexOf(word.toLocaleLowerCase())
  return index<0?escapeHtml(value):escapeHtml(value.slice(0,index))+'<mark>'+escapeHtml(value.slice(index,index+word.length))+'</mark>'+escapeHtml(value.slice(index+word.length))
}
export function pageItems(items,page,size=4) { const pages=Math.max(1,Math.ceil(items.length/size));const current=Math.max(1,Math.min(pages,Number.parseInt(page,10)||1));return {items:items.slice((current-1)*size,current*size),pages,page:current} }
