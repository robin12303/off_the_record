import axios from "axios";
import { tokenStore } from "@/tokenStore";

// baseURL 안전하게(끝 슬래시 제거)
const baseURL = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");

// refresh 호출은 인터셉터가 붙으면 무한루프 날 수 있어서 별도 인스턴스 추천
const refreshApi = axios.create({
    baseURL,
    timeout: 10000,
    headers: { "Content-Type": "application/json" },
    withCredentials: true, // refresh 쿠키 보내려면 필수
});

export const api = axios.create({
    baseURL,
    timeout: 10000,
    headers: { "Content-Type": "application/json" },
    withCredentials: true, // refresh 쿠키 쓰면 켜두는 게 편함
});

// ✅ 요청 인터셉터: 메모리 토큰만 사용
api.interceptors.request.use((config) => {
    const token = tokenStore.get();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

// ✅ 401 처리: refresh로 새 토큰 받고 메모리에만 저장
let isRefreshing = false;
let pending = [];

function flushPending(error, newToken) {
    pending.forEach(({ resolve, reject }) => (error ? reject(error) : resolve(newToken)));
    pending = [];
}


api.interceptors.response.use(
    (res) => res,
    async (error) => {
        const original = error.config;
        if (!error.response) throw error;

        if (error.response.status !== 401 || original._retry) throw error;
        original._retry = true;

        if (isRefreshing) {
            const newToken = await new Promise((resolve, reject) => pending.push({ resolve, reject }));
            original.headers.Authorization = `Bearer ${newToken}`;
            return api(original);
        }

        isRefreshing = true;

        try {
            const r = await refreshApi.post("/auth/refresh", null);
            const newToken = r?.data?.accessToken;
            if (!newToken) throw new Error("No accessToken returned from /auth/refresh");

            tokenStore.set(newToken);
            api.defaults.headers.common.Authorization = `Bearer ${newToken}`;
            flushPending(null, newToken);

            original.headers.Authorization = `Bearer ${newToken}`;
            return api(original);
        } catch (e) {
            flushPending(e, null);
            tokenStore.clear();
            delete api.defaults.headers.common.Authorization;
            throw e;
        } finally {
            isRefreshing = false;
        }
    }
);
export default api;

// 앱 시작 시 한 번 호출해서 “새로고침해도” 로그인 유지 느낌 만들기
export async function initAuth() {
    try {
        const r = await refreshApi.post("/auth/refresh", null);
        const t = r?.data?.accessToken;
        if (t) {
            tokenStore.set(t);
            api.defaults.headers.common.Authorization = `Bearer ${t}`;
        }
    } catch {
        tokenStore.clear();
        delete api.defaults.headers.common.Authorization;
    }
}