<template>
  <article class="agent" :class="[statusClass]">
    <div class="agent-head">
      <span class="agent-icon" :style="{ color: agent.color, background: agent.soft }">
        <Icon :name="agent.icon" :size="17" />
      </span>
      <span class="agent-name">{{ agent.name }}</span>
      <span class="agent-state">
        <template v-if="state.status === 'scanning'">扫描中</template>
        <template v-else-if="state.status === 'done'">
          {{ state.issues > 0 ? `${state.issues} 处` : '通过' }}
        </template>
        <template v-else>待命</template>
      </span>
    </div>

    <p class="agent-desc">{{ agent.desc }}</p>

    <div class="agent-progress" v-if="state.status === 'scanning'">
      <span></span>
    </div>
    <div class="agent-foot" v-else-if="state.status === 'done'">
      <Icon :name="state.issues > 0 ? 'alert' : 'check'" :size="13" />
      <span>{{ state.issues > 0 ? '发现风险' : '未发现该维度问题' }}</span>
    </div>
    <div class="agent-foot muted" v-else>
      <span>等待调度</span>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'

const props = defineProps({
  agent: { type: Object, required: true },
  state: { type: Object, default: () => ({ status: 'idle', issues: 0 }) }
})

const statusClass = computed(() => `is-${props.state.status}`)
</script>

<style scoped>
.agent {
  position: relative;
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 13px 14px 12px;
  transition: border-color .2s, background .2s, transform .2s;
  overflow: hidden;
}

.agent::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 2px;
  background: transparent;
  transition: background .2s;
}

.agent.is-scanning { border-color: rgba(91, 140, 255, .45); background: #161c28; }
.agent.is-scanning::before { background: var(--accent); }
.agent.is-done::before { background: var(--ok); }

.agent-head { display: flex; align-items: center; gap: 8px; }

.agent-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.agent-name { font-size: 13px; font-weight: 600; letter-spacing: .2px; flex: 1; }

.agent-state {
  font-size: 11px;
  color: var(--dim);
  font-variant-numeric: tabular-nums;
}

.is-scanning .agent-state { color: var(--accent); }
.is-done .agent-state { color: var(--ok); }

.agent-desc {
  margin: 8px 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
  min-height: 38px;
}

.agent-foot {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--ok);
  min-height: 16px;
}

.agent-foot.muted { color: var(--dim); }

.agent-progress {
  height: 16px;
  display: flex;
  align-items: center;
}

.agent-progress span {
  display: block;
  height: 2px;
  width: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 40% 100%;
  background-repeat: no-repeat;
  animation: sweep 1.1s linear infinite;
}

@keyframes sweep {
  0% { background-position: -40% 0; }
  100% { background-position: 140% 0; }
}
</style>
