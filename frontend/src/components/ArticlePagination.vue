<script setup>
defineProps({ page: Number, totalPages: Number, loading: Boolean })
const emit = defineEmits(['change'])
</script>

<template>
  <nav v-if="totalPages > 0" class="v2-pagination" aria-label="文章分页">
    <button type="button" :disabled="loading || page <= 1" @click="emit('change', page - 1)">上一页</button>
    <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
    <button type="button" :disabled="loading || page >= totalPages" @click="emit('change', page + 1)">下一页</button>
  </nav>
</template>