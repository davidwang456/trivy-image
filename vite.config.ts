import Vue from "@vitejs/plugin-vue"
import { fileURLToPath, URL } from "node:url"
// Plugins
import AutoImport from "unplugin-auto-import/vite"
import Fonts from "unplugin-fonts/vite"
import Components from "unplugin-vue-components/vite"
// Utilities
import { defineConfig } from "vite"
import Vuetify, { transformAssetUrls } from "vite-plugin-vuetify"

// https://vitejs.dev/config/
export default defineConfig({
  // Must match server.servlet.context-path in backend application.yml for unified deploy.
  base: "/",
  plugins: [
    AutoImport({
      imports: [
        "vue",
        {
          "vue-router/auto": ["useRoute", "useRouter"],
        },
      ],
      dts: "src/auto-imports.d.ts",
      eslintrc: {
        enabled: true,
      },
      vueTemplate: true,
    }),
    Components({
      dts: "src/components.d.ts",
    }),
    Vue({
      template: { transformAssetUrls },
    }),
    // https://github.com/vuetifyjs/vuetify-loader/tree/master/packages/vite-plugin#readme
    Vuetify({
      autoImport: true,
      styles: {
        configFile: "src/styles/settings.scss",
      },
    }),
    Fonts({
      google: {
        families: [
          {
            name: "Roboto",
            styles: "wght@100;300;400;500;700;900",
          },
        ],
      },
    }),
  ],
  define: { "process.env": {} },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
    extensions: [".js", ".json", ".jsx", ".mjs", ".ts", ".tsx", ".vue"],
  },
  build: {
    outDir: "backend/src/main/resources/public",
    emptyOutDir: true,
  },
  server: {
    port: 8080,
    proxy: {
      "/api": {
        target: "http://localhost:9080",
        changeOrigin: true,
      },
    },
  },
})
