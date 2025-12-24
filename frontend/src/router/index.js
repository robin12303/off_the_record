import { createRouter, createWebHistory } from "vue-router";


const HomeView = () => import("@/views/HomeView.vue");
const AboutView = () => import("@/views/AboutView.vue");
const DashBoard = () => import("@/views/DashBoard.vue");
const LogView = () => import("@/views/LogView.vue");
const Metrics = () => import("@/views/MetricsView.vue");
const login = () => import("@/auth/LoginView.vue");
const signup = () => import("@/auth/SignupView.vue");
const routes = [
    { path: "/home", name: "home", component: HomeView },
    { path: "/about", name: "about", component: AboutView },
    { path: "/dashboard", name: "dashboard", component: DashBoard },
    { path: "/log", name: "log", component: LogView },
    { path: "/metrics", name: "metrics", component: Metrics},
    { path: "/", name: "login", component: login,
        meta: { hideHeader: true, hideFooter: true } // ✅ 헤더/푸터 숨김
    },
    { path: "/signup", name: "signup", component: signup,
        meta: { hideHeader: true, hideFooter: true } // ✅ 헤더/푸터 숨김
    },
];

export default createRouter({
    history: createWebHistory(),
    routes,
});
