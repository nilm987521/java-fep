import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const username = ref<string | null>(localStorage.getItem('username'))
  const role = ref<string | null>(localStorage.getItem('role'))

  const isAuthenticated = computed(() => !!token.value)

  function login(credentials: { username: string; password: string }) {
    // Mock login - in production, call API
    token.value = 'mock-token-' + Date.now()
    username.value = credentials.username
    role.value = 'admin'

    localStorage.setItem('token', token.value)
    localStorage.setItem('username', username.value)
    localStorage.setItem('role', role.value)

    return Promise.resolve({ success: true })
  }

  function logout() {
    token.value = null
    username.value = null
    role.value = null

    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
  }

  return {
    token,
    username,
    role,
    isAuthenticated,
    login,
    logout,
  }
})
