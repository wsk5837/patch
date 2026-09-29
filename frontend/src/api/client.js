const TOKEN_KEY = 'gazellio_token'
const DEFAULT_TIMEOUT_MS = 15000
const inFlightGets = new Map()

export function getToken(){ return localStorage.getItem(TOKEN_KEY) }
export function setToken(token){ token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY) }

async function request(path, options = {}) {
  const { timeout = DEFAULT_TIMEOUT_MS, ...fetchOptions } = options
  const headers = { ...(options.headers || {}) }
  if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(path, { ...fetchOptions, headers, signal: controller.signal, body: options.body && !(options.body instanceof FormData) && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body })
    if (res.status === 401) {
      setToken(null)
      window.dispatchEvent(new CustomEvent('gazellio:unauthorized'))
    }
    if (!res.ok) {
      let msg = `${res.status} ${res.statusText}`
      try { const data = await res.json(); msg = data.message || data.detail || data.error || msg } catch {}
      throw new Error(msg)
    }
    if (res.status === 204) return null
    const type = res.headers.get('content-type') || ''
    return type.includes('application/json') ? res.json() : res.text()
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('Request timed out')
    throw error
  } finally {
    window.clearTimeout(timer)
  }
}

export function api(path, options = {}) {
  const method = String(options.method || 'GET').toUpperCase()
  if (method !== 'GET') return request(path, options)
  const key = `${getToken() || 'anonymous'}:${path}`
  if (inFlightGets.has(key)) return inFlightGets.get(key)
  const pending = request(path, options).finally(() => inFlightGets.delete(key))
  inFlightGets.set(key, pending)
  return pending
}

export const authApi = { login: (username,password) => api('/api/auth/login',{method:'POST',body:{username,password}}) }
