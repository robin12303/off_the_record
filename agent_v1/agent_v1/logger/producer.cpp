#include "producer.h" 
LRESULT Producer::LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam)
{
    if (nCode < 0)
        return CallNextHookEx(
            nullptr, nCode, wParam, lParam);
    Producer* instance = GetInstanceFromHook();

    if (instance && logger_running) {
        KBDLLHOOKSTRUCT* kbStruct =
            reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);
        instance->produceLog(wParam, *kbStruct);
    }
    if (instance && metric_running) {
        KBDLLHOOKSTRUCT* kbStruct =
            reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);
        instance->produceMetric(wParam, *kbStruct);
    }
    else { 
        g_keystrokes = -1;
    }
    return CallNextHookEx(
        nullptr, nCode, wParam, lParam);
}

Producer* Producer::GetInstanceFromHook()
{
    return instance_;
}

void Producer::UpdateCapsLockState(WORD vkCode, WPARAM wParam)
{
    if (vkCode == VK_CAPITAL && wParam == WM_KEYDOWN) {
        capsLockOn_ = !capsLockOn_;
    }
}

void Producer::produceLog(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruct)
{ 
    // CapsLock 상태 업데이트
    UpdateCapsLockState(kbStruct.vkCode, wParam);

    // 이벤트 타입 결정
    std::string eventType;
    if (wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN) {
        eventType = "[KEY DOWN]";
    }
    else if (wParam == WM_KEYUP || wParam == WM_SYSKEYUP) {
        eventType = "[KEY UP]";
    }
    else {
        eventType = "[OTHER]";
    }
    // 타임스탬프
    SYSTEMTIME st;
    GetLocalTime(&st);
    std::string timestamp = FormatTime(st);

    bool shiftPressed = IsKeyPressed(VK_SHIFT);
    std::string keyString = VkCodeToString(
        kbStruct.vkCode, shiftPressed, capsLockOn_);

    auto data = KeyEvent(timestamp,capsLockOn_ ? "ON" : "OFF", eventType, keyString);

    {
        std::lock_guard<std::mutex> lk(log_m);
        log_q.push(data);
    } 
    log_sem.release(); 
}

void Producer::produceMetric(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruc)
{
    if (g_keystrokes < 0) g_keystrokes++;
    std::cout << "produceMetric : " << g_keystrokes << "\n";
    g_keystrokes.fetch_add(1, std::memory_order_relaxed); 
}

Producer::Producer()
{
    instance_ = this;
    capsLockOn_ = (GetKeyState(VK_CAPITAL) & 0x0001) != 0;
}

Producer::~Producer()
{
    if (instance_ == this) {
        instance_ = nullptr;
    }
}

bool Producer::start()
{ 

    // 후크 설치
    hook_ = SetWindowsHookEx(
        WH_KEYBOARD_LL, LowLevelKeyboardProc,
        GetModuleHandle(nullptr), 0);
    if (hook_ == nullptr) {
        return false;
    } 
    return true;
}

bool Producer::stop()
{
    logger_running = false;
    return true;
}

