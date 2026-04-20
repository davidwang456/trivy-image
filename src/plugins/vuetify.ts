// Composables
import { createVuetify } from "vuetify"

/**
 * plugins/vuetify.ts
 *
 * Framework documentation: https://vuetifyjs.com`
 */

// Styles
import "@mdi/font/css/materialdesignicons.css"
import "vuetify/styles"
import { VFileUpload } from "vuetify/labs/VFileUpload"

// https://vuetifyjs.com/en/introduction/why-vuetify/#feature-guides
export default createVuetify({
  theme: {
    defaultTheme: "light",
  },
  components: {
    VFileUpload,
  },
})
