import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";        // ✅ 라우터 가져오기
import { initAuth } from "./api/index";

const app = createApp(App);
app.use(router);                      // ✅ 라우터 등록
app.mount("#app");                    // ✅ 먼저 화면 띄우고

initAuth();                           // ✅ 그 다음에 토큰 갱신 시도 (막지 말기)
