<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>体检报告</span>
              <el-icon><Document /></el-icon>
            </div>
          </template>
          <div class="stat-value">{{ stats.reportCount }}</div>
          <div class="stat-label">份</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>检测指标</span>
              <el-icon><DataLine /></el-icon>
            </div>
          </template>
          <div class="stat-value">{{ stats.itemCount }}</div>
          <div class="stat-label">项</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>异常指标</span>
              <el-icon><Warning /></el-icon>
            </div>
          </template>
          <div class="stat-value warning">{{ stats.abnormalCount }}</div>
          <div class="stat-label">项</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>AI诊断</span>
              <el-icon><FirstAidKit /></el-icon>
            </div>
          </template>
          <div class="stat-value">{{ stats.diagnosisCount }}</div>
          <div class="stat-label">次</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="16">
        <el-card>
          <template #header>
            <span>最近体检记录</span>
          </template>
          <el-table :data="recentReports" style="width: 100%">
            <el-table-column prop="reportDate" label="体检日期" width="120" />
            <el-table-column prop="hospital" label="体检医院" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'info'">
                  {{ row.status === 1 ? '已确认' : row.status === 0 ? '待审核' : '已忽略' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button type="primary" link @click="viewReport(row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/upload')">
              <el-icon><Upload /></el-icon>
              上传新报告
            </el-button>
            <el-button type="success" @click="$router.push('/trend')">
              <el-icon><TrendCharts /></el-icon>
              查看趋势
            </el-button>
            <el-button type="warning" @click="$router.push('/diagnosis')">
              <el-icon><FirstAidKit /></el-icon>
              AI诊断
            </el-button>
            <el-button type="info" @click="handleExport" :loading="exporting">
              <el-icon><Download /></el-icon>
              导出全部数据
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getUserReports } from '@/api/report'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const stats = ref({
  reportCount: 0,
  itemCount: 0,
  abnormalCount: 0,
  diagnosisCount: 0,
})

const recentReports = ref<any[]>([])

const fetchReports = async () => {
  try {
    const res = await getUserReports(userStore.userId!) as any
    recentReports.value = res.data.slice(0, 5)
    stats.value.reportCount = res.data.length
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

const viewReport = (id: number) => {
  router.push(`/reports/${id}`)
}

const exporting = ref(false)

const handleExport = async () => {
  exporting.value = true
  try {
    // 后端直接生成 HTML 并下载，前端只需请求文件
    const token = localStorage.getItem('token') || ''
    const resp = await fetch('/api/report/export/' + userStore.userId!, {
      headers: { 'Authorization': 'Bearer ' + token }
    })
    if (!resp.ok) throw new Error('导出失败: HTTP ' + resp.status)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '体检数据_' + new Date().toLocaleDateString('zh-CN') + '.html'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.dashboard {
  animation: fade-in var(--duration-350) var(--ease-out) both;
}

.stat-card {
  text-align: center;
  background: var(--glass-bg) !important;
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}
.stat-card :deep(.el-card__header) {
  padding: 12px 16px 8px !important;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: -0.01em;
  border-bottom: none !important;
}
.stat-card :deep(.el-card__body) {
  padding: 4px 16px 16px !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header .el-icon {
  color: var(--color-blue);
  font-size: 18px;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
  letter-spacing: -0.03em;
}
.stat-value.warning {
  color: var(--color-orange);
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-top: 4px;
  font-weight: 500;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.quick-actions .el-button {
  width: 100%;
  justify-content: flex-start;
  padding-left: 16px !important;
  font-size: 14px !important;
}
</style>