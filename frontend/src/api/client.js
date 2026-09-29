const TOKEN_KEY = 'gazellio_token'

export function getToken(){ return localStorage.getItem(TOKEN_KEY) }
export function setToken(token){ token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY) }

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(path, { ...options, headers, body: options.body && !(options.body instanceof FormData) && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body })
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
}

export const authApi = { login: (username,password) => api('/api/auth/login',{method:'POST',body:{username,password}}) }
