<template>
  <div class="issue" :class="levelClass">
    <div class="issue-bar" />
    <div class="issue-body">
      <div class="issue-head">
        <span class="risk">{{ riskLabel }}</span>
        <code class="loc" v-if="issue.location">{{ issue.location }}</code>
        <span class="role">{{ roleName }}</span>
      </div>
      <p class="desc">{{ issue.description }}</p>
      <p class="sug" v-if="issue.suggestion">
        <span class="sug-label">建议</span>{{ issue.suggestion }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  issue: { type: Object, required: true }
})

const ROLE_NAMES = {
  BUG_HUNTER: 'Bug 猎手',
  'BUG专家': 'Bug 猎手',
  'Bug 猎手': 'Bug 猎手',
  SECURITY: '安全专家',
  安全专家: '安全专家',
  ARCHITECT: '架构专家',
  架构专家: '架构专家'
}

const levelClass = computed(() => {
  const level = String(props.issue.riskLevel || '')
  if (level.includes('高') || level.toUpperCase() === 'HIGH') return 'lv-high'
  if (level.includes('中') || level.toUpperCase() === 'MEDIUM') return 'lv-mid'
  return 'lv-low'
})

const riskLabel = computed(() => {
  if (levelClass.value === 'lv-high') return '高风险'
  if (levelClass.value === 'lv-mid') return '中风险'
  return '低风险'
})

const roleName = computed(() => ROLE_NAMES[props.issue.agentRole] || props.issue.agentRole || '未知')
</script>

<style scoped>
.issue {
  display: flex;
  gap: 0;
  background: var(--panel-2);
  border: 1px solid var(--border-soft);
  border-radius: 9px;
  overflow: hidden;
}

.issue-bar { width: 3px; flex: none; }
.lv-high .issue-bar { background: var(--danger); }
.lv-mid .issue-bar { background: var(--warn); }
.lv-low .issue-bar { background: var(--ok); }

.issue-body { padding: 11px 13px; flex: 1; min-width: 0; }

.issue-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.risk {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 5px;
  font-weight: 600;
}

.lv-high .risk { color: var(--danger); background: rgba(240, 87, 79, .12); }
.lv-mid .risk { color: var(--warn); background: rgba(240, 169, 59, .12); }
.lv-low .risk { color: var(--ok); background: rgba(53, 200, 139, .12); }

.loc {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--muted);
  background: #0e1117;
  border: 1px solid var(--border-soft);
  padding: 1px 6px;
  border-radius: 4px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role { margin-left: auto; font-size: 11px; color: var(--dim); }

.desc {
  margin: 0 0 6px;
  font-size: 12.5px;
  line-height: 1.65;
  color: #c8cfdb;
}

.sug {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}

.sug-label {
  color: var(--accent);
  margin-right: 6px;
  font-weight: 600;
}
</style>
