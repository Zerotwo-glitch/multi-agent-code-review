<template>
  <div class="console">
    <div class="console-head">
      <span class="dot" />
      <span>实时进度</span>
      <span class="count" v-if="lines.length">{{ lines.length }}</span>
    </div>
    <div class="console-body" ref="body">
      <p v-if="!lines.length" class="placeholder">等待任务开始…</p>
      <p v-for="(line, i) in lines" :key="i" class="line" :class="line.kind">
        <span class="time">{{ line.time }}</span>
        <span class="text">{{ line.text }}</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  lines: { type: Array, default: () => [] }
})

const body = ref(null)

watch(
  () => props.lines.length,
  async () => {
    await nextTick()
    if (body.value) body.value.scrollTop = body.value.scrollHeight
  }
)
</script>

<style scoped>
.console {
  background: #0c0f14;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius);
  overflow: hidden;
}

.console-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-soft);
  font-size: 11.5px;
  color: var(--muted);
  letter-spacing: .3px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
  box-shadow: 0 0 8px rgba(53, 200, 139, .8);
}

.count {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: var(--dim);
}

.console-body {
  height: 168px;
  overflow-y: auto;
  padding: 10px 12px;
  font-family: var(--mono);
  font-size: 12px;
  line-height: 1.75;
}

.placeholder { color: var(--dim); margin: 0; }

.line { margin: 0; display: flex; gap: 10px; }

.time { color: #3f4756; flex: none; font-variant-numeric: tabular-nums; }

.text { color: #b9c2d0; word-break: break-all; }

.line.ok .text { color: var(--ok); }
.line.warn .text { color: var(--warn); }
.line.err .text { color: var(--danger); }
</style>
