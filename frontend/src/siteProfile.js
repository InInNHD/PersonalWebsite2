// 这里只放愿意公开的资料；首页、关于页和页脚共同引用。
// 普通对象足够，内容修改后 Vite 会重新加载，不需要 ref 或后台接口。
export const siteProfile = {
    siteName: '萤火手记',
    nickname: 'firefly1v',
    tagline: '记录学习、思考与生活。',
    // 首页与作者卡共同使用的公开文案。
    heroTitle: '把思考留下，',
    heroAccent: '让知识生长。',
    heroDescription: '从一个可运行的小例子出发，记录Java开发、前端实践和项目中的思考。',
    profileDescription: '整理代码，也整理理解。把项目里遇到的问题，写成下次能用上的笔记。',
    aboutParagraphs: [
        '这里是我的个人笔记站。我会把学习和项目实践中的问题、尝试与结论整理下来，方便回顾，也分享给遇到相似问题的人。',
        '公开项目涉及个人网站、票务业务和开发工具。写代码时，我希望先把一条流程完整跑通，再逐步补上异常处理、验证和交付。具体实现与版本记录可以在GitHub查看。',
        '笔记用于整理知识，心得用于回看选择，公开日记则留给愿意分享的日常。尚未想清楚的内容先留在草稿里。',
    ],
    // 暂未确定的信息留空，不生成占位链接。
    githubName: 'InInNHD',
    githubUrl: 'https://github.com/InInNHD',
    // 可填public中的相对站点路径，例如/images/avatar.webp；空或加载失败时用字母标识。
    avatarUrl: '',
    // 根据已确认公开项目整理学习方向；未提供的个人经历与联系方式继续留空。
    learningDirections: [
        { title: 'Java与Spring Boot', description: '' },
        { title: 'Vue与前后端联调', description: '' },
        { title: '数据库与接口设计', description: '' },
        { title: '测试、备份和版本交付', description: '' },
    ],
    experiences: [],
    contactLinks: [],
}
