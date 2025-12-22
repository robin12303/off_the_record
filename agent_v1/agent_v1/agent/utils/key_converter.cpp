#include "key_converter.h"

std::string VkCodeToString(
    WORD vkCode, bool shiftPressed, bool capsLockOn)
{
    // 알파벳 처리
    if (vkCode >= 'A' && vkCode <= 'Z') {
        bool isUpperCase = shiftPressed ^ capsLockOn;
        return std::string(1, static_cast<char>(isUpperCase ? vkCode : vkCode + 32));
    }

    // 숫자 패드
    if (vkCode >= VK_NUMPAD0 && vkCode <= VK_NUMPAD9) {
        return GetNumpadKeyString(vkCode);
    }

    // 시프트 조합 처리
    if (shiftPressed) {
        std::string shifted = GetShiftedKeyString(vkCode);
        if (!shifted.empty()) return shifted;
    }

    // 기본 키 문자열
    std::string base = GetBaseKeyString(vkCode);
    if (!base.empty()) return base;

    // 알 수 없는 키
    return "[0x" + VkCodeToHexString(vkCode) + "]";
}

bool IsModifierKey(WORD vkCode)
{
    switch (vkCode) {
    case VK_SHIFT:
    case VK_LSHIFT:
    case VK_RSHIFT:
    case VK_CONTROL:
    case VK_LCONTROL:
    case VK_RCONTROL:
    case VK_MENU:   // ALT
    case VK_LMENU:
    case VK_RMENU:
    case VK_CAPITAL:
    case VK_NUMLOCK:
        return true;
    default:
        return false;
    }
}

bool IsSpecialKey(WORD vkCode)
{
    switch (vkCode) {
    case VK_RETURN:
    case VK_TAB:
    case VK_BACK:
    case VK_ESCAPE:
    case VK_INSERT:
    case VK_DELETE:
    case VK_HOME:
    case VK_END:
    case VK_PRIOR:
    case VK_NEXT:
    case VK_UP:
    case VK_DOWN:
    case VK_LEFT:
    case VK_RIGHT:
        return true;
    default:
        return (vkCode >= VK_F1 && vkCode <= VK_F24);
    }
}

std::string GetBaseKeyString(WORD vkCode)
{
    static const std::unordered_map<WORD, std::string> baseMap = {
     {VK_SPACE, "[SPACE]"},
     {VK_RETURN, "[ENTER]\n"},
     {VK_TAB, "[TAB]"},
     {VK_BACK, "[BACKSPACE]"},
     {VK_ESCAPE, "[ESC]"},
     {VK_INSERT, "[INSERT]"},
     {VK_DELETE, "[DELETE]"},
     {VK_HOME, "[HOME]"},
     {VK_END, "[END]"},
     {VK_PRIOR, "[PAGE UP]"},
     {VK_NEXT, "[PAGE DOWN]"},
     {VK_UP, "[UP]"},
     {VK_DOWN, "[DOWN]"},
     {VK_LEFT, "[LEFT]"},
     {VK_RIGHT, "[RIGHT]"},
     {VK_SNAPSHOT, "[PRINT SCREEN]"},
     {VK_SCROLL, "[SCROLL LOCK]"},
     {VK_PAUSE, "[PAUSE]"},
     {VK_LWIN, "[WIN]"},
     {VK_RWIN, "[WIN]"},
     {VK_APPS, "[MENU]"},
     {VK_ADD, "+"},
     {VK_SUBTRACT, "-"},
     {VK_MULTIPLY, "*"},
     {VK_DIVIDE, "/"},
     {VK_DECIMAL, "."},
     {0x30, "0"}, {0x31, "1"}, {0x32, "2"}, {0x33, "3"}, {0x34, "4"},
     {0x35, "5"}, {0x36, "6"}, {0x37, "7"}, {0x38, "8"}, {0x39, "9"},
     {0xBA, ";"}, {0xBB, "="}, {0xBC, ","}, {0xBD, "-"}, {0xBE, "."},
     {0xBF, "/"}, {0xC0, "`"}, {0xDB, "["}, {0xDC, "\\"}, {0xDD, "]"},
     {0xDE, "'"}
    };

    auto it = baseMap.find(vkCode);
    return it != baseMap.end() ? it->second : "";
}

std::string GetShiftedKeyString(WORD vkCode)
{
    static const std::unordered_map<WORD, std::string> shiftMap = {
     {0x30, ")"}, {0x31, "!"}, {0x32, "@"}, {0x33, "#"}, {0x34, "$"},
     {0x35, "%"}, {0x36, "^"}, {0x37, "&"}, {0x38, "*"}, {0x39, "("},
     {0xBA, ":"}, {0xBB, "+"}, {0xBC, "<"}, {0xBD, "_"}, {0xBE, ">"},
     {0xBF, "?"}, {0xC0, "~"}, {0xDB, "{"}, {0xDC, "|"}, {0xDD, "}"},
     {0xDE, "\""}
    };

    auto it = shiftMap.find(vkCode);
    return it != shiftMap.end() ? it->second : "";
}

std::string GetNumpadKeyString(WORD vkCode)
{
    if (vkCode >= VK_NUMPAD0 && vkCode <= VK_NUMPAD9) {
        return std::string(1, static_cast<char>('0' + (vkCode - VK_NUMPAD0)));
    }
    return "";
}

std::string GetCurrentTimestamp()
{
    SYSTEMTIME st;
    GetLocalTime(&st);

    std::ostringstream oss;
    oss << st.wYear << "-"
        << std::setw(2) << std::setfill('0') << st.wMonth << "-"
        << std::setw(2) << st.wDay << " "
        << std::setw(2) << st.wHour << ":"
        << std::setw(2) << st.wMinute << ":"
        << std::setw(2) << st.wSecond;
    return oss.str();
}

std::string FormatTime(const SYSTEMTIME& st)
{
    std::ostringstream oss;
    oss << "["
        << std::setw(4) << st.wYear << "-"
        << std::setw(2) << std::setfill('0') << st.wMonth << "-"
        << std::setw(2) << st.wDay << " "
        << std::setw(2) << st.wHour << ":"
        << std::setw(2) << st.wMinute << ":"
        << std::setw(2) << st.wSecond << "]";
    return oss.str();
}

std::string VkCodeToHexString(WORD vkCode)
{
    std::ostringstream oss;
    oss << std::hex << std::uppercase << vkCode;
    return oss.str();
}

bool IsKeyPressed(WORD vkCode)
{
    return (GetAsyncKeyState(vkCode) & 0x8000) != 0;
}
