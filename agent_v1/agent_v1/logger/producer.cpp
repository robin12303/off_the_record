#include "producer.h" 
LRESULT Producer::LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam)
{
    if (nCode < 0)
        return CallNextHookEx(
            nullptr, nCode, wParam, lParam);
    Producer* instance = GetInstanceFromHook(); 

    if (instance && metric_running) {
        KBDLLHOOKSTRUCT* kbStruct =
            reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);
        instance->produceMetric(wParam, *kbStruct);
    } 
    return CallNextHookEx(
        nullptr, nCode, wParam, lParam);
}

Producer* Producer::GetInstanceFromHook()
{
    return instance_;
}
void Producer::produceMetric(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruc)
{
    std::cout << "produceMetric : " << g_keystrokes << "\n";
    g_keystrokes++;
}

Producer::Producer()
{
    instance_ = this; 
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
    return true;
}

