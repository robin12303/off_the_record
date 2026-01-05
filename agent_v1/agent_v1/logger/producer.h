#pragma once
#include "pch.hpp"

// Producer:
// - Windows Low-level keyboard hook(WH_KEYBOARD_LL)을 설치해서
// - 키보드 이벤트를 받아 메트릭(예: g_keystrokes 증가)을 생산하는 역할
//
// 설계 포인트:
// - 훅 콜백은 static 함수로만 받을 수 있으므로 instance_로 this를 우회 접근
// - 훅 콜백 안에서는 "가벼운 작업"만 해야 함(긴 락/네트워크/I/O 금지)

class Producer {
private:
    // 싱글톤 접근 (후크 콜백용)
    // - LowLevelKeyboardProc은 static 콜백이라 this가 없어서
    //   instance_를 통해 현재 Producer 인스턴스에 접근한다.
    //
    // ⚠️ 주의:
    // - Producer를 2개 이상 만들면 마지막 생성된 객체가 instance_를 덮어씀
    // - 멀티 인스턴스가 필요하면 구조를 바꿔야 함
    static inline Producer* instance_ = nullptr;

    // 설치된 훅 핸들
    // - start()에서 SetWindowsHookEx로 받고
    // - stop()에서 UnhookWindowsHookEx로 해제하는 게 정석
    HHOOK hook_ = nullptr;

    // --------------------
    // Hook callback
    // --------------------

    // Windows가 키보드 이벤트 발생 시 호출하는 훅 콜백
    // - nCode < 0이면 즉시 CallNextHookEx로 넘겨야 함(훅 체인 규칙)
    // - wParam: 이벤트 타입(키다운/업 등)
    // - lParam: KBDLLHOOKSTRUCT 포인터(키 정보)
    //
    // ⚠️ 매우 중요:
    // - 이 함수는 OS가 호출하는 경로에 붙어있기 때문에
    //   오래 걸리면 시스템 전체 입력 지연이 생길 수 있음.
    // - 여기서는 카운터 증가 같은 "최소 작업"만 수행하는 게 안전.
    static LRESULT CALLBACK LowLevelKeyboardProc(
        int nCode, WPARAM wParam, LPARAM lParam);

    // instance_ 반환(훅 콜백에서 사용)
    static Producer* GetInstanceFromHook();

    // 실제 메트릭 생산 로직
    // - 예: g_keystrokes 증가, 이벤트를 큐에 넣기(가능하면 가볍게)
    void produceMetric(
        WPARAM wParam,
        const KBDLLHOOKSTRUCT& kbStruc);

public:
    Producer();
    ~Producer();

    // 복사/이동 금지:
    // - hook_ 같은 OS 핸들은 복사/이동 시 수명이 꼬이기 쉬움
    // - instance_ 싱글톤도 복사되면 의미가 깨짐
    Producer(const Producer&) = delete;
    Producer& operator=(const Producer&) = delete;
    Producer(Producer&&) = delete;
    Producer& operator=(Producer&&) = delete;

    // 훅 설치 시작
    // - 성공 시 true, 실패 시 false
    // - 일반적으로 start() 후에는 메시지 루프(GetMessage)가 살아 있어야 안정적으로 이벤트가 들어옴
    bool start();

    // 훅 해제/정지
    // - 정석은 UnhookWindowsHookEx(hook_) 호출
    // - 현재 구현이 단순 return true라면 실제로는 멈추지 않을 수 있음
    bool stop();
};
