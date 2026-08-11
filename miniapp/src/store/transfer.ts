import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface SelectedTransferItem {
  materialId: string
  materialName: string
  qmCode: string
  spec: string
  baseUnit: string
  unitPrices: Record<string, number>
  selectedUnit: string
  qty: number
}

export const useTransferStore = defineStore('transfer', () => {
  const selectedItems = ref<SelectedTransferItem[]>([])

  const totalCount = computed(() => selectedItems.value.reduce((s, i) => s + i.qty, 0))
  const totalAmount = computed(() => selectedItems.value.reduce((s, i) => {
    const price = i.unitPrices[i.selectedUnit] || 0
    return s + price * i.qty
  }, 0))

  /** Add item or increment quantity */
  function addItem(item: Omit<SelectedTransferItem, 'qty'>) {
    const existing = selectedItems.value.find(i => i.materialId === item.materialId && i.selectedUnit === item.selectedUnit)
    if (existing) {
      existing.qty += 1
    } else {
      selectedItems.value.push({ ...item, qty: 1 })
    }
  }

  /** Update selected unit for an item */
  function updateItemUnit(materialId: string, unit: string) {
    const item = selectedItems.value.find(i => i.materialId === materialId)
    if (item) item.selectedUnit = unit
  }

  /** Decrement qty by 1, remove if reaches 0 */
  function decrementItem(materialId: string) {
    const item = selectedItems.value.find(i => i.materialId === materialId)
    if (!item) return
    if (item.qty <= 1) {
      selectedItems.value = selectedItems.value.filter(i => i.materialId !== materialId)
    } else {
      item.qty -= 1
    }
  }

  /** Remove all units of a materialId */
  function removeItem(materialId: string) {
    selectedItems.value = selectedItems.value.filter(i => i.materialId !== materialId)
  }

  /** Remove a single unit entry */
  function removeItemByUnit(materialId: string, unit: string) {
    selectedItems.value = selectedItems.value.filter(i => !(i.materialId === materialId && i.selectedUnit === unit))
  }

  /** Clear all selected items */
  function clearItems() {
    selectedItems.value = []
  }

  /** Get items for form submission */
  function getFormItems() {
    return selectedItems.value.map(i => {
      const price = i.unitPrices[i.selectedUnit] || 0
      return {
        materialName: i.materialName,
        spec: i.spec,
        unit: i.selectedUnit,
        transferQty: i.qty,
        baseUnit: i.baseUnit,
        baseQty: i.baseUnit === i.selectedUnit ? i.qty : i.qty,
        inputUnit: i.selectedUnit,
        inputQty: i.qty,
        unitPrice: price,
        remark: '',
      }
    })
  }

  return {
    selectedItems,
    totalCount,
    totalAmount,
    addItem,
    decrementItem,
    removeItem,
    removeItemByUnit,
    clearItems,
    getFormItems,
    updateItemUnit,
  }
})
