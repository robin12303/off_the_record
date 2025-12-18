#include "producer.h"


// 정적 멤버 초기화
Producer* Producer::instance_ = nullptr;
LRESULT Producer::LowLevelKeyboardProc(
    int nCode, WPARAM wParam, LPARAM lParam)
{
    if(nCode < 0)
        return CallNextHookEx(
			nullptr, nCode, wParam, lParam);
 	Producer* instance = GetInstanceFromHook();
 
    if (instance && instance->running_) {
        KBDLLHOOKSTRUCT* kbStruct =
            reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);
        instance->produce(wParam, *kbStruct);
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
void Producer::produce(WPARAM wParam, const KBDLLHOOKSTRUCT& kbStruct)
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

    bool shiftPressed = IsKeyPressed(VK_SHIFT);
    std::string keyString = VkCodeToString(
        kbStruct.vkCode, shiftPressed, capsLockOn_);

    
    // 타임스탬프
    SYSTEMTIME st;
    GetLocalTime(&st);
    std::string timestamp = FormatTime(st);

    // JSON 데이터 생성
    json::object j;
    j["timeStamp"] = timestamp;
    j["capsLock"] = capsLockOn_ ? "ON" : "OFF";
    j["eventType"] = eventType;
    j["keyString"] = keyString; 
     
    {
        std::lock_guard<std::mutex> lock(m);
        q.push(j);
        sem.release();
    } 
} 
Producer::Producer()  
{
	instance_ = this;
	capsLockOn_ = (GetKeyState(VK_CAPITAL) & 0x0001) != 0;
}

Producer::~Producer()
{
    Stop();
	if (instance_ == this) {
        instance_ = nullptr;
    }
}

bool Producer::start()
{
    if (running_) return false;

    // 후크 설치
    hook_ = SetWindowsHookEx(
        WH_KEYBOARD_LL, LowLevelKeyboardProc,
        GetModuleHandle(nullptr), 0);
    if (hook_ == nullptr) { 
        return false;
    }
     
	
    
	return running_ = true;
}

bool Producer::Stop()
{
    if (!running_) return true;

    running_ = false;

    if (hook_ != nullptr) {
        UnhookWindowsHookEx(hook_);
        hook_ = nullptr;
    }

    return true;
}
 