# 终身体检报告管家 - 前端

基于 Vue 3 + TypeScript + Element Plus + ECharts 的体检报告管理系统前端。

## 技术栈

- **Vue 3**: 渐进式JavaScript框架
- **TypeScript**: JavaScript的超集，提供类型系统
- **Vite**: 下一代前端构建工具
- **Vue Router**: Vue.js官方路由管理器
- **Pinia**: Vue.js官方状态管理库
- **Element Plus**: Vue 3组件库
- **ECharts**: 数据可视化图表库
- **Axios**: HTTP客户端

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API接口定义
│   │   ├── auth.ts       # 认证相关API
│   │   └── report.ts     # 报告相关API
│   ├── assets/           # 静态资源
│   ├── components/       # 公共组件
│   ├── router/           # 路由配置
│   │   └── index.ts
│   ├── store/            # 状态管理
│   │   └── user.ts
│   ├── utils/            # 工具函数
│   │   └── request.ts    # Axios封装
│   ├── views/            # 页面组件
│   │   ├── Dashboard.vue # 仪表盘
│   │   ├── Login.vue     # 登录页
│   │   ├── Layout.vue    # 布局组件
│   │   ├── Upload.vue    # 上传报告
│   │   ├── Reports.vue   # 报告列表
│   │   ├── ReportDetail.vue # 报告详情
│   │   ├── Trend.vue     # 趋势分析
│   │   └── Diagnosis.vue # AI诊断
│   ├── App.vue           # 根组件
│   └── main.ts           # 入口文件
├── index.html            # HTML模板
├── package.json          # 项目配置
├── tsconfig.json         # TypeScript配置
└── vite.config.ts        # Vite配置
```

## 快速开始

### 安装依赖

```bash
cd frontend
npm install
```

### 开发环境

```bash
npm run dev
```

访问 http://localhost:3000

### 生产构建

```bash
npm run build
```

## 功能模块

### 1. 登录/注册
- 用户名密码登录
- 新用户注册
- JWT Token认证

### 2. 仪表盘
- 统计概览（报告数量、检测指标、异常指标、AI诊断）
- 最近体检记录
- 快捷操作

### 3. 上传报告
- 拖拽上传体检报告图片
- AI自动识别OCR
- 识别结果预览和确认

### 4. 报告列表
- 查看所有体检报告
- 按状态筛选
- 查看报告详情

### 5. 趋势分析
- 单指标历年趋势图
- 同类指标对比图
- 参考范围标注
- 异常值高亮

### 6. AI诊断
- 选择多份报告进行分析
- 生成个性化健康诊断报告
- Markdown格式展示

## 开发说明

### 添加新页面

1. 在 `src/views/` 创建新的Vue组件
2. 在 `src/router/index.ts` 添加路由配置
3. 如需API调用，在 `src/api/` 添加接口定义

### 状态管理

使用Pinia进行状态管理，示例：

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useMyStore = defineStore('my', () => {
  const count = ref(0)

  function increment() {
    count.value++
  }

  return { count, increment }
})
```

### API调用

使用封装好的Axios实例：

```typescript
import request from '@/utils/request'

export function getData() {
  return request({
    url: '/data',
    method: 'get',
  })
}
```

### ECharts使用

```typescript
import * as echarts from 'echarts'

const chart = echarts.init(element)
chart.setOption({
  // 配置项
})
```

## 部署

### 开发环境

```bash
npm run dev
```

### 生产环境

```bash
npm run build
```

构建产物在 `dist` 目录，可部署到任何静态文件服务器。

## 注意事项

1. 开发时确保后端服务已启动
2. API请求通过Vite代理转发到后端
3. 生产环境需要配置正确的API地址
4. ECharts图表会自动适应窗口大小变化