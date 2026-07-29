<template>
  <div class="report-detail-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <h3>报告详情</h3>
          <div class="header-actions">
            <template v-if="!editing">
              <el-button type="primary" @click="enterEdit">编辑</el-button>
              <el-button @click="$router.back()">返回</el-button>
            </template>
            <template v-else>
              <el-button type="primary" @click="handleSave">保存</el-button>
              <el-button @click="cancelEdit">取消</el-button>
            </template>
          </div>
        </div>
      </template>

      <!-- 基本信息 -->
      <template v-if="!editing">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报告ID">{{ report.id }}</el-descriptions-item>
          <el-descriptions-item label="体检日期">{{ report.reportDate }}</el-descriptions-item>
          <el-descriptions-item label="体检医院">{{ report.hospital }}</el-descriptions-item>
          <el-descriptions-item label="报告类型">{{ report.reportType || '年度体检' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="report.status === 1 ? 'success' : report.status === 0 ? 'warning' : 'info'">
              {{ report.status === 1 ? '已确认' : report.status === 0 ? '待审核' : '已忽略' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ report.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 编辑模式：基本信息 -->
      <template v-else>
        <div class="edit-basic-info">
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="edit-field">
                <label>体检日期</label>
                <el-date-picker
                  v-model="editForm.reportDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  style="width: 100%;"
                />
              </div>
            </el-col>
            <el-col :span="12">
              <div class="edit-field">
                <label>体检医院</label>
                <el-input v-model="editForm.hospital" placeholder="医院名称" />
              </div>
            </el-col>
          </el-row>
        </div>
      </template>

      <!-- 检测指标 -->
      <div class="table-header">
        <h4>检测指标</h4>
        <el-button v-if="editing" size="small" type="primary" plain @click="addItem">+ 添加指标</el-button>
      </div>

      <!-- 只读表格 -->
      <el-table v-if="!editing" :data="items" border style="width: 100%">
        <el-table-column prop="standardName" label="指标名称" width="180" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="value" label="检测值" width="120" />
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column prop="referenceRange" label="参考范围" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'normal' ? 'success' : row.status === 'high' ? 'danger' : 'warning'">
              {{ row.status === 'normal' ? '正常' : row.status === 'high' ? '偏高' : '偏低' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 编辑表格 -->
      <el-table v-else :data="editForm.items" border style="width: 100%">
        <el-table-column prop="name" label="指标名称" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" min-width="100">
          <template #default="{ row }">
            <el-input v-model="row.category" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="value" label="检测值" min-width="90">
          <template #default="{ row }">
            <el-input v-model.number="row.value" size="small" type="number" />
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" min-width="80">
          <template #default="{ row }">
            <el-input v-model="row.unit" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="referenceRange" label="参考范围" min-width="100">
          <template #default="{ row }">
            <el-input v-model="row.referenceRange" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-select v-model="row.status" size="small" style="width: 100%;">
              <el-option label="正常" value="normal" />
              <el-option label="偏高" value="high" />
              <el-option label="偏低" value="low" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" size="small" link @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="actions" v-if="!editing && report.status === 0">
        <el-button type="primary" @click="handleConfirm">确认保存</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getReportDetail, confirmReport, updateReport } from '@/api/report'

const route = useRoute()
const router = useRouter()

const report = ref<any>({})
const items = ref<any[]>([])
const loading = ref(false)
const editing = ref(false)

const editForm = reactive({
  reportDate: '',
  hospital: '',
  items: [] as any[],
})

const fetchReport = async () => {
  loading.value = true
  try {
    const res = await getReportDetail(Number(route.params.id)) as any
    report.value = res.data
    items.value = res.data.items || []
  } catch (error) {
    ElMessage.error('获取报告详情失败')
  } finally {
    loading.value = false
  }
}

const enterEdit = () => {
  editForm.reportDate = report.value.reportDate
  editForm.hospital = report.value.hospital
  editForm.items = items.value.map((item: any) => ({
    name: item.standardName || item.originalName,
    category: item.category,
    value: item.value,
    unit: item.unit,
    referenceRange: item.referenceRange,
    status: item.status,
  }))
  editing.value = true
}

const cancelEdit = () => {
  editing.value = false
}

const addItem = () => {
  editForm.items.push({
    name: '',
    category: '',
    value: null,
    unit: '',
    referenceRange: '',
    status: 'normal',
  })
}

const removeItem = (index: number) => {
  editForm.items.splice(index, 1)
}

const handleSave = async () => {
  try {
    await updateReport(report.value.id, {
      reportDate: editForm.reportDate,
      hospital: editForm.hospital,
      items: editForm.items,
    })
    ElMessage.success('保存成功')
    editing.value = false
    fetchReport()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const handleConfirm = async () => {
  try {
    await confirmReport(report.value.id)
    ElMessage.success('确认成功')
    fetchReport()
  } catch (error) {
    ElMessage.error('确认失败')
  }
}

onMounted(async () => {
  await fetchReport()
  // 如果 URL 带 edit=true 参数，自动进入编辑模式
  if (route.query.edit === 'true') {
    enterEdit()
  }
})
</script>

<style scoped>
.report-detail-container {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', system-ui, sans-serif;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.edit-basic-info {
  margin-bottom: 16px;
}

.edit-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.edit-field label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  margin-bottom: 10px;
}

.table-header h4 {
  margin: 0;
}

.actions {
  margin-top: 20px;
  text-align: center;
}

/* Apple 风格卡片 */
.report-detail-container :deep(.el-card) {
  border-radius: 16px;
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.report-detail-container :deep(.el-card__header) {
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

/* Apple 风格按钮 */
.report-detail-container :deep(.el-button--primary) {
  background: #007aff;
  border-color: #007aff;
  border-radius: 10px;
  font-weight: 600;
}

/* Apple 风格表格 */
.report-detail-container :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

.report-detail-container :deep(.el-table th) {
  background: #f5f5f7;
  font-weight: 600;
}
</style>
