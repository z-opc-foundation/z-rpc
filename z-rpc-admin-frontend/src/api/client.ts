import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090'

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API error:', error)
    return Promise.reject(error)
  }
)

export default client
