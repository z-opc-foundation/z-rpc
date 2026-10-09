import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3030,
        fs: {allow: ['..']},
        proxy: {'/naming': {target: 'http://localhost:8888', changeOrigin: true}, '/actuator': {target: 'http://localhost:8888', changeOrigin: true}},
    },
})
