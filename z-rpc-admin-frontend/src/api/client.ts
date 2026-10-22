import axios from 'axios'

// 默认端口跟 z-rpc-admin 的 application.yml 对齐(server.port: 19090);
// 旧的 9090 是过期读数, 留着会让控制台默认连不上后端。
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:19090'

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
