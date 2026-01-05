#include "producer.h"

// Low-level keyboard hook 콜백:
// Windows가 키보드 이벤트 발생 시 이 함수를 호출한다.
// ⚠️ 주의: 이 콜백은 OS가 호출하는 컨텍스트에서 실행되므로
// 오래 걸리는 작업(네트워크, 파일 I/O, 긴 락)은 여기서 하면 시스템 전체가 느려질 수 있다.
LRESULT Producer::LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam)
{
    // nCode < 0 이면 훅 체인을 방해하면 안 되므로 바로 다음 훅으로 넘김
    if (nCode < 0)
        return CallNextHookEx(nullptr, nCode, wParam, lParam);

    // 훅 콜백은 static 함수라 this가 없으므로,
    // 전역/정적 포인터(instance_)를 통해 객체 인스턴스를 찾는다.
    Producer* instance = GetInstanceFromHook();

    // metric_running이 true일 때만 처리(수집 on/off 플래그)
    // metric_running이 다른 스레드에서도 읽/쓰면 atomic으로 두는 게 안전함.
    if (instance && metric_running) {

        // lParam에는 KBDLLHOOKSTRUCT가 들어있음(키 이벤트 세부정보)
        KBDLLHOOKSTRUCT* kbStruct =
            reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);

        // 실제 "메트릭 생산" 로직 호출
        // ⚠️ 여기서는 가능한 한 가볍게 처리해야 함
        instance->produceMetric(wParam, *kbStruct);
    }

    // 이벤트를 계속 전달(다른 훅/대상 앱이 정상 동작하도록)
    return CallNextHookEx(nullptr, nCode, wParam, lParam);
}

// instance_를 통해 현재 객체 인스턴스를 반환
// ⚠️ 단, instance_ 방식은 "Producer 객체를 딱 하나만" 쓸 때만 안전하다.
Producer* Producer::GetInstanceFromHook()
{
    return instance_;
}

// 키 이벤트를 "메트릭"으로 집계하는 함수
// 여기서는 단순히 keystrokes 카운트를 증가시킴
void Producer::produceMetric(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruc)
{
    // 디버그 출력: 이벤트마다 출력하면 성능에 영향이 큼(키 입력 많으면 콘솔 병목)
    std::cout << "produceMetric : " << g_keystrokes << "\n";

    // 키 입력 카운트 증가
    // ⚠️ g_keystrokes가 여러 스레드에서 접근된다면 atomic 또는 락이 필요함.
    g_keystrokes++;
}

Producer::Producer()
{
    // static instance_에 this를 등록해서 훅 콜백에서 접근 가능하게 함
    // ⚠️ 여러 Producer 객체를 만들면 마지막 생성된 객체가 덮어쓴다.
    instance_ = this;
}

Producer::~Producer()
{
    // 현재 instance_가 자기 자신이면 정리
    // (다른 객체가 이미 덮어쓴 경우에는 건드리지 않음)
    if (instance_ == this) {
        instance_ = nullptr;
    }
}

bool Producer::start()
{
    // Low-level keyboard hook 설치
    // WH_KEYBOARD_LL: 시스템 전역 키보드 이벤트를 받는 훅
    // LowLevelKeyboardProc: 콜백 함수 포인터
    // GetModuleHandle(nullptr): 현재 모듈 핸들(실행 파일 모듈)
    // threadId = 0: 모든 스레드(전역 훅)
    hook_ = SetWindowsHookEx(
        WH_KEYBOARD_LL,
        LowLevelKeyboardProc,
        GetModuleHandle(nullptr),
        0);

    // 설치 실패 시 false
    if (hook_ == nullptr) {
        return false;
    }

    return true;
}

bool Producer::stop()
{
    // TODO: 훅 해제 필요
    // 일반적으로 UnhookWindowsHookEx(hook_) 호출하고 hook_ = nullptr 로 정리함.
    // 현재는 true만 반환하므로 실제로는 훅이 계속 살아있을 수 있음.
    return true;
}
