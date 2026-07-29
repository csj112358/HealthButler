<template>
  <div class="diagnosis-container">
    <el-card>
      <template #header>
        <h3>AI健康诊断</h3>
      </template>

      <el-alert
        title="AI健康诊断仅供参考，不能替代专业医生的诊断"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      />

      <el-form :model="form" label-width="100px">
        <el-form-item label="选择报告">
          <el-select
            v-model="form.reportIds"
            multiple
            placeholder="请选择要分析的报告"
            style="width: 100%;"
          >
            <el-option
              v-for="report in reports"
              :key="report.id"
              :label="`${report.reportDate} - ${report.hospital}`"
              :value="report.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleDiagnosis">
            生成诊断报告
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 诊断结果 -->
      <div v-if="diagnosisResult" class="result-container">
        <el-divider content-position="left">诊断结果</el-divider>
        <div class="result-meta">
          <span>生成时间：{{ diagnosisResult.diagnosisDate }}</span>
          <span>使用模型：{{ diagnosisResult.modelName }}</span>
        </div>
        <div class="result-content" v-html="renderedContent"></div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUserReports, generateDiagnosis } from '@/api/report'

const userStore = useUserStore()

const form = reactive({
  reportIds: [] as number[],
})

const reports = ref<any[]>([])
const loading = ref(false)
const diagnosisResult = ref<any>(null)

const renderedContent = computed(() => {
  if (!diagnosisResult.value) return ''
  // 简单的Markdown渲染，实际项目中可以使用markdown-it等库
  let content = diagnosisResult.value.diagnosisContent
  content = content.replace(/### (.*)/g, '<h3>$1</h3>')
  content = content.replace(/## (.*)/g, '<h2>$1</h2>')
  content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\n/g, '<br/>')
  return content
})

const fetchReports = async () => {
  try {
    const res = await getUserReports(userStore.userId!) as any
    reports.value = res.data.filter((r: any) => r.status === 1)
  } catch (error) {
    ElMessage.error('获取报告列表失败')
  }
}

const handleDiagnosis = async () => {
  if (form.reportIds.length === 0) {
    ElMessage.warning('请至少选择一份报告')
    return
  }

  loading.value = true
  try {
    const res = await generateDiagnosis(userStore.userId!, form.reportIds) as any
    diagnosisResult.value = res.data
    ElMessage.success('诊断报告生成成功')
  } catch (error) {
    ElMessage.error('生成诊断报告失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.result-container {
  margin-top: 20px;
}

.result-meta {
  display: flex;
  gap: 20px;
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}

.result-content {
  line-height: 1.8;
  color: #303133;
}

.result-content :deep(h2) {
  margin-top: 20px;
  margin-bottom: 10px;
  color: #409eff;
}

.result-content :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: #606266;
}
</style>