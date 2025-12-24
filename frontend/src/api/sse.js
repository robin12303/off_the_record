// src/api/sse.js
import { fetchEventSource } from "@microsoft/fetch-event-source";
import api from "@/api/index.js";

export function startSseRead(id, { onKeyEvent, onConnected, onError } = {}) {
    const token = localStorage.getItem("access_token");
    const url = `${api.defaults.baseURL}/api/sse/stream/read/${encodeURIComponent(id)}`;

    const ctrl = new AbortController();

    fetchEventSource(url, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "text/event-stream",
        },
        signal: ctrl.signal,

        onopen(res) {
            if (!res.ok) throw new Error(`SSE open failed: ${res.status}`);
            // 연결 자체는 열림
        },

        onmessage(msg) {
            // 서버가 "event: key_event" 이런 식으로 보내면 msg.event에 들어옴
            if (msg.event === "connected") onConnected?.(msg.data);
            else if (msg.event === "key_event") onKeyEvent?.(msg.data);
            else {
                // event 지정 안 하면 기본 메시지로 옴
                // 필요하면 여기서 처리
            }
        },

        onerror(err) {
            onError?.(err);
            // throw err; // <- throw 하면 재연결 중단, 안 하면 자동 재시도
        },
    });

    return () => ctrl.abort(); // ✅ stop 함수
}

export function startSseMetrics(id, { onMetricEvent, onConnected, onError } = {}) {
    const token = localStorage.getItem("access_token");
    const url = `${api.defaults.baseURL}/api/sse/stream/metrics/${encodeURIComponent(id)}`;

    const ctrl = new AbortController();

    fetchEventSource(url, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: "text/event-stream",
        },
        signal: ctrl.signal,

        onopen(res) {
            if (!res.ok) throw new Error(`SSE open failed: ${res.status}`);
        },

        onmessage(msg) {
            if (msg.event === "connected") onConnected?.(msg.data);
            else if (msg.event === "metric_event") onMetricEvent?.(msg.data);
        },

        onerror(err) {
            onError?.(err);
            // throw err; // throw하면 재연결 중단
        },
    });

    return () => ctrl.abort(); // ✅ stop 함수
}