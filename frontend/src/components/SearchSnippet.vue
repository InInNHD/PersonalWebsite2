<script setup>
defineProps({ match: { type: Object, required: true } })
const fields = { title: '标题命中', summary: '摘要命中', body: '正文命中' }
</script>
<template>
  <!-- 三段均为Vue文本插值；搜索到HTML或代码时也不执行，不使用v-html。 -->
  <p class="search-snippet"><small>{{ fields[match.field] || '匹配片段' }}</small><span>{{ match.leading ? '…' : '' }}{{ match.before }}<mark>{{ match.match }}</mark>{{ match.after }}{{ match.trailing ? '…' : '' }}</span></p>
</template>
