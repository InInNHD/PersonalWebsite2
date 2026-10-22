<script setup>
defineProps({ name: { type: String, default: 'link' } })
const paths = {
  link: 'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2',
  code: 'm8 6-6 6 6 6m8-12 6 6-6 6M14 4l-4 16',
  book: 'M12 5v16M3 3c4 0 7 0 9 2 2-2 5-2 9-2v16c-4 0-7 0-9 2-2-2-5-2-9-2Z',
  tool: 'M14 6a5 5 0 0 0-6 6L2 18l4 4 6-6a5 5 0 0 0 6-6l-4 2-2-2-2Z',
  course: 'm2 8 10-5 10 5-10 5Zm4 3v6l6 4 6-4v-6M22 8v10',
}
</script>
<template><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] ?? paths.link" /></svg></template>
