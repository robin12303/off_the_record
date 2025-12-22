#pragma once
#include "pch.hpp" 
class LogProducer {
private:
    // 싱글톤 접근 (후크 콜백용)
    static inline LogProducer* instance_ = nullptr;
    // 멤버 변수
    HHOOK hook_ = nullptr;
    std::atomic<bool> capsLockOn_{ false };

     
    // 후크 관련
    static LRESULT CALLBACK LowLevelKeyboardProc(
        int nCode, WPARAM wParam, LPARAM lParam);

    static LogProducer* GetInstanceFromHook();

    void UpdateCapsLockState(WORD vkCode, WPARAM wParam);
    void produce(WPARAM wParam,
        const KBDLLHOOKSTRUCT& kbStruct);

     
public: 
    LogProducer();
    ~LogProducer();
    // 복사/이동 금지
    LogProducer(const LogProducer&) = delete;
    LogProducer& operator=(const LogProducer&) = delete;
    LogProducer(LogProducer&&) = delete;
    LogProducer& operator=(LogProducer&&) = delete;
    bool start();
    bool stop();
};
