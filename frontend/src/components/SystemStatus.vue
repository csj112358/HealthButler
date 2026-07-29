<template>
  <div class="system-status" :class="{ loading: overallStatus === 'loading' }">
    <el-tooltip placement="top" :show-after="300" :hide-after="0">
      <template #content>
        <div class="status-tooltip">
          <div class="status-row">
            <span class="status-dot" :class="components.mysql"></span>
            <span>MySQL: {{ components.mysql === 'up' ? '正常' : '异常' }}</span>
          </div>
          <div class="status-row">
            <span class="status-dot" :class="components.redis"></span>
            <span>Redis: {{ components.redis === 'up' ? '正常' : '异常' }}</span>
          </div>
        </div>
      </template>
      <div class="status-trigger">
        <span class="status-dot indicator" :class="overallStatus"></span>
        <span class="status-text">{{ statusLabel }}</span>
      </div>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

interface ComponentStatus {
  mysql: 'up' | 'down'
  redis: 'up' | 'down'
  timestamp: number
}

interface HealthResponse {
  code: number
  message: string
  data: ComponentStatus
}

const POLL_INTERVAL = 30000

const components = ref<ComponentStatus>({ mysql: 'down', redis: 'down', timestamp: 0 })
const firstLoad = ref(true)
let timer: ReturnType<typeof setInterval> | null = null

const overallStatus = computed(() => {
  if (firstLoad.value) return 'loading'
  if (components.value.mysql === 'up' && components.value.redis === 'up') return 'up'
  return 'down'
})

const statusLabel = computed(() => {
  if (firstLoad.value) return '检测中...'
  if (overallStatus.value === 'up') return '系统正常'
  return '服务异常'
})

async function checkHealth() {
  try {
    const response = await fetch('/api/public/health')
    if (response.ok) {
      const json: HealthResponse = await response.json()
      if (json.code === 200 && json.data) {
        components.value = json.data
      }
    }
  } catch {
    components.value = { mysql: 'down', redis: 'down', timestamp: Date.now() }
  } finally {
    firstLoad.value = false
  }
}

onMounted(() => {
  checkHealth()
  timer = setInterval(checkHealth, POLL_INTERVAL)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.system-status {
  position: fixed;
  bottom: 16px;
  left: 16px;
  z-index: 1000;
}

.status-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  box-shadow: var(--shadow-md);
  cursor: default;
  transition: all var(--transition-normal);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
}

.status-trigger:hover {
  background: rgba(255, 255, 255, 0.9);
}

.status-text {
  font-family: var(--font-family);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
}

.status-dot.indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.indicator.up {
  background: var(--color-green);
}

.status-dot.indicator.down {
  background: var(--color-red);
}

.status-dot.indicator.loading {
  background: var(--color-orange);
  animation: pulse 1.2s ease-in-out infinite;
}

.status-tooltip .status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--text-primary);
}

.status-tooltip .status-row:first-child {
  padding-top: 0;
}

.status-tooltip .status-row:last-child {
  padding-bottom: 0;
}

.status-tooltip .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-tooltip .status-dot.up {
  background: var(--color-green);
}

.status-tooltip .status-dot.down {
  background: var(--color-red);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

@media (prefers-reduced-motion: reduce) {
  .status-dot.indicator.loading {
    animation: none;
    opacity: 1;
  }
}
</style>
