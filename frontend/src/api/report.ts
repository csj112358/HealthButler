import request from '@/utils/request'

export function uploadReport(userId: number, file: File) {
  const formData = new FormData()
  formData.append('userId', userId.toString())
  formData.append('file', file)
  return request({
    url: '/report/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function submitTextReport(text: string) {
  return request({
    url: '/report/text',
    method: 'post',
    data: { text },
  })
}

export function confirmReport(reportId: number, data?: any) {
  return request({
    url: `/report/confirm/${reportId}`,
    method: 'post',
    data,
  })
}

export function updateReport(reportId: number, data: any) {
  return request({
    url: `/report/update/${reportId}`,
    method: 'put',
    data,
  })
}

export function deleteReport(reportId: number) {
  return request({
    url: `/report/delete/${reportId}`,
    method: 'delete',
  })
}

export function getUserReports(userId: number) {
  return request({
    url: `/report/list/${userId}`,
    method: 'get',
  })
}

export function getReportDetail(reportId: number) {
  return request({
    url: `/report/detail/${reportId}`,
    method: 'get',
  })
}

export function getTrendData(userId: number, standardName: string) {
  return request({
    url: `/report/trend/${userId}/${standardName}`,
    method: 'get',
  })
}

export function getCategoryTrendData(userId: number, category: string) {
  return request({
    url: `/report/trend/category/${userId}/${category}`,
    method: 'get',
  })
}

export function generateDiagnosis(userId: number, reportIds: number[]) {
  return request({
    url: '/report/diagnosis',
    method: 'post',
    params: { userId, reportIds: reportIds.join(',') },
  })
}

export function getUserIndicators(userId: number) {
  return request({
    url: `/report/indicators/${userId}`,
    method: 'get',
  })
}