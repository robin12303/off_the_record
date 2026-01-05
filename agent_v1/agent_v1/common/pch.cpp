#include "pch.hpp"

// ============================================================================
// UUID / Install ID
// ============================================================================

// 새 UUID 문자열을 생성 (Windows RPC UUID API 사용)
// - UuidCreate: UUID 생성
// - UuidToStringA: 문자열 변환
// - RpcStringFreeA: 변환된 문자열 버퍼 해제
std::string NewUuid()
{
    UUID u{};
    RPC_STATUS st = UuidCreate(&u);

    // RPC_S_UUID_LOCAL_ONLY: 로컬에서만 유니크 보장 (그래도 보통 충분)
    if (st != RPC_S_OK && st != RPC_S_UUID_LOCAL_ONLY) return {};

    RPC_CSTR s = nullptr;
    if (UuidToStringA(&u, &s) != RPC_S_OK || !s) return {};

    std::string out(reinterpret_cast<const char*>(s));
    RpcStringFreeA(&s);
    return out;
}

// 설치/기기 식별용 ID를 파일로 저장해두고 재사용
// - 프로그램 재실행/재부팅해도 ID가 유지되게 만드는 용도
// - 예시는 실행 폴더에 ".install_id" 저장
//   ⚠️ 실제 배포라면 ProgramData/LocalAppData 같은 위치가 더 낫고 권한 이슈도 고려해야 함.
std::string LoadOrCreateInstallId()
{
    // ProgramData 같은 곳 추천. (예시는 실행 폴더에 .install_id)
    const std::string path = ".install_id";

    // load: 파일이 있으면 한 줄 읽어 그대로 사용
    if (std::filesystem::exists(path)) {
        std::ifstream in(path);
        std::string id;
        std::getline(in, id);
        if (!id.empty()) return id;
    }

    // create: 없으면 새 UUID 생성해서 저장
    std::string id = NewUuid();
    std::ofstream out(path, std::ios::trunc);
    out << id;
    return id;
}

// ============================================================================
// UTF16 <-> UTF8 helpers
// ============================================================================

// UTF-16(wstring) -> UTF-8(string)
// - WideCharToMultiByte 두 번 호출(필요 길이 계산 -> 변환)
static std::string WideToUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int needed = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
        nullptr, 0, nullptr, nullptr);
    if (needed <= 0) return {};
    std::string out(needed, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
        out.data(), needed, nullptr, nullptr);
    return out;
}

// 문자열 앞뒤 공백/탭/개행 제거
static std::wstring Trim(const std::wstring& s) {
    auto is_ws = [](wchar_t c) { return c == L' ' || c == L'\t' || c == L'\r' || c == L'\n'; };
    size_t b = 0, e = s.size();
    while (b < e && is_ws(s[b])) b++;
    while (e > b && is_ws(s[e - 1])) e--;
    return s.substr(b, e - b);
}

// vector<wstring>를 sep로 이어붙임
static std::wstring Join(const std::vector<std::wstring>& v, const wchar_t* sep) {
    std::wostringstream oss;
    for (size_t i = 0; i < v.size(); ++i) {
        if (i) oss << sep;
        oss << v[i];
    }
    return oss.str();
}

// ============================================================================
// Registry helpers
// ============================================================================

// REG_SZ 읽기 (문자열)
// - RegGetValueW로 크기 확인 후 버퍼 할당 -> 다시 읽기
static std::wstring RegGetSz(HKEY root, const wchar_t* subKey, const wchar_t* valueName) {
    DWORD type = 0;
    DWORD sizeBytes = 0;

    LONG rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_SZ,
        &type, nullptr, &sizeBytes);
    if (rc != ERROR_SUCCESS || sizeBytes < sizeof(wchar_t)) return L"";

    std::vector<wchar_t> buf(sizeBytes / sizeof(wchar_t), L'\0');
    rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_SZ,
        &type, buf.data(), &sizeBytes);
    if (rc != ERROR_SUCCESS) return L"";

    // RegGetValueW는 null-terminated로 줌
    return Trim(std::wstring(buf.data()));
}

// REG_DWORD 읽기
static bool RegGetDword(HKEY root, const wchar_t* subKey, const wchar_t* valueName, DWORD& out) {
    DWORD type = 0;
    DWORD size = sizeof(DWORD);
    DWORD val = 0;
    LONG rc = RegGetValueW(root, subKey, valueName, RRF_RT_REG_DWORD,
        &type, &val, &size);
    if (rc != ERROR_SUCCESS) return false;
    out = val;
    return true;
}

// ============================================================================
// WMI wrapper
// ============================================================================

// HRESULT를 16진 문자열로 변환 (예외 메시지용)
std::string Wmi::HrHex(HRESULT hr)
{
    std::ostringstream oss;
    oss << "0x" << std::hex << std::uppercase << (unsigned long)hr;
    return oss.str();
}

// COM 보안 초기화는 프로세스 단위로 보통 1회만 하면 됨
// - CoInitializeSecurity는 중복 호출하면 RPC_E_TOO_LATE가 날 수 있음(무시 가능)
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

// Wmi 객체 생성 시 WMI 서비스 연결 및 프록시 설정
// ⚠️ 주의: COM 초기화(CoInitializeEx)는 호출 스레드에서 되어 있어야 함.
//          이 파일에는 CoInitializeEx가 안 보이니 다른 곳에서 하고 있어야 안전함.
Wmi::Wmi()
{
    InitSecurityOnce();

    Microsoft::WRL::ComPtr<IWbemLocator> locator;
    HRESULT hr = CoCreateInstance(CLSID_WbemLocator, nullptr,
        CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&locator));
    if (FAILED(hr)) throw std::runtime_error("CoCreateInstance(IWbemLocator) failed: " + HrHex(hr));

    // ROOT\CIMV2: 일반적인 시스템 정보 네임스페이스
    hr = locator->ConnectServer(
        _bstr_t(L"ROOT\\CIMV2"),
        nullptr, nullptr, nullptr,
        0, nullptr, nullptr,
        services_.GetAddressOf()
    );
    if (FAILED(hr)) throw std::runtime_error("WMI ConnectServer failed: " + HrHex(hr));

    // 프록시 권한(보안) 설정: 호출/가장(impersonate) 수준
    hr = CoSetProxyBlanket(
        services_.Get(),
        RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, nullptr,
        RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE,
        nullptr, EOAC_DYNAMIC_CLOAKING
    );
    if (FAILED(hr)) throw std::runtime_error("CoSetProxyBlanket failed: " + HrHex(hr));
}

// 특정 WMI 클래스에서 문자열 속성(propName)들을 가져와 리스트로 반환
// 예: Win32_Processor.Name, Win32_VideoController.Name
std::vector<std::wstring> Wmi::QueryStringList(const std::wstring& className, const std::wstring& propName)
{
    if (!services_) return {};

    std::wstring wql = L"SELECT " + propName + L" FROM " + className;

    Microsoft::WRL::ComPtr<IEnumWbemClassObject> enumerator;
    HRESULT hr = services_->ExecQuery(
        _bstr_t(L"WQL"),
        _bstr_t(wql.c_str()),
        WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY, // 전방향/즉시 반환 플래그
        nullptr,
        enumerator.GetAddressOf()
    );
    if (FAILED(hr)) return {};

    std::vector<std::wstring> out;

    // 결과 순회
    while (true) {
        Microsoft::WRL::ComPtr<IWbemClassObject> obj;
        ULONG returned = 0;

        // WBEM_INFINITE: 다음 객체가 올 때까지 대기(블로킹)
        hr = enumerator->Next(WBEM_INFINITE, 1, obj.GetAddressOf(), &returned);
        if (FAILED(hr) || returned == 0) break;

        VARIANT vt; VariantInit(&vt);
        hr = obj->Get(propName.c_str(), 0, &vt, nullptr, nullptr);

        // 문자열(BSTR)만 처리
        if (SUCCEEDED(hr) && vt.vt == VT_BSTR && vt.bstrVal) {
            out.emplace_back(Trim(vt.bstrVal));
        }
        VariantClear(&vt);
    }
    return out;
}

// ============================================================================
// Hostname
// ============================================================================

// 컴퓨터 이름을 UTF-16으로 가져옴
// - fqdn=true면 FQDN(예: host.domain.local), false면 호스트명만
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

// 호스트 이름을 UTF-8로 반환
std::string GetHostNameUtf8(bool fqdn)
{
    return WideToUtf8(GetHostNameW(fqdn));
}

// ============================================================================
// Collectors (Spec 수집)
// ============================================================================

// RAM 용량을 "12.34 GB" 형태 문자열로
std::string FormatRamGbString(double gb)
{
    std::ostringstream oss;
    oss.setf(std::ios::fixed);
    oss << std::setprecision(2) << gb << " GB";
    return oss.str();
}

// 설치된 물리 RAM을 GB로 얻기
// - GetPhysicallyInstalledSystemMemory는 KB 단위 반환
bool TryGetInstalledRamGb(double& outGb)
{
    ULONGLONG kb = 0;
    if (!GetPhysicallyInstalledSystemMemory(&kb)) return false;
    outGb = static_cast<double>(kb) / (1024.0 * 1024.0); // KB -> GB
    return true;
}

// Windows 제품명/버전 정보를 레지스트리에서 구성
// - ProductName, DisplayVersion(또는 ReleaseId), CurrentVersion, Build, UBR
void GetWindowsOsNameVersion(std::string& outName, std::string& outVersion)
{
    const wchar_t* subKey = L"SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";

    std::wstring productName = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"ProductName");
    std::wstring displayVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"DisplayVersion");
    if (displayVersion.empty()) displayVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"ReleaseId");

    std::wstring currentVersion = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentVersion");     // "10.0" 등
    std::wstring buildNumber = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentBuildNumber"); // "22631" 등
    if (buildNumber.empty())    buildNumber = RegGetSz(HKEY_LOCAL_MACHINE, subKey, L"CurrentBuild");

    DWORD ubr = 0;
    bool hasUbr = RegGetDword(HKEY_LOCAL_MACHINE, subKey, L"UBR", ubr);

    // os_name: "Microsoft " prefix 제거(있으면)
    const std::wstring ms = L"Microsoft ";
    if (productName.rfind(ms, 0) == 0) productName = productName.substr(ms.size());
    productName = Trim(productName);

    // os_version: "23H2 (10.0.22631.2861)" 같은 형태 만들기
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

// Spec 구조체를 채우는 메인 함수
Spec getSpec()
{
    Spec spec;

    // -------------------- CPU/GPU (WMI) --------------------
    try {
        Wmi wmi;
        auto cpu = wmi.QueryStringList(L"Win32_Processor", L"Name");
        auto gpu = wmi.QueryStringList(L"Win32_VideoController", L"Name");

        // 빈값/중복 정리
        auto clean = [](std::vector<std::wstring>& v) {
            v.erase(std::remove_if(v.begin(), v.end(),
                [](const std::wstring& s) { return Trim(s).empty(); }), v.end());
            std::sort(v.begin(), v.end());
            v.erase(std::unique(v.begin(), v.end()), v.end());
            };
        clean(cpu);
        clean(gpu);

        // CPU는 없으면 "(unknown)", GPU는 없으면 빈 문자열 처리
        spec.cpu_name = WideToUtf8(cpu.empty() ? L"(unknown)" : Join(cpu, L", "));
        spec.gpu_name = WideToUtf8(gpu.empty() ? L"" : Join(gpu, L", "));
    }
    catch (...) {
        // WMI가 막히거나 권한/초기화 문제일 때 대비
        spec.cpu_name = "(unknown)";
        spec.gpu_name = "";
    }

    // -------------------- machine_guid (TODO/placeholder) --------------------
    try {
        // ⚠️ 현재 "\0"는 "널 문자 1개"짜리 문자열이 될 수 있음.
        // 즉, 빈 문자열("")과도 다르고 출력/직렬화에서 이상하게 보일 수 있음.
        // 의도한 게 "아직 미구현"이면 "" 또는 "(unknown)" 같은 게 보통 안전함.
        spec.machine_guid = "\0";
    }
    catch (const std::exception& e) {
        spec.machine_guid = "(unknown)";
    }

    // -------------------- RAM --------------------
    double ramGb = 0.0;
    if (TryGetInstalledRamGb(ramGb)) spec.ram = FormatRamGbString(ramGb);
    else spec.ram = "(unknown)";

    // -------------------- OS --------------------
    GetWindowsOsNameVersion(spec.os_name, spec.os_version);

    // -------------------- HOSTNAME --------------------
    spec.host_name = GetHostNameUtf8(false);

    return spec;
}

// ============================================================================
// Time formatting (로그/메트릭 timestamp 용도)
// ============================================================================

// 현재 시간을 "YYYY-MM-DD HH:MM:SS"로
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

// SYSTEMTIME을 "[YYYY-MM-DD HH:MM:SS]" 포맷으로
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

// ============================================================================
// Globals: received message pipeline
// ============================================================================

// 수신 메시지 큐 보호용 뮤텍스
std::mutex recv_m;

// 수신 메시지 큐(문자열 JSON 원문 저장)
// - producer(네트워크 수신)가 push
// - consumer(MessageHandler)가 pop
std::queue<std::string> recv_q;

// 수신 메시지 "개수"를 나타내는 세마포어
// - recv_q에 push한 뒤 recv_sem.release()로 알림
// - MessageHandler는 recv_sem.acquire()로 메시지 올 때까지 대기
std::counting_semaphore<MAX_COUNT> recv_sem(0);

// 소비자(로거/핸들러) 실행 플래그
// - 여러 스레드에서 읽고 쓰므로 atomic
std::atomic<bool> logger_consumer_running = { false };

// ============================================================================
// Globals: metric pipeline
// ============================================================================

// 키 입력 횟수 카운터(메트릭)
// - 여러 스레드에서 증가/읽을 수 있으니 atomic
std::atomic<uint64_t> g_keystrokes = { 0 };

// 윈도우(집계 간격) ms
// - 생산/소비/전송 주기 같은 곳에서 공유한다면 atomic이 안전
std::atomic<int> g_window_ms = { 1000 }; // default safe

// 메트릭 큐 보호용 뮤텍스
std::mutex metric_m;

// 메트릭 큐 "개수" 세마포어
// - metric_q에 push 후 metric_sem.release()
// - consumer는 metric_sem.acquire()로 대기
std::counting_semaphore<MAX_COUNT> metric_sem(0);

// 메트릭 큐 (json::object 형태로 저장)
// - producer가 json 만들어 push
// - consumer가 pop해서 전송/처리
std::queue<json::object> metric_q;

// 메트릭 수집 on/off 플래그(명령 START/STOP로 토글)
// - 훅 콜백/생산자/소비자 등 여러 스레드에서 읽히면 atomic이 필수
std::atomic<bool> metric_running = { false };

// ============================================================================
// ClockMapper: steady_clock -> system_clock(에폭 ms) 매핑
// ============================================================================

// steady_clock은 시스템 시간 변경(시간 보정/사용자 변경)에 영향을 안 받는 반면,
// system_clock은 epoch 기반이지만 시간 변경의 영향을 받음.
// 여기서는 "기준 시점"을 잡아 steady 시간을 epoch ms로 변환하는 용도.
ClockMapper::ClockMapper()
    : steady_base(std::chrono::steady_clock::now()),
    sys_base(std::chrono::system_clock::now()) {
}

// steady_clock time_point를 epoch(ms)로 변환
// - steady_base와 sys_base를 같은 순간으로 잡고,
//   이후 steady의 delta를 system_clock에 더해 epoch로 변환
int64_t ClockMapper::to_epoch_ms(std::chrono::steady_clock::time_point tp) const
{
    using namespace std::chrono;
    auto sys_tp = sys_base + (tp - steady_base);
    return duration_cast<milliseconds>(sys_tp.time_since_epoch()).count();
}
