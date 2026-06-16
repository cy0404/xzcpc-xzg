/**
 * 外网连通性测试 — 模拟 wx.request
 * 用法：node test-httpbin.js
 */
const c = {
  g: (s) => `\x1b[32m${s}\x1b[0m`,
  r: (s) => `\x1b[31m${s}\x1b[0m`,
  y: (s) => `\x1b[33m${s}\x1b[0m`,
  b: (s) => `\x1b[1m${s}\x1b[0m`,
}

async function wxRequest({ url, method = 'GET' }) {
  console.log(c.b(`\n>>> ${method} ${url}`))
  try {
    const res = await fetch(url, { method, signal: AbortSignal.timeout(10000) })
    const text = await res.text()
    let data
    try { data = JSON.parse(text) } catch { data = text }
    console.log(c.g(`✓ 成功 — 状态码: ${res.status}`))
    console.log('origin:', data.origin || data.url || '(无)')
    console.log('响应预览:', JSON.stringify(data).substring(0, 300))
    return { ok: true, data }
  } catch (e) {
    console.log(c.r(`✗ 失败 — ${e.message}`))
    return { ok: false, error: e.message }
  }
}

async function main() {
  console.log(c.b('🌐 外网连通性测试\n'))

  // 1. httpbin（通用测试）
  const r1 = await wxRequest({ url: 'https://httpbin.org/get' })

  // 2. xzcp.ink（你的线上服务器）
  const r2 = await wxRequest({ url: 'https://www.xzcp.ink/storeInventory/api/mp' })

  console.log(c.b('\n──────────────'))
  console.log(`httpbin:  ${r1.ok ? c.g('✓ 通') : c.r('✗ 不通')}`)
  console.log(`xzcp.ink: ${r2.ok ? c.g('✓ 通') : c.r('✗ 不通 — DNS 解析到 198.18.0.17（保留地址）')}`)

  if (r1.ok && !r2.ok) {
    console.log(c.y('\n👉 结论：本机外网正常，xzcp.ink 域名解析异常'))
    console.log('   请检查 hosts 文件、代理/VPN、DNS 设置')
  }
}

main()
