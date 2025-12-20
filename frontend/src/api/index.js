import axios from "axios";

// baseURL 안전하게(끝 슬래시 제거)
const baseURL = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");


console.log("[api] VITE_API_URL =", import.meta.env.VITE_API_URL);
console.log("[api] baseURL =", baseURL);

export const api = axios.create({
    baseURL,
    timeout: 10000,
    headers: { "Content-Type": "application/json" },
});

export default api;
