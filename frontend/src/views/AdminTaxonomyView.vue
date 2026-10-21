<script setup>
import { computed, reactive } from 'vue'
import AdminTaxonomyPanel from '../components/AdminTaxonomyPanel.vue'
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue'
import { useAdminLeave } from '../useAdminLeave.js'
const dirty = reactive({ categories: false, tags: false }), busy = reactive({ categories: false, tags: false })
const { discardOpen, resolveDiscard } = useAdminLeave(computed(() => dirty.categories || dirty.tags), computed(() => busy.categories || busy.tags))
</script>
<template>
  <section><header class="studio-page-heading"><div><p class="studio-eyebrow">ORGANIZE / CONNECT THE NOTES</p><h1>分类与标签</h1><p>名称修改后，已有文章的编号关联保持不变。</p></div></header>
    <div class="studio-taxonomy-grid"><AdminTaxonomyPanel path="/categories" label="分类" @dirty="dirty.categories = $event" @busy="busy.categories = $event" /><AdminTaxonomyPanel path="/tags" label="标签" @dirty="dirty.tags = $event" @busy="busy.tags = $event" /></div>
    <AdminConfirmDialog v-if="discardOpen" title="放弃名称修改？" message="当前名称尚未保存，离开将放弃输入。" confirm-text="放弃并离开" @resolve="resolveDiscard" />
  </section>
</template>
