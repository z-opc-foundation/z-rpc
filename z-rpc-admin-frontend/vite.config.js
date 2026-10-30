import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import {safeEmptyOutDir} from '../build-tools/safe-vite-plugin.mjs'
// https://vitejs.dev/config/
export default defineConfig({
    plugins: [safeEmptyOutDir(), react()],
    server: {
        port: 5173,
        host: '0.0.0.0',
        open: false
    },
    build: {
        outDir: 'dist',
        sourcemap: false,
        chunkSizeWarningLimit: 1500
    }
});
