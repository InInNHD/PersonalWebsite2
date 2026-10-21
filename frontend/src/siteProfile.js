// 这里只放愿意公开的资料；首页、关于页和页脚共同引用。
// 普通对象足够，内容修改后 Vite 会重新加载，不需要 ref 或后台接口。
export const siteProfile = {
    siteName: '萤火手记',
    nickname: 'firefly1v',
    tagline: '记录学习、思考与生活。',
    // 首页与作者卡共同使用的公开文案。
    heroTitle: '把思考留下，',
    heroAccent: '让知识生长。',
    heroDescription: '在这里整理学习中的发现，也收藏生活里的微光。',
    profileDescription: '把零散的知识连成线，把普通的日子写成篇。',
    aboutParagraphs: [
        '我在这里整理学习过程中的笔记，记录思考与心得，也保留一些愿意分享的生活片段。',
        '希望这些记录能帮助我回顾自己的成长，也为偶然来到这里的人提供一点参考。',
    ],
    // 暂未确定的信息留空，不生成占位链接。
    githubName: 'InInNHD',
    githubUrl: 'https://github.com/InInNHD',
    // 可填public中的相对站点路径，例如/images/avatar.webp；空或加载失败时用字母标识。
    avatarUrl: '',
    // 没有提供真实资料就留空，关于页隐藏对应区块。
    learningDirections: [],
    experiences: [],
    contactLinks: [],
}