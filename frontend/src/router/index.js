import { createRouter, createWebHistory } from "vue-router";


const HomeView = () => import("@/views/HomeView.vue");
const AboutView = () => import("@/views/AboutView.vue");
const DashBoard = () => import("@/views/DashBoard.vue");
const LogView = () => import("@/views/LogView.vue");
const Login = () => import("@/auth/Login.vue");

const routes = [
    {
        path: "/",
        name: "login",
        component: Login,
        meta: { hideHeader: true, hideFooter: true }, // ✅ 추가
    },
    { path: "/home", name: "home", component: HomeView },
    { path: "/about", name: "about", component: AboutView },
    { path: "/dashboard", name: "dashboard", component: DashBoard },
    { path: "/log", name: "log", component: LogView },
];

export default createRouter({
    history: createWebHistory(),
    routes,
});
