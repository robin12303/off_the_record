import { createRouter, createWebHistory } from "vue-router";


const HomeView = () => import("@/views/HomeView.vue");
const AboutView = () => import("@/views/AboutView.vue");
const DashBoard = () => import("@/views/DashBoard.vue");
const LogView = () => import("@/views/LogView.vue");
const routes = [
    { path: "/", name: "home", component: HomeView },
    { path: "/about", name: "about", component: AboutView },
    { path: "/dashboard", name: "dashboard", component: DashBoard },
    { path: "/log", name: "log", component: LogView },

];

export default createRouter({
    history: createWebHistory(),
    routes,
});
