/**
 * API 连通性测试脚本
 * 用法：node test-api.js
 * 用途：测试 https://www.xzcp.ink 线上 API 是否可用，排查是否用了 mock 数据
 */
const BASE = 'https://www.xzcp.ink/storeInventory/api/mp'

// 帮你在终端输出彩色的工具函数
const colors = {
  green: (s) => `\x1b[32m${s}\x1b[0m`,
  red: (s) => `\x1b[31m${s}\x1b[0m`,
  yellow: (s) => `\x1b[33m${s}\x1b[0m`,
  cyan: (s) => `\x1b[36m${s}\x1b[0m`,
  bold: (s) => `\x1b[1m${s}\x1b[0m`,
}

async function test(label, url, options = {}) {
  const method = options.method || 'GET'
  console.log('')
  console.log(colors.cyan('━━━ ' + label + ' ━━━'))
  console.log(colors.bold(method + ' ') + url)
  if (options.body) {
    console.log('Body: ' + JSON.stringify(options.body))
  }
  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json', ...options.headers },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: AbortSignal.timeout(15000),
    })
    const contentType = res.headers.get('content-type') || ''
    const isJson = contentType.includes('json')
    const raw = await res.text()

    console.log(colors.green('✓ 状态码: ' + res.status))
    console.log('Content-Type: ' + contentType)

    if (isJson) {
      try {
        const json = JSON.parse(raw)
        console.log('响应 JSON:')
        console.log(JSON.stringify(json, null, 2))
        return { ok: true, json }
      } catch {
        console.log('响应(前800字符):')
        console.log(raw.substring(0, 800))
        return { ok: true, raw }
      }
    } else {
      console.log('响应(前800字符):')
      console.log(raw.substring(0, 800))
      return { ok: true, raw }
    }
  } catch (e) {
    console.log(colors.red('✗ 请求失败: ' + e.message))
    return { ok: false, error: e.message }
  }
}

async function main() {
  console.log(colors.bold('\n🔧 盘点工具 API 连通性测试'))
  console.log('目标服务器: ' + BASE)
  console.log('测试时间: ' + new Date().toISOString())
  console.log('')

  let pass = 0
  let fail = 0

  // 测试 1：GET 根路径（纯连通性测试）
  const r1 = await test('GET 根路径（连通性测试）', BASE)
  r1.ok ? pass++ : fail++

  // 测试 2：GET /auth/wx/login（原接口，GET 方式）
  const r2 = await test('GET /auth/wx/login', BASE + '/auth/wx/login')
  r2.ok ? pass++ : fail++

  // 测试 3：POST /auth/wx/login（真实登录方式）
  const r3 = await test('POST /auth/wx/login', BASE + '/auth/wx/login', {
    method: 'POST',
    body: { code: 'test_code_123', wxNickname: 'test_user' },
  })
  r3.ok ? pass++ : fail++

  // 测试 4：GET /auth/me（需要 token，预期 401）
  const r4 = await test('GET /auth/me（预期 401）', BASE + '/auth/me')
  r4.ok ? pass++ : fail++

  // 汇总
  console.log('')
  console.log(colors.bold('══════════════════════════════════'))
  console.log(`通过: ${pass}  |  失败: ${fail}  |  总计: ${pass + fail}`)
  if (fail === 0) {
    console.log(colors.green('✅ 全部通过 — 线上 API 可用'))
  } else {
    console.log(colors.yellow('⚠️ 有 ' + fail + ' 个请求失败'))
    console.log('')
    console.log('可能原因:')
    console.log('  1. 域名 DNS 解析异常（当前解析到 198.18.0.17，非正常公网 IP）')
    console.log('  2. 网络代理/VPN 拦截')
    console.log('  3. 服务器未启动或不可达')
    console.log('  4. 项目的 BASE_URL 配置有误')
    console.log('')
    console.log('排查建议:')
    console.log('  - 检查 ' + colors.bold('miniapp/src/utils/constants.ts') + ' 中的 BASE_URL')
    console.log('  - 确认本地后端是否启动: ' + colors.bold('cd server && mvn spring-boot:run'))
    console.log('  - 如果用本地后端，把 BASE_URL 改成 ' + colors.bold('http://localhost:8080/api/mp'))
  }
  console.log(colors.bold('══════════════════════════════════'))
}

main()
