import axios from "axios";

export const api = axios.create({
    baseURL: "http://localhost:8080",
    timeout: 10000,
    withCredentials: true, // 쿠키/세션 쓰면 켜고, 아니면 false로 두는 게 깔끔
});