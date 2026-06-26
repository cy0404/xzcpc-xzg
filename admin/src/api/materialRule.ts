import api from './index'

export interface MaterialRuleUnit {
  unitName: string
  baseUnit?: boolean
}

export interface MaterialUnitConversion {
  fromQuantity: number
  fromUnit: string
  toQuantity: number
  toUnit: string
}

export interface MaterialWeightConversion {
  weightQuantity: number
  weightUnit: string
  countQuantity: number
  countUnit: string
}

export interface MaterialRuleRecord {
  materialId: string
  materialName: string
  qmCode?: string
  parentCategory?: string
  category?: string
  spec?: string
  ruleId?: string
  baseUnit?: string
  stockUnit?: string
  unitPrice?: number
  updatedAt?: string
  ruleStatus: 'maintained' | 'pending'
  units: MaterialRuleUnit[]
  conversions: MaterialUnitConversion[]
  weightConversions: MaterialWeightConversion[]
}

export function getMaterialRules(params?: {
  keyword?: string
  parentCategory?: string
  category?: string
  baseUnit?: string
  conversionType?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/material-rules', { params })
}

export function getMaterialRuleDetail(materialId: string) {
  return api.get(`/material-rules/${materialId}`)
}

export function getBaseUnits() {
  return api.get<string[]>('/material-rules/base-units')
}

export function saveMaterialRule(materialId: string, data: {
  baseUnit: string
  stockUnit: string
  unitPrice: number
  units: MaterialRuleUnit[]
  conversions: MaterialUnitConversion[]
  weightConversions: MaterialWeightConversion[]
}) {
  return api.put(`/material-rules/${materialId}`, data)
}
