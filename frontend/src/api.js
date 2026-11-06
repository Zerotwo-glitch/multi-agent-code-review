/**
 * 后端接口封装
 * - 提交审查：POST /api/review/multi-agent（body 就是代码原文）
 * - 进度推送：GET  /api/review/stream/{taskId}（SSE）
 * - 结果查询：GET  /api/review/detail/{taskId}
 */

export async function submitReview(code, userId = 'u1', language = 'java') {
  const res = await fetch(
    `/api/review/multi-agent?userId=${encodeURIComponent(userId)}&language=${encodeURIComponent(language)}`,
    { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=UTF-8' }, body: code }
  )
  const text = await res.text()
  if (!res.ok) throw new Error(text || `HTTP ${res.status}`)
  const match = text.match(/(\d+)/)
  if (!match) throw new Error(text)
  return Number(match[1])
}

export async function fetchDetail(taskId) {
  const res = await fetch(`/api/review/detail/${taskId}`)
  if (!res.ok) throw new Error(`查询任务失败：HTTP ${res.status}`)
  return res.json()
}

export function openStream(taskId, onMessage, onError) {
  const es = new EventSource(`/api/review/stream/${taskId}`)
  es.onmessage = (e) => onMessage(e.data)
  es.onerror = () => onError && onError()
  return es
}
