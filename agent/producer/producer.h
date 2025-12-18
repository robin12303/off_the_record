#pragma once
#include "sync_globals.h" 

namespace json = boost::json;
class Producer {
private:
    // 싱글톤 접근 (후크 콜백용)
    static Producer* instance_;

    // 멤버 변수
    HHOOK hook_ = nullptr;
    std::atomic<bool> running_{ false };
    std::atomic<bool> capsLockOn_{ false };

    // 후크 관련
    static LRESULT CALLBACK LowLevelKeyboardProc(
        int nCode, WPARAM wParam, LPARAM lParam);
    static Producer* GetInstanceFromHook();

    void UpdateCapsLockState(WORD vkCode, WPARAM wParam);
    void produce(WPARAM wParam,
        const KBDLLHOOKSTRUCT& kbStruct);

public:
    Producer();
    ~Producer();

    // 복사/이동 방지
    Producer(const Producer&) = delete;
    Producer& operator=(const Producer&) = delete;
    Producer(Producer&&) = delete;
    Producer& operator=(Producer&&) = delete;
    bool start();
    bool Stop();
};