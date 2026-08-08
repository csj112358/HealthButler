<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">终身体检报告管家</h2>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleLogin" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
        <div class="login-footer">
          <el-link type="primary" @click="showRegister = true">注册新账号</el-link>
        </div>
      </el-form>
    </el-card>

    <!-- 注册对话框 -->
    <el-dialog v-model="showRegister" title="注册" width="400px">
      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="registerForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="registerForm.gender">
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
            <el-radio :label="0">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker v-model="registerForm.birthDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" :loading="registerLoading" @click="handleRegister">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { useUserStore } from '@/store/user'
import { register } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()
const loading = ref(false)
const registerLoading = ref(false)
const showRegister = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  password: '',
  phone: '',
  gender: 0,
  birthDate: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  registerLoading.value = true
  try {
    await register(registerForm)
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--bg-primary);
  padding: 24px;
}

.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--glass-bg-heavy);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-xl) !important;
  animation: scale-in var(--duration-350) var(--ease-spring) both;
}
.login-card :deep(.el-card__header) {
  text-align: center;
  padding: 24px 24px 8px !important;
  border-bottom: none !important;
}
.login-card :deep(.el-card__body) {
  padding: 8px 24px 24px !important;
}

.login-title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.025em;
  color: var(--text-primary);
  margin: 0;
}

.login-footer {
  text-align: center;
  margin-top: 4px;
}
.login-footer :deep(.el-link) {
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 480px) {
  .login-card {
    width: 100%;
  }
}
</style>