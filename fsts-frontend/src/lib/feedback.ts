import { ElMessage } from 'element-plus'

/**
 * 统一的操作反馈。
 *
 * <p>Element Plus 的默认提示 3 秒后消失，而"失败原因"往往需要用户逐字读完再决定下一步；
 * 时间太短会让人以为"点了没反应"（这也是本项目实际踩过的坑：后端返回 400，
 * 但提示一闪而过，用户只看到弹窗没关）。因此错误提示统一延长到 6 秒并允许手动关闭。
 *
 * <p>成功提示保持默认时长——成功的提示不需要用户反复确认。
 */
export function showError(message: string): void {
  ElMessage({ message, type: 'error', duration: 6000, showClose: true })
}

/** 需要用户看清的警告（例如唯一性校验未通过）同样延长停留时间 */
export function showWarning(message: string): void {
  ElMessage({ message, type: 'warning', duration: 5000, showClose: true })
}
