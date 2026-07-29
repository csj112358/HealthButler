import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref<number | null>(null)
  const username = ref('')

  async function login(credentials: { username: string; password: string }) {
    const res = await loginApi(credentials) as any
    token.value = res.data.token
    userId.value = res.data.userId
    username.value = res.data.username
    localStorage.setItem('token', res.data.token)
    return res
  }

  function logout() {
    token.value = ''
    userId.value = null
    username.value = ''
    localStorage.removeItem('token')
  }

  return {
    token,
    userId,
    username,
    login,
    logout,
  }
})