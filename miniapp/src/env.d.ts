/// <reference types="@dcloudio/types" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare const uni: {
  request: (options: UniApp.RequestOptions) => any
  navigateTo: (options: UniApp.NavigateToOptions) => any
  switchTab: (options: UniApp.SwitchTabOptions) => any
  redirectTo: (options: UniApp.RedirectToOptions) => any
  navigateBack: (options?: UniApp.NavigateBackOptions) => any
  showToast: (options: UniApp.ShowToastOptions) => any
  showLoading: (options: UniApp.ShowLoadingOptions) => any
  hideLoading: () => void
  showModal: (options: UniApp.ShowModalOptions) => any
  login: (options?: any) => Promise<UniApp.LoginRes>
  setStorageSync: (key: string, data: any) => void
  getStorageSync: (key: string) => any
  removeStorageSync: (key: string) => void
  getSystemInfoSync: () => UniApp.GetSystemInfoResult
}
