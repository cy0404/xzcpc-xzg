import api from './index'

export function getCategories() {
  return api.get<string[]>('/materials/categories')
}

export function getParentCategories() {
  return api.get<string[]>('/materials/parent-categories')
}

