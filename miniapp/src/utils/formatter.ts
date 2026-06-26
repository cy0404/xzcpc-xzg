export function formatDate(dateStr: string | null): string {
  if (!dateStr) return '--'
  // 兼容 LocalDateTime.toString() 格式 "2026-05-30T15:30:00"
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (match) {
    return `${match[1]}-${match[2]}-${match[3]}`
  }
  return '--'
}

export function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '--'
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (match) {
    return `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}`
  }
  return '--'
}

export function calcProgress(entered: number, total: number): number {
  if (total === 0) return 0
  return Math.round((entered / total) * 100)
}

export function isDeadlineExpired(deadline: string | null): boolean {
  if (!deadline) return false
  // 后端返回 LocalDateTime.toString() 格式，手动解析避免 Safari/小程序兼容问题
  const match = deadline.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
  if (match) {
    const d = new Date(+match[1], +match[2] - 1, +match[3], +match[4], +match[5])
    return d.getTime() < Date.now()
  }
  return false
}

export function formatMoney(value: number | string | null | undefined): string {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00'
  return num.toFixed(2)
}

/** 物料展示名：如果二级分类是"半成品"，自动追加后缀 */
export function materialDisplayName(material: { materialName?: string; category?: string } | null | undefined): string {
  const name = material?.materialName || ''
  const cat = material?.category || ''
  if (cat === '半成品' && !name.includes('（半成品）')) {
    return name + '（半成品）'
  }
  return name
}
