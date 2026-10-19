import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'

// 只加载当前写作常用的语言，不自动猜测语言，也不引入整套语言包。
for (const [name, grammar] of Object.entries({ java, javascript, typescript, xml, css, sql, json, bash, python })) {
    hljs.registerLanguage(name, grammar)
}


// 保持关闭原始HTML。文章文字、代码、目录名称都不能作为脚本执行。
const markdown = new MarkdownIt({ html: false, linkify: true })

// fence 和缩进代码块共用工具栏。代码原文放入返回数据，不写进HTML属性。
function renderCode(tokens, index, options, env) {
    const token = tokens[index]
    const requested = token.info.trim().split(/\s+/)[0].toLowerCase()
    const language = hljs.getLanguage(requested) ? requested : ''
    const label = markdown.utils.escapeHtml((requested || 'text').slice(0, 32))
    const number = env.codeBlocks.push(token.content) - 1
    // 未知语言和解析失败都回退为转义后的纯文本，正文仍然可读。
    let html = markdown.utils.escapeHtml(token.content)
    if (language) {
        try { html = hljs.highlight(token.content, { language, ignoreIllegals: true }).value }
        catch { /* 保留上面的纯文本结果。 */ }
    }
    return `<section class="reading-code" aria-label="代码块"><div class="reading-code-bar">`
        + `<span>${label}</span><button type="button" data-code-index="${number}" aria-label="复制第${number + 1}段代码">复制</button>`
        + `</div><pre><code class="hljs">${html}</code></pre></section>\n`
}
markdown.renderer.rules.fence = renderCode
markdown.renderer.rules.code_block = renderCode

const defaultImage = markdown.renderer.rules.image
markdown.renderer.rules.image = (tokens, index, options, env, renderer) => {
    // 已有链接的图片保留原链接行为，避免生成嵌套交互控件。
    let linkDepth = 0
    for (let i = 0; i < index; i += 1) {
        if (tokens[i].type === 'link_open') linkDepth += 1
        if (tokens[i].type === 'link_close') linkDepth -= 1
    }
    tokens[index].attrSet('referrerpolicy', 'no-referrer')
    const image = defaultImage(tokens, index, options, env, renderer)
    if (linkDepth > 0) return image
    const alt = renderer.renderInlineAsText(tokens[index].children ?? [], options, env)
    const label = markdown.utils.escapeHtml(`查看大图：${alt || '正文图片'}`)
    return `<button type="button" class="reading-image" data-reading-image aria-label="${label}">${image}</button>`
}

function inlineText(token) {
    return (token.children ?? []).map(child => {
        if (['text', 'code_inline', 'image'].includes(child.type)) return child.content
        if (['softbreak', 'hardbreak'].includes(child.type)) return ' '
        return ''
    }).join('')
}

export function renderArticle(source = '') {
    const tokens = markdown.parse(source, {})
    const used = new Set()
    const toc = []
    for (let index = 0; index < tokens.length; index += 1) {
        if (tokens[index].type !== 'heading_open') continue
        const title = inlineText(tokens[index + 1]) || '未命名章节'
        // 标题文字决定基础ID；重复标题逐个加后缀，所有层级共同去重。
        // read-前缀避免与站点外框和表单ID冲突。
        const slug = title.normalize('NFKC').toLowerCase()
            .replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '').slice(0, 64)
        const base = `read-${slug || 'section'}`
        let id = base
        let suffix = 2
        while (used.has(id)) id = `${base}-${suffix++}`
        used.add(id)
        tokens[index].attrSet('id', id)
        const level = Number(tokens[index].tag.slice(1))
        if (level === 2 || level === 3) toc.push({ id, title, level })
    }

    // 只统计可读文字和代码，排除Markdown链接地址等标记。
    // 这是估算：中文每分钟300字，其他语言每分钟200词，至少1分钟。
    const text = tokens.map(token => token.type === 'inline' ? inlineText(token)
        : ['fence', 'code_block'].includes(token.type) ? token.content : '').join('\n')
    const chinese = (text.match(/\p{Script=Han}/gu) ?? []).length
    const words = (text.replace(/\p{Script=Han}/gu, ' ').match(/[\p{L}\p{N}]+/gu) ?? []).length
    const env = { codeBlocks: [] }
    const html = markdown.renderer.render(tokens, markdown.options, env)
    return {
        html,
        codeBlocks: env.codeBlocks,
        toc,
        minutes: Math.max(1, Math.ceil(chinese / 300 + words / 200)),
    }
}