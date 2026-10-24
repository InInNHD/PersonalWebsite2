import { ref } from 'vue'

// 仅记住本次页面会话的列表密度，正文返回时不把紧凑布局变回卡片。
export const compactArticles = ref(false)
