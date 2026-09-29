import express from 'express'
import { createProxyMiddleware } from 'http-proxy-middleware'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const app = express()
const backend = process.env.BACKEND_HOSTPORT ? `http://${process.env.BACKEND_HOSTPORT}` : (process.env.BACKEND_URL || 'http://localhost:8080')

app.use('/api', createProxyMiddleware({ target: backend, changeOrigin: true, xfwd: true }))
app.use('/actuator', createProxyMiddleware({ target: backend, changeOrigin: true, xfwd: true }))
app.use(express.static(path.join(__dirname, 'dist'), { maxAge: '1h' }))
app.use((_req, res) => res.sendFile(path.join(__dirname, 'dist', 'index.html')))
const port = Number(process.env.PORT || 10000)
app.listen(port, '0.0.0.0', () => console.log(`Gazellio web listening on ${port}, backend=${backend}`))
