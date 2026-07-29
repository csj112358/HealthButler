<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="aside">
      <div class="logo">
        <h3>体检报告管家</h3>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/upload">
          <el-icon><Upload /></el-icon>
          <span>上传报告</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <span>体检报告</span>
        </el-menu-item>
        <el-menu-item index="/trend">
          <el-icon><TrendCharts /></el-icon>
          <span>趋势分析</span>
        </el-menu-item>
        <el-menu-item index="/diagnosis">
          <el-icon><FirstAidKit /></el-icon>
          <span>AI诊断</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ userStore.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;
}

.aside {
  background: #1d1d1f;
  overflow: hidden;
  border-right: 0.5px solid rgba(255, 255, 255, 0.08);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.02em;
  border-bottom: 0.5px solid rgba(255, 255, 255, 0.08);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.08);
  padding: 0 24px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #1d1d1f;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.user-info:hover {
  background: rgba(118, 118, 128, 0.08);
}

.main {
  background: #f5f5f7;
  padding: 24px;
  overflow-y: auto;
}

/* 侧边栏菜单 Apple 风格 */
.side-menu {
  background: transparent;
  border-right: none;
  padding: 8px;
}

.side-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.72);
  border-radius: 10px;
  margin: 2px 0;
  height: 42px;
  line-height: 42px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(0, 122, 255, 0.2);
  color: #fff;
}

.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
  margin-right: 10px;
}
</style>