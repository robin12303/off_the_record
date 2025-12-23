#pragma once
#include "pch.hpp" 
class Producer {
private:
    // 싱글톤 접근 (후크 콜백용)
    static inline Producer* instance_ = nullptr;
    // 멤버 변수
    HHOOK hook_ = nullptr;
    std::atomic<bool> capsLockOn_{ false };

     
    // 후크 관련
    static LRESULT CALLBACK LowLevelKeyboardProc(
        int nCode, WPARAM wParam, LPARAM lParam);

    static Producer* GetInstanceFromHook();

    void UpdateCapsLockState(WORD vkCode, WPARAM wParam);
    void produceLog(WPARAM wParam,
        const KBDLLHOOKSTRUCT& kbStruct);
    void produceMetric(WPARAM wParam,
        const KBDLLHOOKSTRUCT& kbStruc);
     
public: 
    Producer();
    ~Producer();
    // 복사/이동 금지
    Producer(const Producer&) = delete;
    Producer& operator=(const Producer&) = delete;
    Producer(Producer&&) = delete;
    Producer& operator=(Producer&&) = delete;
    bool start();
    bool stop();
};
