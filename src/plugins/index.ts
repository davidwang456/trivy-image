import router from "../router"

/**
 * plugins/index.ts
 *
 * Automatically included in `./src/main.ts`
 */

// Plugins
import vuetify from "./vuetify"
import Clipboard from "v-clipboard"

// Types
import type { App } from "vue"

export function registerPlugins(app: App) {
  app.use(vuetify).use(router)
  app.use(Clipboard).use(router)
}
