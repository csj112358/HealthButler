import request from '@/utils/request'

export function login(data: { username: string; password: string }) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  })
}

export function register(data: {
  username: string
  password: string
  phone?: string
  gender?: number
  birthDate?: string
}) {
  return request({
    url: '/auth/register',
    method: 'post',
    data,
  })
}

export function refreshToken() {
  return request({
    url: '/auth/refresh',
    method: 'post',
  })
}