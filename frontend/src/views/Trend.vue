<template>
  <div class="trend-container">
    <!-- 顶部控制栏 -->
    <div class="trend-header">
      <div class="header-left">
        <h3 class="page-title">趋势分析</h3>
        <span class="page-subtitle">追踪健康指标变化</span>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchText"
          placeholder="搜索指标..."
          clearable
          prefix-icon="Search"
          class="search-input"
        />
        <el-button
          :icon="Filter"
          circle
          class="filter-btn"
          @click="showAdvanced = !showAdvanced"
        />
      </div>
    </div>

    <!-- 分类标签组 -->
    <div class="category-tags">
      <div class="tags-scroll">
        <div
          v-for="cat in categories"
          :key="cat.key"
          class="cat-tag"
          :class="{ active: activeCategory === cat.key }"
          @click="selectCategory(cat.key)"
        >
          <span class="cat-icon">{{ cat.icon }}</span>
          <span class="cat-label">{{ cat.label }}</span>
        </div>
      </div>
    </div>

    <!-- 已选指标标签 -->
    <div v-if="selectedIndicators.length > 0" class="selected-tags">
      <transition-group name="tag-fade">
        <el-tag
          v-for="item in selectedIndicators"
          :key="item"
          closable
          :type="isAbnormal(item) ? 'danger' : 'primary'"
          class="indicator-tag"
          @close="toggleIndicator(item)"
          @click="highlightSeries(item)"
        >
          {{ item }}
          <span v-if="isAbnormal(item)" class="abnormal-dot">!</span>
        </el-tag>
      </transition-group>
    </div>

    <!-- 指标选择器 -->
    <div class="indicator-selector">
      <div class="indicator-grid">
        <div
          v-for="item in filteredIndicators"
          :key="item.name"
          class="indicator-card"
          :class="{
            selected: selectedIndicators.includes(item.name),
            abnormal: item.status && item.status !== 'normal',
          }"
          @click="toggleIndicator(item.name)"
        >
          <div class="card-check">
            <el-icon v-if="selectedIndicators.includes(item.name)"><Check /></el-icon>
          </div>
          <div class="card-name">{{ item.name }}</div>
          <div class="card-category">{{ item.category }}</div>
          <div v-if="item.value != null" class="card-value">
            {{ item.value }}
            <span class="card-unit">{{ item.unit || '' }}</span>
          </div>
          <div v-if="item.status && item.status !== 'normal'" class="card-abnormal">
            {{ item.status === 'high' ? '偏高' : '偏低' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 高级筛选（折叠面板） -->
    <el-collapse-transition>
      <div v-show="showAdvanced" class="advanced-panel">
        <div class="panel-header">
          <span>高级筛选</span>
          <el-button text @click="showAdvanced = false">收起</el-button>
        </div>
        <div class="panel-body">
          <div v-for="cat in categories" :key="cat.key" class="panel-group">
            <div class="group-label">{{ cat.icon }} {{ cat.label }}</div>
            <div class="group-items">
              <el-checkbox
                v-for="ind in getCategoryIndicators(cat.key)"
                :key="ind.name"
                :model-value="selectedIndicators.includes(ind.name)"
                @change="toggleIndicator(ind.name)"
              >
                {{ ind.name }}
              </el-checkbox>
            </div>
          </div>
        </div>
      </div>
    </el-collapse-transition>

    <!-- 图表区域 -->
    <div class="chart-area">
      <div v-if="selectedIndicators.length === 0" class="empty-state">
        <el-empty description="请从上方选择指标查看趋势">
          <template #image>
            <div class="empty-icon">📊</div>
          </template>
        </el-empty>
      </div>

      <template v-else>
        <!-- 单指标详细趋势图 -->
        <div v-if="selectedIndicators.length === 1 && trendData" class="chart-card">
          <div class="chart-card-header">
            <div class="chart-title">
              <span class="title-name">{{ trendData.standardName }}</span>
              <el-tag size="small" :type="getLatestStatusType()">{{ getLatestStatusText() }}</el-tag>
            </div>
            <div class="chart-meta">
              <span>参考范围：{{ trendData.referenceRange || '--' }}</span>
              <span>单位：{{ trendData.unit || '--' }}</span>
            </div>
          </div>
          <div ref="singleChartRef" class="chart-canvas"></div>
        </div>

        <!-- 多指标对比图 -->
        <div v-if="selectedIndicators.length > 1" class="chart-card">
          <div class="chart-card-header">
            <div class="chart-title">
              <span class="title-name">多指标对比</span>
              <span class="title-count">{{ selectedIndicators.length }} 项指标</span>
            </div>
          </div>
          <div ref="categoryChartRef" class="chart-canvas"></div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Check, Filter } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getTrendData, getUserIndicators } from '@/api/report'

const userStore = useUserStore()

// ===== 分类图标映射 =====
const categoryIcons: Record<string, string> = {
  '血常规': '🩸',
  '肝功能': '🫀',
  '肾功能': '🫘',
  '血脂': '🫧',
  '血糖': '🍬',
  '血压': '💉',
  '甲状腺': '🦋',
  '电解质': '⚗️',
  '其他': '📋',
}

// ===== 状态 =====
const activeCategory = ref('all')
const searchText = ref('')
const selectedIndicators = ref<string[]>([])
const showAdvanced = ref(false)
const trendData = ref<any>(null)
const categoryTrendData = ref<any[]>([])
const allIndicators = ref<any[]>([])  // 从后端获取的所有指标

const singleChartRef = ref<HTMLElement>()
const categoryChartRef = ref<HTMLElement>()
let singleChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let debounceTimer: ReturnType<typeof setTimeout> | null = null

// ===== 计算属性 =====
const categories = computed(() => {
  const cats = new Set<string>()
  allIndicators.value.forEach(ind => cats.add(ind.category || '其他'))
  return [
    { key: 'all', label: '全部', icon: '📋' },
    ...Array.from(cats).map(cat => ({
      key: cat,
      label: cat,
      icon: categoryIcons[cat] || '📊',
    })),
  ]
})

const filteredIndicators = computed(() => {
  let list = allIndicators.value
  if (activeCategory.value !== 'all') {
    list = list.filter(ind => ind.category === activeCategory.value)
  }
  if (searchText.value) {
    list = list.filter(ind => ind.name.includes(searchText.value))
  }
  return list
})

// ===== 方法 =====
function getCategoryIndicators(key: string): any[] {
  if (key === 'all') return allIndicators.value
  return allIndicators.value.filter(ind => ind.category === key)
}

// 获取用户所有指标
async function fetchIndicators() {
  try {
    const res = await getUserIndicators(userStore.userId!) as any
    allIndicators.value = res.data || []
  } catch (error) {
    console.error('获取指标列表失败', error)
  }
}

function selectCategory(key: string) {
  activeCategory.value = key
}

function toggleIndicator(name: string) {
  const idx = selectedIndicators.value.indexOf(name)
  if (idx >= 0) {
    selectedIndicators.value.splice(idx, 1)
  } else {
    selectedIndicators.value.push(name)
  }
  debouncedFetch()
}

function highlightSeries(name: string) {
  const chart = selectedIndicators.value.length === 1 ? singleChart : categoryChart
  if (!chart) return
  chart.dispatchAction({ type: 'highlight', seriesName: name })
  setTimeout(() => {
    chart.dispatchAction({ type: 'downplay', seriesName: name })
  }, 2000)
}

function isAbnormal(name: string): boolean {
  const ind = allIndicators.value.find(i => i.name === name)
  return ind ? (ind.status && ind.status !== 'normal') : false
}

function getLatestStatusType(): '' | 'success' | 'warning' | 'danger' {
  if (!trendData.value?.dataPoints?.length) return ''
  const last = trendData.value.dataPoints[trendData.value.dataPoints.length - 1]
  if (last.status === 'normal') return 'success'
  if (last.status === 'high') return 'danger'
  return 'warning'
}

function getLatestStatusText(): string {
  if (!trendData.value?.dataPoints?.length) return '--'
  const last = trendData.value.dataPoints[trendData.value.dataPoints.length - 1]
  if (last.status === 'normal') return '正常'
  if (last.status === 'high') return '偏高'
  return '偏低'
}

// ===== 数据获取（防抖） =====
function debouncedFetch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { fetchData() }, 300)
}

async function fetchData() {
  if (selectedIndicators.value.length === 0) {
    trendData.value = null
    categoryTrendData.value = []
    return
  }

  try {
    if (selectedIndicators.value.length === 1) {
      const res = await getTrendData(userStore.userId!, selectedIndicators.value[0]) as any
      trendData.value = res.data
      categoryTrendData.value = []
      await nextTick()
      renderSingleChart()
    } else {
      trendData.value = null
      const results: any[] = []
      for (const name of selectedIndicators.value) {
        try {
          const res = await getTrendData(userStore.userId!, name) as any
          if (res.data) results.push(res.data)
        } catch { /* skip */ }
      }
      categoryTrendData.value = results
      await nextTick()
      renderCategoryChart()
    }
  } catch (error) {
    console.error('获取趋势数据失败', error)
  }
}

// ===== 图表渲染：单指标 =====
function renderSingleChart() {
  if (!singleChartRef.value || !trendData.value) return
  if (singleChart) singleChart.dispose()
  singleChart = echarts.init(singleChartRef.value)

  const data = trendData.value
  // 按日期排序，确保 x 轴从左到右是时间正序
  const sortedPoints = [...data.dataPoints].sort((a: any, b: any) => a.date.localeCompare(b.date))
  const dates = sortedPoints.map((p: any) => p.date)
  const values = sortedPoints.map((p: any) => p.value)
  const statuses = sortedPoints.map((p: any) => p.status)

  const refRange = data.referenceRange || ''
  let minRef: number | null = null
  let maxRef: number | null = null
  if (refRange.includes('-')) {
    const parts = refRange.split('-')
    minRef = parseFloat(parts[0])
    maxRef = parseFloat(parts[1])
  } else if (refRange.startsWith('<')) {
    maxRef = parseFloat(refRange.substring(1))
  } else if (refRange.startsWith('>')) {
    minRef = parseFloat(refRange.substring(1))
  }

  const allValues = values.filter((v: any) => v != null) as number[]
  const yMin = Math.floor((minRef != null ? Math.min(minRef, Math.min(...allValues)) : Math.min(...allValues)) * 0.9)
  const yMax = Math.ceil((maxRef != null ? Math.max(maxRef, Math.max(...allValues)) : Math.max(...allValues)) * 1.1)

  // 参考区间色带
  const markAreaData: any[] = []
  if (minRef != null && maxRef != null) {
    markAreaData.push([
      { yAxis: minRef, itemStyle: { color: 'rgba(82, 196, 26, 0.08)' } },
      { yAxis: maxRef },
    ])
    markAreaData.push([
      { yAxis: yMin, itemStyle: { color: 'rgba(255, 77, 79, 0.06)' } },
      { yAxis: minRef },
    ])
    markAreaData.push([
      { yAxis: maxRef, itemStyle: { color: 'rgba(255, 77, 79, 0.06)' } },
      { yAxis: yMax },
    ])
  }

  // 异常散点
  const abnormalData = values
    .map((v: any, i: number) => statuses[i] !== 'normal' ? [dates[i], v] : null)
    .filter(Boolean)

  const seriesConfig: any[] = [
    {
      name: data.standardName,
      type: 'line',
      data: values,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 2.5, color: '#1890ff' },
      itemStyle: { color: '#1890ff', borderWidth: 2, borderColor: '#fff' },
      emphasis: { lineStyle: { width: 3.5 }, itemStyle: { borderWidth: 3 } },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(24, 144, 255, 0.15)' },
          { offset: 1, color: 'rgba(24, 144, 255, 0.02)' },
        ]),
      },
      markArea: markAreaData.length > 0 ? { data: markAreaData, silent: true } : undefined,
      markLine: {
        silent: true,
        symbol: 'none',
        data: [
          ...(minRef != null ? [{
            yAxis: minRef,
            lineStyle: { color: '#faad14', type: 'dashed', width: 1 },
            label: { formatter: '下限 ' + minRef, position: 'insideEndTop', color: '#faad14', fontSize: 10 },
          }] : []),
          ...(maxRef != null ? [{
            yAxis: maxRef,
            lineStyle: { color: '#ff4d4f', type: 'dashed', width: 1 },
            label: { formatter: '上限 ' + maxRef, position: 'insideEndBottom', color: '#ff4d4f', fontSize: 10 },
          }] : []),
        ],
      },
    },
  ]

  if (abnormalData.length > 0) {
    seriesConfig.push({
      name: '异常',
      type: 'scatter',
      data: abnormalData,
      symbolSize: 12,
      itemStyle: { color: '#ff4d4f', borderColor: '#fff', borderWidth: 2 },
      z: 10,
    })
  }

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8e8e8',
      borderWidth: 1,
      textStyle: { color: '#333', fontSize: 13 },
      formatter: (params: any) => {
        const arr = Array.isArray(params) ? params : [params]
        const date = arr[0]?.axisValue || ''
        let html = `<div style="font-weight:600;margin-bottom:6px">${date}</div>`
        arr.forEach((p: any) => {
          if (p.value == null || p.seriesName === '异常') return
          const status = statuses[p.dataIndex]
          const dot = status === 'normal' ? '🟢' : status === 'high' ? '🔴' : '🟡'
          const label = status === 'normal' ? '正常' : status === 'high' ? '偏高' : '偏低'
          html += `<div style="display:flex;align-items:center;gap:6px;margin:3px 0">
            ${dot} <span>${p.seriesName}：${p.value} ${data.unit}</span>
            <span style="color:#999;font-size:11px">(${label})</span>
          </div>`
        })
        if (minRef != null || maxRef != null) {
          html += `<div style="margin-top:6px;padding-top:6px;border-top:1px solid #f0f0f0;color:#999;font-size:11px">参考：${refRange}</div>`
        }
        return html
      },
    },
    grid: { left: 60, right: 30, top: 30, bottom: 50 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11, rotate: dates.length > 6 ? 30 : 0 },
    },
    yAxis: {
      type: 'value',
      name: data.unit,
      nameTextStyle: { color: '#999', fontSize: 11 },
      min: yMin,
      max: yMax,
      splitLine: { lineStyle: { color: '#f5f5f5', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11 },
    },
    series: seriesConfig,
  }

  singleChart.setOption(option, true)
}

// ===== 图表渲染：多指标对比 =====
function renderCategoryChart() {
  if (!categoryChartRef.value || categoryTrendData.value.length === 0) return
  if (categoryChart) categoryChart.dispose()
  categoryChart = echarts.init(categoryChartRef.value)

  const allDates = new Set<string>()
  categoryTrendData.value.forEach((item: any) => {
    // 每个指标的数据点也按日期排序
    if (item.dataPoints) {
      item.dataPoints.sort((a: any, b: any) => a.date.localeCompare(b.date))
    }
    item.dataPoints?.forEach((p: any) => allDates.add(p.date))
  })
  const dates = Array.from(allDates).sort()

  const colors = ['#1890ff', '#13c2c2', '#52c41a', '#faad14', '#722ed1', '#eb2f96', '#fa541c']

  const series = categoryTrendData.value.map((item: any, idx: number) => {
    const data = dates.map(date => {
      const point = item.dataPoints?.find((p: any) => p.date === date)
      return point ? point.value : null
    })
    const color = colors[idx % colors.length]
    return {
      name: item.standardName,
      type: 'line',
      data,
      connectNulls: true,
      smooth: 0.3,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color },
      itemStyle: { color, borderWidth: 2, borderColor: '#fff' },
      emphasis: { lineStyle: { width: 3 } },
    }
  })

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8e8e8',
      borderWidth: 1,
      textStyle: { color: '#333', fontSize: 13 },
    },
    legend: {
      data: categoryTrendData.value.map((item: any) => item.standardName),
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: '#666' },
    },
    grid: { left: 60, right: 30, top: 20, bottom: 50 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11, rotate: dates.length > 6 ? 30 : 0 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f5f5f5', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11 },
    },
    series,
  }

  categoryChart.setOption(option, true)
}

// ===== 窗口缩放 =====
function handleResize() {
  singleChart?.resize()
  categoryChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  fetchIndicators()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  singleChart?.dispose()
  categoryChart?.dispose()
})
</script>

<style scoped>
/* ===== Apple Design System ===== */
.trend-container {
  padding: 4px 0;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 顶部控制栏 — 毛玻璃材质 */
.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-radius: 16px;
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.page-subtitle {
  font-size: 13px;
  color: #86868b;
  font-weight: 400;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 200px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: rgba(118, 118, 128, 0.12);
  border: none;
  box-shadow: none;
  transition: all 0.2s ease;
}

.search-input :deep(.el-input__wrapper:focus-within) {
  background: rgba(118, 118, 128, 0.08);
  box-shadow: 0 0 0 3px rgba(0, 125, 250, 0.2);
}

.filter-btn {
  border: none;
  background: rgba(118, 118, 128, 0.12);
  color: #1d1d1f;
  transition: all 0.15s ease;
}

.filter-btn:active {
  transform: scale(0.95);
}

/* 分类标签组 */
.category-tags {
  margin-bottom: 20px;
}

.tags-scroll {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-tag {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 10px;
  background: rgba(118, 118, 128, 0.08);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  font-size: 13px;
  font-weight: 500;
  color: #1d1d1f;
  border: none;
  -webkit-user-select: none;
  user-select: none;
}

.cat-tag:active {
  transform: scale(0.96);
}

.cat-tag:hover {
  background: rgba(118, 118, 128, 0.16);
}

.cat-tag.active {
  background: #007aff;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 122, 255, 0.3);
}

.cat-icon {
  font-size: 14px;
}

/* 已选指标标签 */
.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
  min-height: 32px;
}

.indicator-tag {
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  border-radius: 8px;
  font-weight: 500;
}

.indicator-tag:active {
  transform: scale(0.95);
}

.abnormal-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ff3b30;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  margin-left: 4px;
  line-height: 1;
}

.tag-fade-enter-active,
.tag-fade-leave-active {
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.tag-fade-enter-from,
.tag-fade-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

/* 指标选择器 */
.indicator-selector {
  margin-bottom: 20px;
}

.indicator-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 10px;
}

.indicator-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 14px 14px 12px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
  overflow: hidden;
  -webkit-user-select: none;
  user-select: none;
}

.indicator-card:active {
  transform: scale(0.97);
}

.indicator-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.1);
}

.indicator-card.selected {
  background: #f0f5ff;
  border-color: #007aff;
  box-shadow: 0 2px 10px rgba(0, 122, 255, 0.15);
}

.indicator-card.abnormal {
  background: #fff5f5;
  border-color: rgba(255, 59, 48, 0.2);
}

.indicator-card.abnormal.selected {
  background: #fff0f0;
  border-color: #ff3b30;
}

.card-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #fff;
  background: #007aff;
  box-shadow: 0 1px 4px rgba(0, 122, 255, 0.3);
}

.card-name {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 2px;
  letter-spacing: -0.01em;
}

.card-category {
  font-size: 11px;
  color: #86868b;
  margin-bottom: 6px;
}

.card-value {
  font-size: 20px;
  font-weight: 700;
  color: #007aff;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.indicator-card.abnormal .card-value {
  color: #ff3b30;
}

.card-unit {
  font-size: 11px;
  font-weight: 500;
  color: #86868b;
}

.card-abnormal {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #ff3b30;
  padding: 2px 0;
  letter-spacing: 0.02em;
}

/* 高级筛选 */
.advanced-panel {
  margin-bottom: 20px;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.panel-body {
  padding: 16px 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.panel-group {
  min-width: 160px;
}

.group-label {
  font-size: 12px;
  font-weight: 600;
  color: #86868b;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-items :deep(.el-checkbox) {
  margin-right: 0;
}

.group-items :deep(.el-checkbox__label) {
  font-size: 13px;
  font-weight: 500;
}

/* 图表区域 */
.chart-area {
  min-height: 400px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 350px;
}

.empty-icon {
  font-size: 72px;
  opacity: 0.2;
}

.chart-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 24px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.chart-card-header {
  margin-bottom: 20px;
}

.chart-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.title-name {
  font-size: 18px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
}

.title-count {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
}

.chart-meta {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #86868b;
  font-weight: 400;
}

.chart-canvas {
  width: 100%;
  height: 400px;
}

/* 响应式 */
@media (max-width: 768px) {
  .trend-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .header-right {
    width: 100%;
  }

  .search-input {
    flex: 1;
  }

  .indicator-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 8px;
  }

  .chart-canvas {
    height: 300px;
  }

  .page-title {
    font-size: 20px;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .cat-tag,
  .indicator-card,
  .indicator-tag {
    transition: opacity 0.2s ease;
    transform: none !important;
  }

  .tag-fade-enter-active,
  .tag-fade-leave-active {
    transition: opacity 0.2s ease;
  }
}
</style>
