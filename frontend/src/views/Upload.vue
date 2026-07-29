<template>
  <div class="upload-container">
    <el-card>
      <template #header>
        <h3>上传体检报告</h3>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 文字粘贴模式 -->
        <el-tab-pane label="粘贴报告文字" name="text">
          <div class="text-input-area">
            <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
              用手机识别体检报告后，将文字内容粘贴到下方，AI 将自动提取检测指标。
            </el-alert>
            <el-input
              v-model="reportText"
              type="textarea"
              :rows="12"
              placeholder="请粘贴体检报告的文字内容..."
            />
            <div class="upload-actions">
              <el-button type="primary" :loading="uploading" @click="handleTextSubmit">
                开始分析
              </el-button>
              <el-button @click="reportText = ''">清空</el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- 图片上传模式 -->
        <el-tab-pane label="上传报告图片" name="image">
          <el-upload
            class="upload-area"
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :file-list="fileList"
            accept="image/*"
            :limit="1"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 JPG、PNG、BMP 等图片格式，文件大小不超过 10MB
              </div>
            </template>
          </el-upload>

          <div class="upload-actions" v-if="fileList.length > 0">
            <el-button type="primary" :loading="uploading" @click="handleImageUpload">
              开始识别
            </el-button>
            <el-button @click="fileList = []">清空</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 识别结果预览（可编辑） -->
      <el-card v-if="ocrResult" class="result-card" style="margin-top: 20px;">
        <template #header>
          <div class="result-header">
            <h4>识别结果</h4>
            <el-tag :type="resultStatus === 'success' ? 'success' : resultStatus === 'error' ? 'danger' : 'warning'">
              {{ resultStatus === 'success' ? '识别成功' : resultStatus === 'error' ? '识别失败' : '待确认（可编辑）' }}
            </el-tag>
          </div>
        </template>

        <!-- 基本信息可编辑 -->
        <div class="edit-basic-info">
          <el-row :gutter="16">
            <el-col :span="12">
              <div class="edit-field">
                <label>体检日期</label>
                <el-date-picker
                  v-model="ocrResult.reportDate"
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
                <el-input v-model="ocrResult.hospital" placeholder="医院名称" />
              </div>
            </el-col>
          </el-row>
        </div>

        <div class="table-header">
          <h4>检测指标</h4>
          <el-button size="small" type="primary" plain @click="addItem">+ 添加指标</el-button>
        </div>
        <el-table :data="ocrResult.items" border style="width: 100%">
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

        <div class="result-actions">
          <el-button type="primary" @click="handleConfirm">确认保存</el-button>
          <el-button @click="handleReject">忽略</el-button>
        </div>
      </el-card>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { useUserStore } from '@/store/user'
import { uploadReport, submitTextReport, confirmReport } from '@/api/report'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('text')
const reportText = ref('')
const fileList = ref<UploadFile[]>([])
const uploading = ref(false)
const reportId = ref<number | null>(null)
const ocrResult = ref<any>(null)
const resultStatus = ref<'pending' | 'success' | 'error'>('pending')

const handleFileChange = (file: UploadFile) => {
  fileList.value = [file]
}

const handleTextSubmit = async () => {
  if (!reportText.value.trim()) {
    ElMessage.warning('请先粘贴体检报告文字')
    return
  }

  uploading.value = true
  try {
    const res = await submitTextReport(reportText.value) as any
    reportId.value = res.data.reportId
    ocrResult.value = res.data.ocrResult
    resultStatus.value = 'pending'
    ElMessage.success('解析完成，请确认结果')
  } catch (error) {
    ElMessage.error('解析失败')
  } finally {
    uploading.value = false
  }
}

const handleImageUpload = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  try {
    const res = await uploadReport(userStore.userId!, fileList.value[0].raw!) as any
    reportId.value = res.data
    ocrResult.value = res.data.ocrResult
    resultStatus.value = 'pending'
    ElMessage.success('识别完成，请确认结果')
  } catch (error) {
    ElMessage.error('识别失败')
  } finally {
    uploading.value = false
  }
}

const handleConfirm = async () => {
  if (!reportId.value) return

  try {
    // 提交编辑后的数据
    const editedData = {
      reportDate: ocrResult.value.reportDate,
      hospital: ocrResult.value.hospital,
      items: ocrResult.value.items,
    }
    await confirmReport(reportId.value, editedData)
    resultStatus.value = 'success'
    ElMessage.success('保存成功')
    setTimeout(() => {
      router.push('/reports')
    }, 1500)
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const handleReject = () => {
  ocrResult.value = null
  reportId.value = null
  fileList.value = []
  reportText.value = ''
}

const addItem = () => {
  if (!ocrResult.value) return
  ocrResult.value.items.push({
    name: '',
    category: '',
    value: null,
    unit: '',
    referenceRange: '',
    status: 'normal',
  })
}

const removeItem = (index: number) => {
  if (!ocrResult.value) return
  ocrResult.value.items.splice(index, 1)
}
</script>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', system-ui, sans-serif;
}

.upload-area {
  width: 100%;
}

.upload-actions {
  margin-top: 20px;
  text-align: center;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  margin-top: 16px;
  margin-bottom: 10px;
}

.table-header h4 {
  margin: 0;
}

.result-actions {
  margin-top: 20px;
  text-align: center;
}

/* Apple 风格卡片 */
.upload-container :deep(.el-card) {
  border-radius: 16px;
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.upload-container :deep(.el-card__header) {
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.result-card {
  border-radius: 14px;
}

/* Apple 风格按钮 */
.upload-container :deep(.el-button--primary) {
  background: #007aff;
  border-color: #007aff;
  border-radius: 10px;
  font-weight: 600;
}

.upload-container :deep(.el-button--primary:active) {
  transform: scale(0.97);
}

/* Apple 风格 Tab */
.upload-container :deep(.el-tabs__item) {
  font-weight: 500;
}

.upload-container :deep(.el-tabs__active-bar) {
  background: #007aff;
}

/* Apple 风格表格 */
.upload-container :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

.upload-container :deep(.el-table th) {
  background: #f5f5f7;
  font-weight: 600;
}
</style>
