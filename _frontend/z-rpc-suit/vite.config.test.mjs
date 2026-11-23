import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import test from 'node:test'
import axios from 'axios'
import {resolveConfig} from 'vite'

const root = fileURLToPath(new URL('.', import.meta.url))
const source = relative => readFile(new URL(relative, import.meta.url), 'utf8')

// These are literal Vite proxy contexts, whose middleware uses startsWith.
// getUri only composes URLs; no request, server or backend is started here.
const matches = (proxy, url) => Object.keys(proxy).filter(prefix => url.startsWith(prefix))

test('RPC client URLs are covered by the suit proxy in both dependency modes', {timeout: 10000}, async t => {
    const api = await source('../z-rpc-component/src/services/api.js')
    const baseURL = api.match(/const request = createRequest\(\{baseURL:\s*'([^']+)'/)?.[1]
    assert.equal(baseURL, '/api')
    const paths = [...api.matchAll(/request\.(?:get|post|put|patch|delete)\(\s*'([^']+)'/g)].map(match => match[1])
    assert.equal(paths.length, 4, 'Review proxy coverage when the client endpoint set changes')
    const urls = paths.map(url => axios.getUri({baseURL, url}))
    assert.ok(urls.includes('/api/naming/listServices'))
    const status = await source('./src/Status.jsx')
    const actuator = [...status.matchAll(/fetch\('([^']+)'\)/g)].map(match => match[1])
    assert.deepEqual(actuator, ['/actuator/health', '/actuator/info'])

    const previous = process.env.LOCAL_SIBLINGS
    try {
        for (const mode of [undefined, '1']) {
            await t.test(mode === '1' ? 'LOCAL_SIBLINGS=1' : 'default (unset)', async () => {
                if (mode === undefined) delete process.env.LOCAL_SIBLINGS
                else process.env.LOCAL_SIBLINGS = mode
                const config = await resolveConfig({root, configFile: fileURLToPath(new URL('./vite.config.js', import.meta.url)), envFile: false, logLevel: 'silent'}, 'serve')
                const proxy = config.server.proxy
                for (const url of urls) {
                    assert.deepEqual(matches(proxy, url), ['/api/naming'], `${url} must reach the naming proxy`)
                    assert.deepEqual(matches(proxy, `${url}?probe=coverage`), ['/api/naming'])
                }
                for (const url of actuator) assert.deepEqual(matches(proxy, url), ['/actuator'])
                // LoginPage is currently local mock auth, not an /api/auth client.
                assert.deepEqual(proxy, {
                    '/api/naming': {target: 'http://localhost:8888', changeOrigin: true},
                    '/actuator': {target: 'http://localhost:8888', changeOrigin: true},
                })
                for (const url of ['/api/other', '/api/auth/login', '/naming/listServices', '/z-rpc/services', '/src/App.jsx']) {
                    assert.deepEqual(matches(proxy, url), [], `${url} must stay outside the proxy`)
                }
            })
        }
    } finally {
        if (previous === undefined) delete process.env.LOCAL_SIBLINGS
        else process.env.LOCAL_SIBLINGS = previous
    }
})
