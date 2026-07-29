<template>
  <div class="reports-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <h3>体检报告列表</h3>
          <el-button type="primary" @click="$router.push('/upload')">
            <el-icon><Upload /></el-icon>
            上传新报告
          </el-button>
        </div>
      </template>

      <el-table :data="reports" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="reportDate" label="体检日期" width="120" />
        <el-table-column prop="hospital" label="体检医院" />
        <el-table-column prop="reportType" label="报告类型" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'info'">
              {{ row.status === 1 ? '已确认' : row.status === 0 ? '待审核' : '已忽略' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row.id)">查看详情</el-button>
            <el-button type="warning" link @click="editReport(row.id)">编辑</el-button>
            <el-button
              v-if="row.status === 0"
              type="success"
              link
              @click="handleConfirm(row.id)"
            >
              确认
            </el-button>
            <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUserReports, confirmReport, deleteReport } from '@/api/report'

const router = useRouter()
const userStore = useUserStore()

const reports = ref<any[]>([])
const loading = ref(false)

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await getUserReports(userStore.userId!) as any
    reports.value = res.data
  } catch (error) {
    ElMessage.error('获取报告列表失败')
  } finally {
    loading.value = false
  }
}

const viewDetail = (id: number) => {
  router.push(`/reports/${id}`)
}

const editReport = (id: number) => {
  router.push(`/reports/${id}?edit=true`)
}

const handleConfirm = async (id: number) => {
  try {
    await confirmReport(id)
    ElMessage.success('确认成功')
    fetchReports()
  } catch (error) {
    ElMessage.error('确认失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条报告吗？删除后无法恢复。', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteReport(id)
    ElMessage.success('删除成功')
    fetchReports()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.reports-container {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', system-ui, sans-serif;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* Apple 风格卡片 */
.reports-container :deep(.el-card) {
  border-radius: 16px;
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.reports-container :deep(.el-card__header) {
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

/* Apple 风格表格 */
.reports-container :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

.reports-container :deep(.el-table th) {
  background: #f5f5f7;
  font-weight: 600;
  font-size: 12px;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.reports-container :deep(.el-table td) {
  font-size: 13px;
}

/* Apple 风格按钮 */
.reports-container :deep(.el-button--primary) {
  background: #007aff;
  border-color: #007aff;
  border-radius: 10px;
  font-weight: 600;
}

.reports-container :deep(.el-button--danger.is-link) {
  color: #ff3b30;
}
</style>