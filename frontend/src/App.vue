<template>
  <div class="app">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">
        <span class="mark">
          <Icon name="code" :size="16" />
        </span>
        <span class="brand-name">Code Agent</span>
        <span class="brand-sub">多智能体代码审查</span>
      </div>

      <div class="topbar-right">
        <span class="chip" :class="statusClass">
          <i class="chip-dot" />
          {{ statusText }}
        </span>
        <span class="timer" v-if="running || elapsed > 0">
          <Icon name="clock" :size="13" />{{ clock }}
        </span>
      </div>
    </header>

    <main class="workspace">
      <!-- 左：代码输入 -->
      <section class="panel editor-panel">
        <div class="panel-head">
          <span class="panel-title">待审查代码</span>
          <div class="panel-actions">
            <select v-model="language">
              <option value="java">Java</option>
              <option value="python">Python</option>
              <option value="javascript">JavaScript</option>
            </select>
            <button @click="loadSample">示例</button>
            <button @click="clearAll" :disabled="running">清空</button>
          </div>
        </div>

        <div class="editor">
          <div class="gutter" ref="gutter">
            <span v-for="n in lineCount" :key="n">{{ n }}</span>
          </div>
          <textarea
            ref="textarea"
            v-model="code"
            spellcheck="false"
            wrap="off"
            :disabled="running"
            placeholder="粘贴需要审查的代码…"
            @scroll="syncScroll"
          />
        </div>

        <div class="panel-foot">
          <span class="meta">{{ code.length }} 字符 · {{ lineCount }} 行</span>
          <button class="btn-primary" :disabled="running || !code.trim()" @click="start">
            <Icon :name="running ? 'spark' : 'play'" :size="15" />
            {{ running ? '审查中…' : '开始审查' }}
          </button>
        </div>
      </section>

      <!-- 右：审查结果 -->
      <section class="panel result-panel">
        <div class="panel-head">
          <span class="panel-title">审查结果</span>
          <span class="task-id" v-if="taskId">TASK #{{ taskId }}</span>
        </div>

        <div class="result-scroll">
          <div class="agents">
            <AgentCard
              v-for="a in AGENTS"
              :key="a.role"
              :agent="a"
              :state="agentState[a.role]"
            />
          </div>

          <ConsoleLog v-if="running || logs.length" :lines="logs" />

          <div class="issues" v-if="issues.length">
            <div class="section-head">
              <span>问题明细</span>
              <span class="badge">{{ issues.length }}</span>
            </div>
            <div class="issue-list">
              <IssueCard v-for="(issue, i) in issues" :key="i" :issue="issue" />
            </div>
          </div>

          <div class="report" v-if="report">
            <div class="section-head">
              <span>审查报告</span>
              <button class="ghost-small" @click="copyReport">
                <Icon name="copy" :size="12" /> 复制
              </button>
            </div>
            <pre class="report-body">{{ report }}</pre>
          </div>

          <div class="empty" v-if="!taskId && !running">
            <Icon name="terminal" :size="26" />
            <p>提交代码后，三个智能体将并行审查</p>
            <span>Bug 猎手 · 安全专家 · 架构专家</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import Icon from './components/Icon.vue'
import AgentCard from './components/AgentCard.vue'
import IssueCard from './components/IssueCard.vue'
import ConsoleLog from './components/ConsoleLog.vue'
import { submitReview, fetchDetail, openStream } from './api.js'

const AGENTS = [
  {
    role: 'BUG_HUNTER',
    name: 'Bug 猎手',
    desc: '空指针、越界、资源泄漏、线程安全',
    icon: 'bug',
    color: '#5b8cff',
    soft: 'rgba(91,140,255,.12)'
  },
  {
    role: 'SECURITY',
    name: '安全专家',
    desc: '注入、越权、硬编码、敏感信息泄露',
    icon: 'shield',
    color: '#f0574f',
    soft: 'rgba(240,87,79,.12)'
  },
  {
    role: 'ARCHITECT',
    name: '架构专家',
    desc: '数据一致性、性能隐患、接口规范',
    icon: 'layers',
    color: '#a479ff',
    soft: 'rgba(164,121,255,.12)'
  }
]

const SAMPLE = `public class OrderService {

    private final JdbcTemplate jdbc;

    public String queryOrder(String orderNo) {
        // 直接拼接 SQL
        String sql = "select * from t_order where order_no = '" + orderNo + "'";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        Map<String, Object> first = rows.get(0);
        return first.get("status").toString();
    }

    public void close(AutoCloseable resource) throws Exception {
        resource.close();
    }
}`

const code = ref(SAMPLE)
const language = ref('java')
const running = ref(false)
const taskId = ref(null)
const elapsed = ref(0)
const logs = ref([])
const issues = ref([])
const report = ref('')
const textarea = ref(null)
const gutter = ref(null)

const agentState = reactive({
  BUG_HUNTER: { status: 'idle', issues: 0 },
  SECURITY: { status: 'idle', issues: 0 },
  ARCHITECT: { status: 'idle', issues: 0 }
})

let stream = null
let timer = null

const lineCount = computed(() => code.value.split('\n').length)

const clock = computed(() => {
  const m = String(Math.floor(elapsed.value / 60)).padStart(2, '0')
  const s = String(elapsed.value % 60).padStart(2, '0')
  return `${m}:${s}`
})

const statusClass = computed(() => {
  if (running.value) return 'running'
  if (taskId.value) return 'done'
  return 'idle'
})

const statusText = computed(() => {
  if (running.value) return '审查中'
  if (taskId.value) return '已完成'
  return '就绪'
})

function now() {
  const d = new Date()
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
}

function pushLog(text, kind = '') {
  logs.value.push({ time: now(), text, kind })
}

function reset() {
  issues.value = []
  report.value = ''
  logs.value = []
  taskId.value = null
  elapsed.value = 0
  Object.keys(agentState).forEach((k) => {
    agentState[k] = { status: 'idle', issues: 0 }
  })
}

function syncScroll() {
  if (gutter.value && textarea.value) {
    gutter.value.scrollTop = textarea.value.scrollTop
  }
}

function loadSample() {
  code.value = SAMPLE
}

function clearAll() {
  reset()
  code.value = ''
}

async function start() {
  reset()
  running.value = true
  pushLog('提交审查任务…')

  try {
    taskId.value = await submitReview(code.value, 'u1', language.value)
    pushLog(`任务已创建 #${taskId.value}`, 'ok')

    timer = setInterval(() => (elapsed.value += 1), 1000)
    listen(taskId.value)
  } catch (err) {
    running.value = false
    pushLog(`提交失败：${err.message}`, 'err')
  }
}

function listen(id) {
  stream = openStream(
    id,
    (data) => handleMessage(id, data),
    () => {
      if (running.value) {
        pushLog('连接中断，尝试读取结果…', 'warn')
        finish(id)
      }
    }
  )
}

function handleMessage(id, data) {
  pushLog(data)

  const scanning = data.match(/Agent \[([A-Z_]+)\]/)
  if (scanning && agentState[scanning[1]]) {
    agentState[scanning[1]] = { status: 'scanning', issues: 0 }
  }

  const found = data.match(/^\[([A-Z_]+)\] 发现 (\d+) 处问题/)
  if (found && agentState[found[1]]) {
    agentState[found[1]] = { status: 'done', issues: Number(found[2]) }
  }

  const clean = data.match(/^\[([A-Z_]+)\] 扫描完毕/)
  if (clean && agentState[clean[1]]) {
    agentState[clean[1]] = { status: 'done', issues: 0 }
  }

  if (data.includes('审查流程全部完成')) {
    finish(id)
  }
}

async function finish(id) {
  if (!running.value && report.value) return
  running.value = false
  clearInterval(timer)
  timer = null
  if (stream) {
    stream.close()
    stream = null
  }

  try {
    const detail = await fetchDetail(id)
    issues.value = detail.issueList || []
    report.value = detail.taskInfo?.finalReport || ''
    pushLog(`已加载 ${issues.value.length} 条问题明细`, 'ok')
  } catch (err) {
    pushLog(`加载结果失败：${err.message}`, 'err')
  }
}

async function copyReport() {
  try {
    await navigator.clipboard.writeText(report.value)
    pushLog('报告已复制到剪贴板', 'ok')
  } catch {
    pushLog('复制失败，请手动选择文本', 'warn')
  }
}

onBeforeUnmount(() => {
  if (stream) stream.close()
  if (timer) clearInterval(timer)
})

const STATUS_ALIAS = {
  BUG_HUNTER: 'BUG_HUNTER',
  'BUG专家': 'BUG_HUNTER',
  'Bug 猎手': 'BUG_HUNTER',
  SECURITY: 'SECURITY',
  安全专家: 'SECURITY',
  ARCHITECT: 'ARCHITECT',
  架构专家: 'ARCHITECT'
}

function roleKey(raw) {
  return STATUS_ALIAS[raw] || raw
}

// 支持 ?task=123 直接打开某个历史审查结果（方便分享链接）
onMounted(async () => {
  const id = new URLSearchParams(location.search).get('task')
  if (!id) return
  taskId.value = Number(id)
  try {
    const detail = await fetchDetail(taskId.value)
    issues.value = detail.issueList || []
    report.value = detail.taskInfo?.finalReport || ''
    const counts = {}
    issues.value.forEach((i) => {
      const key = roleKey(i.agentRole)
      counts[key] = (counts[key] || 0) + 1
    })
    Object.keys(agentState).forEach((role) => {
      agentState[role] = { status: 'done', issues: counts[role] || 0 }
    })
    pushLog(`已载入历史任务 #${taskId.value}`, 'ok')
  } catch (err) {
    pushLog(`载入任务失败：${err.message}`, 'err')
  }
})
</script>

<style scoped>
.app { height: 100%; display: flex; flex-direction: column; }

/* ---------- 顶栏 ---------- */
.topbar {
  height: 54px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(180deg, #0d1016, #0a0c10);
}

.brand { display: flex; align-items: center; gap: 9px; }

.mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #5b8cff, #7c5cff);
  box-shadow: 0 0 18px rgba(91, 140, 255, .35);
}

.brand-name { font-size: 15px; font-weight: 650; letter-spacing: .3px; }

.brand-sub {
  font-size: 11.5px;
  color: var(--dim);
  padding-left: 10px;
  margin-left: 2px;
  border-left: 1px solid var(--border);
}

.topbar-right { display: flex; align-items: center; gap: 12px; }

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid var(--border);
  color: var(--muted);
}

.chip-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--dim); }
.chip.running { color: var(--accent); border-color: rgba(91,140,255,.4); background: var(--accent-soft); }
.chip.running .chip-dot { background: var(--accent); animation: pulse 1.4s infinite; }
.chip.done { color: var(--ok); border-color: rgba(53,200,139,.35); }
.chip.done .chip-dot { background: var(--ok); }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: .25; }
}

.timer {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

/* ---------- 主体 ---------- */
.workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(380px, 44%) 1fr;
  gap: 14px;
  padding: 14px 20px 18px;
}

.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.panel-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 14px;
  border-bottom: 1px solid var(--border-soft);
}

.panel-title { font-size: 13px; font-weight: 600; }

.panel-actions { display: flex; gap: 8px; align-items: center; }

.task-id {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--dim);
  letter-spacing: .5px;
}

/* ---------- 编辑器 ---------- */
.editor {
  flex: 1;
  min-height: 0;
  display: flex;
  background: #0b0e13;
}

.gutter {
  flex: none;
  width: 46px;
  padding: 14px 8px 14px 0;
  text-align: right;
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.75;
  color: #343b49;
  overflow: hidden;
  user-select: none;
}

.gutter span { display: block; }

.editor textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: #cfd7e4;
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.75;
  padding: 14px 14px 14px 0;
  tab-size: 4;
}

.editor textarea::placeholder { color: #3a4150; }

.panel-foot {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid var(--border-soft);
}

.meta { font-size: 11.5px; color: var(--dim); font-variant-numeric: tabular-nums; }

/* ---------- 结果区 ---------- */
.result-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.agents {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #c2cad8;
  margin-bottom: 9px;
}

.badge {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 5px;
  background: var(--accent-soft);
  color: var(--accent);
}

.issue-list { display: flex; flex-direction: column; gap: 8px; }

.report-body {
  margin: 0;
  padding: 14px;
  background: #0b0e13;
  border: 1px solid var(--border-soft);
  border-radius: 9px;
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.85;
  color: #c2cad8;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow-y: auto;
}

.ghost-small {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  color: var(--muted);
}

.empty {
  flex: 1;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #333a48;
}

.empty p { margin: 0; font-size: 13px; color: var(--dim); }
.empty span { font-size: 11.5px; color: #3a4150; }

@media (max-width: 1180px) {
  .workspace { grid-template-columns: 1fr; }
  .agents { grid-template-columns: 1fr; }
}
</style>
