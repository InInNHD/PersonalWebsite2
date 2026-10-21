import { readFile, writeFile, rename, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { siteProfile } from '../src/siteProfile.js'

// 只在本机执行。可选GITHUB_TOKEN只由同步进程读取，不写入JSON或前端代码。
// 一次完整成功才替换快照；任一请求失败时保留原文件，避免把失败显示成0个项目。
export async function collectGitHub(owner, previous = null, request = fetch) {
    if (!/^[a-z\d](?:[a-z\d-]{0,37}[a-z\d])?$/i.test(owner)) throw new Error('GitHub用户名格式错误')
    const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'PersonalWebsite2-profile-sync' }
    if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`
    async function pages(path, maxPages = Infinity) {
        const items = []
        for (let page = 1; page <= maxPages; page++) {
            const url = new URL(`https://api.github.com${path}`)
            url.searchParams.set('per_page', '100')
            url.searchParams.set('page', String(page))
            const response = await request(url, { headers, signal: AbortSignal.timeout(20000) })
            if (!response.ok) throw new Error(`GitHub读取失败：HTTP ${response.status}，${url.pathname}`)
            const data = await response.json()
            if (!Array.isArray(data)) throw new Error('GitHub列表响应格式错误')
            items.push(...data)
            if (!response.headers.get('link')?.includes('rel="next"')) break
        }
        return items
    }

    // owner公开列表包含原创、fork和归档项目；标签如实显示，不把fork当原创。
    const repositories = (await pages(`/users/${owner}/repos?type=owner&sort=full_name`))
        .filter(repo => !repo.private && repo.owner?.login.toLowerCase() === owner.toLowerCase())
    const allowed = new Set(repositories.map(repo => repo.full_name))
    const projects = repositories.map(repo => ({
        id: String(repo.id), fullName: repo.full_name, name: repo.name,
        description: repo.description ?? '', url: `https://github.com/${repo.full_name}`,
        language: repo.language ?? '', fork: repo.fork, archived: repo.archived,
        createdAt: repo.created_at, pushedAt: repo.pushed_at,
    })).sort((a, b) => (b.pushedAt ?? '').localeCompare(a.pushedAt ?? '') || a.fullName.localeCompare(b.fullName))
    // 只累积之前已抓到的推送。现在不再公开/已删除的仓库及其历史不继续展示。
    const pushes = new Map((previous?.owner === owner ? previous.events ?? [] : [])
        .filter(event => event.kind === 'PUSH' && allowed.has(event.repo))
        .map(event => [event.id, { id: event.id, kind: 'PUSH', repo: event.repo,
            date: event.date, title: event.title, url: event.url }]))
    // 按仓库取活动，包含协作者或工具推送，不局限于账号本人触发的事件。
    const activity = []
    for (const repo of repositories) {
        activity.push(...await pages(`/repos/${repo.full_name}/events`, 3))
    }
    for (const event of activity) {
        if (!event.public || event.type !== 'PushEvent' || !allowed.has(event.repo?.name)) continue
        const head = event.payload?.head
        const before = event.payload?.before
        const branch = (event.payload?.ref ?? '').replace(/^refs\/heads\//, '')
        const root = `https://github.com/${event.repo.name}`
        const url = /^[a-f\d]{40}$/i.test(head ?? '')
            ? (/^[a-f\d]{40}$/i.test(before ?? '') && !/^0+$/.test(before)
                ? `${root}/compare/${before}...${head}` : `${root}/commit/${head}`) : root
        pushes.set(`push:${event.id}`, { id: `push:${event.id}`, kind: 'PUSH', repo: event.repo.name,
            date: event.created_at, title: branch ? `推送到 ${branch}` : '公开推送', url })
    }
    const events = [...pushes.values()]
    for (const repo of repositories) {
        const root = `/repos/${repo.full_name}`
        // Release使用published_at，不拿仓库updated_at或标签提交日期冒充发布时间。
        for (const release of await pages(`${root}/releases`)) {
            if (release.draft || !release.published_at) continue
            events.push({ id: `release:${release.id}`, kind: 'RELEASE', repo: repo.full_name,
                date: release.published_at, title: release.name || release.tag_name,
                prerelease: release.prerelease, url: release.html_url })
        }
        // 公共活动无法恢复更早的每次push：当前可见分支的历史明确标为COMMIT。
        // 不包含作者邮箱、原始payload或token，提交标题仅保留首行。
        const commits = new Map()
        for (const branch of await pages(`${root}/branches`)) {
            for (const commit of await pages(`${root}/commits?sha=${encodeURIComponent(branch.name)}`)) {
                commits.set(commit.sha, commit)
            }
        }
        for (const commit of commits.values()) {
            events.push({ id: `commit:${repo.id}:${commit.sha}`, kind: 'COMMIT', repo: repo.full_name,
                date: commit.commit.committer?.date ?? commit.commit.author?.date,
                title: commit.commit.message.split(/\r?\n/)[0], url: commit.html_url })
        }
    }
    return { schemaVersion: 1, owner, syncedAt: new Date().toISOString(), projects,
        events: events.filter(event => Number.isFinite(Date.parse(event.date)))
            .sort((a, b) => b.date.localeCompare(a.date) || a.id.localeCompare(b.id)) }
}

// 从scripts相对定位，不依赖终端当前所在目录。
// 临时文件与目标同目录，写完后rename，读取中的页面不会遇到半份JSON。
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
    const destination = fileURLToPath(new URL('../public/github-profile.json', import.meta.url))
    let previous = null
    try { previous = JSON.parse(await readFile(destination, 'utf8')) }
    catch (error) { if (error.code !== 'ENOENT') throw error }
    const data = await collectGitHub(siteProfile.githubName, previous)
    await mkdir(dirname(destination), { recursive: true })
    await writeFile(destination + '.tmp', JSON.stringify(data, null, 2) + '\n', 'utf8')
    await rename(destination + '.tmp', destination)
    console.log(`GitHub同步完成：${data.projects.length}个公开项目，${data.events.length}条记录。`)
}