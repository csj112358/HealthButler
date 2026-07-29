import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '仪表盘' },
        },
        {
          path: 'reports',
          name: 'Reports',
          component: () => import('@/views/Reports.vue'),
          meta: { title: '体检报告' },
        },
        {
          path: 'reports/:id',
          name: 'ReportDetail',
          component: () => import('@/views/ReportDetail.vue'),
          meta: { title: '报告详情' },
        },
        {
          path: 'upload',
          name: 'Upload',
          component: () => import('@/views/Upload.vue'),
          meta: { title: '上传报告' },
        },
        {
          path: 'trend',
          name: 'Trend',
          component: () => import('@/views/Trend.vue'),
          meta: { title: '趋势分析' },
        },
        {
          path: 'diagnosis',
          name: 'Diagnosis',
          component: () => import('@/views/Diagnosis.vue'),
          meta: { title: 'AI诊断' },
        },
      ],
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router