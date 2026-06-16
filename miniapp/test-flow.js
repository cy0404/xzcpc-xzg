/**
 * 盘点工具 — 小程序端全流程测试脚本
 * 用法：node test-flow.js [BASE_URL]
 * 示例：node test-flow.js http://192.168.110.213:30261/storeInventory/api/mp
 *
 * 测试流程：登录 → 查任务 → 查分区物料 → 录入 → 保存 → 汇总 → 结果
 */

const BASE = process.argv[2] || 'http://192.168.110.213:30261/storeInventory/api/mp'

const C = {
  reset:  '\x1b[0m',
  bold:   '\x1b[1m',
  dim:    '\x1b[2m',
  green:  '\x1b[32m',
  red:    '\x1b[31m',
  yellow: '\x1b[33m',
  cyan:   '\x1b[36m',
  blue:   '\x1b[34m',
}

let token = ''
let pass = 0
let fail = 0

function log(icon, label, msg, color = '') {
  console.log(`  ${icon} ${color}${label}${C.reset}: ${C.dim}${msg}${C.reset}`)
}

async function req(method, path, body = null) {
  const url = BASE + path
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
    signal: AbortSignal.timeout(15000),
  }
  if (token) opts.headers['Authorization'] = 'Bearer ' + token
  if (body) opts.body = JSON.stringify(body)

  try {
    const res = await fetch(url, opts)
    const text = await res.text()
    let json = null
    try { json = JSON.parse(text) } catch {}
    return { status: res.status, ok: res.ok, json, text }
  } catch (e) {
    return { status: 0, ok: false, json: null, text: '', error: e.message }
  }
}

async function test(step, label, method, path, body, expectOk = true) {
  console.log(`\n${C.cyan}━━━ ${step}. ${label} ━━━${C.reset}`)
  console.log(`  ${C.bold}${method}${C.reset} ${path}`)
  if (body) console.log(`  Body: ${JSON.stringify(body)}`)

  const r = await req(method, path, body)

  if (r.error) {
    console.log(`  ${C.red}✗ 网络错误: ${r.error}${C.reset}`)
    fail++
    return r
  }

  const okIcon = (expectOk ? r.ok : !r.ok) ? `${C.green}✓${C.reset}` : `${C.red}✗${C.reset}`
  console.log(`  ${okIcon} 状态码: ${r.status}`)

  if (r.json) {
    const { code, message, data } = r.json
    console.log(`  code: ${code ?? '-'}  message: ${message ?? '-'}`)
    if (data !== undefined && data !== null) {
      if (typeof data === 'object' && !Array.isArray(data)) {
        const keys = Object.keys(data)
        console.log(`  data keys: [${keys.join(', ')}]`)
      } else if (Array.isArray(data)) {
        console.log(`  data: 数组, 长度=${data.length}`)
      } else {
        console.log(`  data: ${data}`)
      }
    }
  }

  if ((expectOk && r.ok) || (!expectOk && !r.ok)) pass++
  else fail++

  return r
}

async function main() {
  console.log(C.bold('\n📦 盘点工具 — 小程序端全流程测试'))
  console.log(`服务器: ${C.blue}${BASE}${C.reset}`)
  console.log(`时间:   ${new Date().toISOString()}`)

  // ═══════════════════════════════════════
  // 1. 认证
  // ═══════════════════════════════════════
  console.log(`\n${C.yellow}${C.bold}▶ 认证模块${C.reset}`)

  const r1 = await test('1', '微信登录 POST /auth/wx/login', 'POST', '/auth/wx/login', {
    code: 'test_code_' + Date.now(),
    wxNickname: '测试用户',
  })
  if (r1.json?.data?.token) {
    token = r1.json.data.token
    log('🔑', '获取到 token', token.substring(0, 20) + '...', C.green)
  } else {
    log('⚠️', '未获取到 token', '后续接口可能返回 401', C.yellow)
  }

  await test('2', '获取当前用户 GET /auth/me', 'GET', '/auth/me')

  // ═══════════════════════════════════════
  // 2. 任务
  // ═══════════════════════════════════════
  console.log(`\n${C.yellow}${C.bold}▶ 任务模块${C.reset}`)

  const r3 = await test('3', '任务列表 GET /tasks', 'GET', '/tasks')
  const tasks = r3.json?.data ?? []
  let taskId = null
  if (tasks.length > 0) {
    taskId = tasks[0].id || tasks[0].taskId
    log('📋', '第一个任务', `id=${taskId}, name=${tasks[0].taskName || tasks[0].name || '-'}`, C.green)
  } else {
    log('⚠️', '无任务', '跳过任务相关测试', C.yellow)
  }

  if (taskId) {
    const r4 = await test('4', `任务详情 GET /tasks/${taskId}`, 'GET', `/tasks/${taskId}`)

    // ═══════════════════════════════════════
    // 3. 分区物料录入
    // ═══════════════════════════════════════
    console.log(`\n${C.yellow}${C.bold}▶ 分区物料录入${C.reset}`)

    const zones = r4.json?.data?.zones ?? []
    let zoneId = null
    if (zones.length > 0) {
      zoneId = zones[0].id || zones[0].zoneId
      log('📍', '第一个分区', `id=${zoneId}, name=${zones[0].zoneName || zones[0].name || '-'}`, C.green)
    }

    if (zoneId) {
      // 获取分区物料
      const r5 = await test('5', `分区物料 GET /tasks/${taskId}/zones/${zoneId}/materials`, 'GET',
        `/tasks/${taskId}/zones/${zoneId}/materials`)

      const materials = r5.json?.data ?? []
      let materialId = null
      if (materials.length > 0) {
        materialId = materials[0].materialId
        log('📦', '第一个物料', `id=${materialId}, name=${materials[0].materialName || '-'}`, C.green)
        if (materials[0].inventoryRule) {
          const rule = materials[0].inventoryRule
          log('📐', '盘点规则', `baseUnit=${rule.baseUnit}, units=${rule.units?.map(u=>u.unitName).join(',') || '-'}`, C.blue)
        }
      }

      if (materialId) {
        // 单条录入
        await test('6', `单条录入 POST /tasks/${taskId}/zones/${zoneId}/item-save`, 'POST',
          `/tasks/${taskId}/zones/${zoneId}/item-save`, {
            materialId,
            qty: 10,
            inputMode: 'unit',
            originalQty: 10,
            originalUnit: materials[0]?.inventoryRule?.baseUnit || materials[0]?.unit || '个',
          })

        // 再次获取，验证录入结果
        await test('7', `验证录入 GET /tasks/${taskId}/zones/${zoneId}/materials`, 'GET',
          `/tasks/${taskId}/zones/${zoneId}/materials`)
      }

      // 批量保存分区
      await test('8', `保存分区 POST /tasks/${taskId}/zones/${zoneId}/save`, 'POST',
        `/tasks/${taskId}/zones/${zoneId}/save`, { items: [] })
    }

    // ═══════════════════════════════════════
    // 4. 汇总与结果
    // ═══════════════════════════════════════
    console.log(`\n${C.yellow}${C.bold}▶ 汇总与结果${C.reset}`)

    await test('9', `汇总 GET /tasks/${taskId}/summary`, 'GET', `/tasks/${taskId}/summary`)
    await test('10', `结果 GET /tasks/${taskId}/result`, 'GET', `/tasks/${taskId}/result`)
  }

  // ═══════════════════════════════════════
  // 5. 错误场景
  // ═══════════════════════════════════════
  console.log(`\n${C.yellow}${C.bold}▶ 错误场景${C.reset}`)

  await test('11', '不存在任务 GET /tasks/99999', 'GET', '/tasks/99999')

  if (taskId) {
    await test('12', '无token查任务 GET /tasks (预期401)', 'GET', '/tasks', null, false)
  }

  // ═══════════════════════════════════════
  // 汇总
  // ═══════════════════════════════════════
  console.log(`\n${C.bold}══════════════════════════════════${C.reset}`)
  console.log(`${C.green}通过: ${pass}${C.reset}  |  ${C.red}失败: ${fail}${C.reset}  |  总计: ${pass + fail}`)
  if (fail === 0) {
    console.log(`${C.green}${C.bold}✅ 全部通过${C.reset}`)
  } else {
    console.log(`${C.yellow}⚠️ 有 ${fail} 个请求不符合预期${C.reset}`)
  }
  console.log(`${C.bold}══════════════════════════════════${C.reset}\n`)
}

main()
