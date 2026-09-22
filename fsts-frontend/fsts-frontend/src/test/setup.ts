import { config } from '@vue/test-utils'

config.global.stubs = {
  transition: false,
  'el-icon': true,
}
