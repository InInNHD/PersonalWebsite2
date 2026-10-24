import test from 'node:test'
import assert from 'node:assert/strict'
import {articles,topics,filterArticles,pageItems,highlight} from './data.mjs'
test('正文关键词命中，标签与栏目组合不放宽条件',()=>{
  assert.deepEqual(filterArticles({q:'事务'}).map(a=>a.id),['request'])
  assert.equal(filterArticles({q:'事务',tag:'Vue'}).length,0)
  assert.equal(filterArticles({kind:'日记'}).length,1)
})
test('分页边界、主题顺序与每篇链接目标有效',()=>{
  assert.equal(pageItems(articles,99).page,2)
  assert.equal(pageItems(articles,-2).page,1)
  assert.deepEqual(pageItems([],0).items,[])
  for(const t of topics)assert.ok(t.ids.every(id=>articles.some(a=>a.id===id)))
  assert.deepEqual(filterArticles({topic:'web'}).map(a=>a.id),['request','restore','layout'])
})
test('查询与高亮按文本处理，不执行HTML且不解释正则',()=>{
  assert.equal(highlight('<img src=x onerror=alert(1)>','img'),'&lt;<mark>img</mark> src=x onerror=alert(1)&gt;')
  assert.equal(highlight('a+b','+'),'a<mark>+</mark>b')
  assert.equal(filterArticles({q:'<script>'}).length,0)
})
