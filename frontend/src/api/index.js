import axios from "axios";

// baseURL 안전하게(끝 슬래시 제거)
const baseURL = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");

export const api = axios.create({
    baseURL,
    timeout: 10000,
    headers: { "Content-Type": "application/json" },
    withCredentials: true, // ✅ refresh_token 쿠키 보내고/받기
});

// access 토큰 저장/조회 (일단 localStorage로 단순 구현)
const getAccessToken = () => localStorage.getItem("access_token");
const setAccessToken = (t) => localStorage.setItem("access_token", t);
const clearAccessToken = () => localStorage.removeItem("access_token");

// ✅ 요청마다 Authorization 붙이기
api.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

let isRefreshing = false;
let waiters = [];

// ✅ 401이면 refresh로 access 재발급 후 원요청 재시도
api.interceptors.response.use(
    (res) => res,
    async (err) => {
        const status = err.response?.status;
        const original = err.config;

        // 네 auth 엔드포인트에서 401이 난 걸 또 refresh로 돌리면 무한루프 나니까 제외
        const isAuthCall = original?.url?.includes("/api/auth/");
        if (status !== 401 || !original || original._retry || isAuthCall) {
            throw err;
        }

        original._retry = true;

        if (isRefreshing) {
            const token = await new Promise((resolve, reject) => {
                waiters.push({ resolve, reject });
            });
            original.headers.Authorization = `Bearer ${token}`;
            return api(original);
        }

        isRefreshing = true;
        try {
            const { data } = await api.post("/api/auth/refresh"); // ✅ 쿠키로 refresh
            setAccessToken(data.accessToken);

            waiters.forEach((w) => w.resolve(data.accessToken));
            waiters = [];

            original.headers.Authorization = `Bearer ${data.accessToken}`;
            return api(original);
        } catch (e) {
            waiters.forEach((w) => w.reject(e));
            waiters = [];
            clearAccessToken();
            throw e;
        } finally {
            isRefreshing = false;
        }
    }
);

export default api;
