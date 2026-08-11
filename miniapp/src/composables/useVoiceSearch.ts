import { ref } from 'vue'

let voiceManager: any = null
let voiceManagerReady = false
let siSessionActive = false
/** 平台侧隐私协议是否已同意（体验版/正式版共用，同 AppID） */
let privacyGranted = false

/** 页面进入时预读，避免体验版已授权、正式版首按仍走「仅隐私」分支 */
function refreshVoiceAuthState() {
  uni.getPrivacySetting({
    success(res) {
      if (!res.needAuthorization) privacyGranted = true
    },
  })
}

function mapVoiceError(res: any) {
  const msg = String(res?.msg || res?.errMsg || '')
  const code = res?.retcode
  if (code === -30011 || msg.includes('recognition finished')) {
    return '上次识别未结束，请 1 秒后再试'
  }
  if (msg.includes('privacy') || msg.includes('banned')) {
    return '语音功能未开通，请联系管理员配置麦克风隐私协议'
  }
  if (msg.includes('record failed') || msg.includes('record manager')) {
    return '录音启动失败，请检查麦克风权限'
  }
  if (code === -30001) return '未获得麦克风权限'
  return msg || '识别失败'
}

/** 引导去小程序设置页开启 scope.record（与手机「微信→麦克风」是不同项） */
function showMiniProgramMicGuide() {
  uni.showModal({
    title: '需要开启麦克风',
    content: '请在小程序设置中开启「麦克风」权限。\n\n说明：手机「设置→微信→麦克风」已开启，仍需在本小程序里单独授权。',
    confirmText: '去设置',
    cancelText: '取消',
    success(res) {
      if (res.confirm) uni.openSetting({})
    },
  })
}

function isMicPermissionError(res: any) {
  const msg = String(res?.msg || res?.errMsg || '')
  const code = res?.retcode
  return code === -30001 || msg.includes('record failed') || msg.includes('record manager')
}

function getVoiceManager() {
  if (voiceManager) return voiceManager
  // #ifdef MP-WEIXIN
  try {
    // @ts-ignore
    voiceManager = requirePlugin('WechatSI').getRecordRecognitionManager()
    voiceManagerReady = true
  } catch {
    voiceManagerReady = false
  }
  // #endif
  return voiceManager
}

function forceStopWechatSI() {
  if (!voiceManager) return
  try { voiceManager.stop() } catch { /* ignore */ }
  siSessionActive = false
}

function resetVoiceManager() {
  forceStopWechatSI()
  voiceManager = null
  voiceManagerReady = false
}

function ensurePrivacyAuthorized(): Promise<boolean> {
  if (privacyGranted) return Promise.resolve(true)
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    uni.getPrivacySetting({
      success(res) {
        if (!res.needAuthorization) {
          privacyGranted = true
          resolve(true)
          return
        }
        uni.requirePrivacyAuthorize({
          success: () => {
            privacyGranted = true
            resolve(true)
          },
          fail: () => resolve(false),
        })
      },
      fail: () => {
        privacyGranted = true
        resolve(true)
      },
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(true)
    // #endif
  })
}

export function useVoiceSearch(onResult: (text: string) => void) {
  const recording = ref(false)
  const voiceLoading = ref(false)
  const voiceRecognizing = ref(false)
  const voiceCooldown = ref(false)
  const voiceCancelled = ref(false)
  const touchStartY = ref(0)

  let cooldownSafetyTimer: ReturnType<typeof setTimeout> | null = null
  let loadingGuardTimer: ReturnType<typeof setTimeout> | null = null

  function clearLoadingGuard() {
    if (loadingGuardTimer) {
      clearTimeout(loadingGuardTimer)
      loadingGuardTimer = null
    }
  }

  function cleanupVoice() {
    recording.value = false
    voiceLoading.value = false
    clearLoadingGuard()
  }

  function fullResetVoice() {
    forceStopWechatSI()
    cleanupVoice()
    clearCooldownLock()
  }

  function startLoadingGuard() {
    clearLoadingGuard()
    loadingGuardTimer = setTimeout(() => {
      if (voiceLoading.value && !recording.value) {
        resetVoiceManager()
        cleanupVoice()
        uni.showToast({ title: '启动超时，请松开后重试', icon: 'none' })
      }
    }, 6000)
  }

  function startCooldownLock() {
    voiceCooldown.value = true
    if (cooldownSafetyTimer) clearTimeout(cooldownSafetyTimer)
    cooldownSafetyTimer = setTimeout(() => {
      voiceCooldown.value = false
      voiceRecognizing.value = false
    }, 1200)
  }

  function clearCooldownLock() {
    voiceCooldown.value = false
    voiceRecognizing.value = false
    if (cooldownSafetyTimer) {
      clearTimeout(cooldownSafetyTimer)
      cooldownSafetyTimer = null
    }
  }

  function handleVoiceError(res: any) {
    siSessionActive = false
    clearCooldownLock()
    cleanupVoice()
    if (res?.retcode === -30011) resetVoiceManager()

    if (isMicPermissionError(res)) {
      uni.getSetting({
        success(setting) {
          const recordAuth = setting.authSetting['scope.record']
          if (recordAuth === false) {
            showMiniProgramMicGuide()
          } else if (recordAuth === true) {
            // 小程序麦克风已授权仍失败 → 多为正式版隐私接口未在公众平台配置
            uni.showModal({
              title: '录音启动失败',
              content: '麦克风已授权但仍无法录音。请确认管理员已在微信公众平台「用户隐私保护指引」中勾选并审核通过「麦克风/录音」；或删除小程序后重新进入再试。',
              confirmText: '我知道了',
              showCancel: false,
            })
          } else {
            // 未弹出过小程序麦克风授权（体验版授权后正式版偶发）
            showMiniProgramMicGuide()
          }
        },
        fail() {
          uni.showToast({ title: mapVoiceError(res), icon: 'none', duration: 2500 })
        },
      })
      return
    }
    uni.showToast({ title: mapVoiceError(res), icon: 'none', duration: 2500 })
  }

  function bindWechatSIHandlers(mgr: any) {
    mgr.onStart = () => {
      clearLoadingGuard()
      siSessionActive = true
      recording.value = true
      voiceLoading.value = false
    }
    mgr.onStop = (res: any) => {
      siSessionActive = false
      clearCooldownLock()
      if (res?.result && !voiceCancelled.value) onResult(res.result)
    }
    mgr.onError = (res: any) => handleVoiceError(res)
  }

  /** 在用户 touch 同步栈内启动（正式版要求）；由插件触发小程序级麦克风授权 */
  function beginWechatSISync() {
    if (siSessionActive) forceStopWechatSI()

    const mgr = getVoiceManager()
    if (!mgr || !voiceManagerReady) {
      cleanupVoice()
      uni.showToast({ title: '语音插件未加载', icon: 'none' })
      return
    }

    voiceLoading.value = true
    startLoadingGuard()
    bindWechatSIHandlers(mgr)
    try {
      mgr.start({ duration: 30000, lang: 'zh_CN' })
    } catch {
      siSessionActive = false
      cleanupVoice()
      uni.showToast({ title: '启动录音失败', icon: 'none' })
    }
  }

  /** 首次：仅处理隐私协议，不在异步里申请 scope.record */
  async function requestPrivacyOnly() {
    voiceLoading.value = true
    startLoadingGuard()

    const privacyOk = await ensurePrivacyAuthorized()
    cleanupVoice()

    if (!privacyOk) {
      uni.showToast({ title: '需同意隐私政策后使用语音', icon: 'none' })
      return
    }
    uni.showToast({ title: '请再次按住说话', icon: 'none', duration: 2500 })
  }

  function startVoice(e: any) {
    e?.preventDefault?.()
    if (voiceLoading.value || recording.value || voiceRecognizing.value || voiceCooldown.value) return
    voiceCancelled.value = false
    const t = e.touches?.[0]
    if (t) touchStartY.value = t.clientY

    if (!privacyGranted) {
      requestPrivacyOnly()
      return
    }

    // 隐私已同意：同步启动，由 WechatSI 触发小程序麦克风授权
    beginWechatSISync()
  }

  function onVoiceMove(e: any) {
    if (!recording.value) return
    const touch = e.touches?.[0]
    if (!touch) return
    voiceCancelled.value = touchStartY.value - touch.clientY > 65
  }

  function cancelVoice() {
    if (voiceLoading.value && !recording.value) {
      fullResetVoice()
      return
    }
    if (!recording.value) return
    recording.value = false
    startCooldownLock()
    forceStopWechatSI()
  }

  function stopVoice(e: any) {
    e?.preventDefault?.()
    if (!recording.value) return
    if (voiceCancelled.value) {
      recording.value = false
      startCooldownLock()
      forceStopWechatSI()
      uni.showToast({ title: '已取消', icon: 'none' })
    } else {
      recording.value = false
      voiceRecognizing.value = true
      forceStopWechatSI()
    }
  }

  function initVoice() {
    getVoiceManager()
    refreshVoiceAuthState()
  }

  return {
    recording,
    voiceLoading,
    voiceRecognizing,
    voiceCooldown,
    voiceCancelled,
    startVoice,
    onVoiceMove,
    stopVoice,
    cancelVoice,
    initVoice,
    refreshVoiceAuthState,
  }
}
