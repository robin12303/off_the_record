#pragma once

// ============================================================================
// 공통 헤더(pch): 프로젝트 전반에서 쓰는 라이브러리/타입/전역 선언 모음
// - 빌드 속도(PCH)용 + 공통 유틸/전역 파이프라인 선언
// ============================================================================

#include <boost/asio.hpp>
#include <boost/beast.hpp>
#include <boost/json.hpp>

// 동시성/동기화
#include <semaphore>
#include <atomic>
#include <mutex>
#include <thread>
#include <stop_token>

// 표준 유틸
#include <iostream>
#include <string>
#include <chrono>
#include <unordered_map>
#include <sstream>
#include <iomanip>
#include <stdexcept>
#include <vector>
#include <memory>
#include <deque>
#include <queue>
#include <algorithm>
#include <format>
#include <cstdint>

// Windows / COM / WMI
#include <Windows.h>
#include <wbemidl.h>
#include <comdef.h>
#include <wrl/client.h>

// 파일/경로
#include <fstream>
#include <filesystem>

// WMI/UUID 관련 링크 라이브러리
#pragma comment(lib, "wbemuuid.lib")
#pragma comment(lib, "Rpcrt4.lib")

// ---------------------------------------------------------------------------
// 네임스페이스 별칭(편하게 쓰려고)
// ---------------------------------------------------------------------------
namespace json = boost::json;
namespace asio = boost::asio;
namespace beast = boost::beast;
namespace websocket = beast::websocket;

// chrono 리터럴(5s, 100ms 같은 표현)
using namespace std::chrono_literals;

// TCP 타입 별칭
using tcp = asio::ip::tcp;

// ---------------------------------------------------------------------------
// 세마포어 최대 카운트 상수
// - counting_semaphore의 템플릿 파라미터로 사용
// - "큐에 쌓일 수 있는 최대 신호 개수"의 상한(정책적으로 조절 가능)
// ---------------------------------------------------------------------------
constexpr int MAX_COUNT = 1024;

// ============================================================================
// UUID / Install ID
// ============================================================================

// 새 UUID 문자열 생성 (Windows RPC API 사용)
std::string NewUuid();

// 설치/기기 식별용 ID를 파일에서 로드하거나 새로 생성 후 저장
std::string LoadOrCreateInstallId();

// ============================================================================
// UTF16 <-> UTF8 (문자열 변환 유틸)
// ============================================================================

// UTF-16(wstring) -> UTF-8(string)
std::string WideToUtf8(const std::wstring& w);

// 문자열 앞뒤 공백 제거
std::wstring Trim(const std::wstring& s);

// vector<wstring>를 sep로 join
std::wstring Join(const std::vector<std::wstring>& v, const wchar_t* sep);

// ============================================================================
// Registry helpers (Windows 버전 정보 등 읽는 용도)
// ============================================================================

// 레지스트리 REG_SZ 읽기
std::wstring RegGetSz(HKEY root, const wchar_t* subKey, const wchar_t* valueName);

// 레지스트리 REG_DWORD 읽기
bool RegGetDword(HKEY root, const wchar_t* subKey, const wchar_t* valueName, DWORD& out);

// ============================================================================
// WMI wrapper (CPU/GPU 같은 HW 정보 수집 용도)
// ============================================================================

class Wmi {
private:
    // HRESULT를 16진 문자열로 포맷(에러 메시지에 쓰기 좋음)
    static std::string HrHex(HRESULT hr);

    // 프로세스 전체에서 1회만 COM 보안 초기화(CoInitializeSecurity)
    static void InitSecurityOnce();

    // ComApartment: 스레드 단위 COM 초기화/정리 RAII
    // - COINIT_MULTITHREADED로 CoInitializeEx 호출
    // - 성공하면 소멸 시 CoUninitialize 호출
    // - 이미 다른 모드로 초기화된 경우(RPC_E_CHANGED_MODE)는 "이미 COM이 켜져있다"로 보고 넘어감
    struct ComApartment {
        bool active = false; // 내가 CoInitializeEx 성공했으면 true
        ComApartment() {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            if (hr == S_OK || hr == S_FALSE) {
                active = true;
            }
            else if (hr == RPC_E_CHANGED_MODE) {
                // 이미 다른 모드(STA 등)로 init된 스레드
                // (이 경우 CoUninitialize를 내가 호출하면 안 될 수 있으니 active=false)
                active = false;
            }
            else {
                throw std::runtime_error("CoInitializeEx failed: " + HrHex(hr));
            }
        }
        ~ComApartment() noexcept {
            if (active) CoUninitialize();
        }
    };

    // 중요: 멤버 선언 순서가 파괴 순서를 결정함(역순 파괴)
    // - services_가 먼저 Release되고, com_은 마지막에 CoUninitialize
    ComApartment com_;
    Microsoft::WRL::ComPtr<IWbemServices> services_;

public:
    // 생성자에서 WMI 서비스 연결/프록시 설정 등을 수행
    Wmi();

    // 기본 소멸자로 충분(ComPtr이 Release하고 com_이 CoUninitialize)
    // ~Wmi() = default;

    // 특정 WMI 클래스에서 문자열 속성 목록을 조회
    // 예: Win32_Processor.Name, Win32_VideoController.Name
    std::vector<std::wstring> QueryStringList(const std::wstring& className, const std::wstring& propName);
};

// ============================================================================
// Hostname (컴퓨터 이름)
// ============================================================================

// fqdn=true면 FQDN, false면 호스트명만
std::wstring GetHostNameW(bool fqdn);
std::string GetHostNameUtf8(bool fqdn);

// ============================================================================
// Spec: 수집한 시스템 정보를 담는 구조체
// ============================================================================

struct Spec {
    std::string cpu_name;     // CPU 모델명
    std::string gpu_name;     // GPU 모델명(없으면 empty)
    std::string ram;          // RAM (ex: "16.00 GB")
    std::string os_name;      // OS 이름 (ex: "Windows 11 Pro")
    std::string os_version;   // OS 버전 (ex: "23H2 (10.0.22631.2861)")
    std::string machine_guid; // 머신 GUID 같은 식별자(미구현이면 "(unknown)" 등)
    std::string host_name;    // 호스트명(필요 시)
};

// ============================================================================
// Collectors (Spec 구성 유틸)
// ============================================================================

// RAM GB를 "xx.xx GB" 문자열로 포맷
std::string FormatRamGbString(double gb);

// 설치 RAM을 GB로 얻기(GetPhysicallyInstalledSystemMemory 사용)
bool TryGetInstalledRamGb(double& outGb);

// 레지스트리 기반 OS 이름/버전 수집
void GetWindowsOsNameVersion(std::string& outName, std::string& outVersion);

// 전체 Spec 수집 함수(CPU/GPU/RAM/OS/hostname 등)
Spec getSpec();

// ============================================================================
// Time formatting (로그/메트릭 timestamp)
// ============================================================================

// 현재 시간을 문자열로 생성(사람 읽기용)
std::string GetCurrentTimestamp();

// SYSTEMTIME을 "[YYYY-MM-DD HH:MM:SS]" 형태로 포맷
std::string FormatTime(const SYSTEMTIME& st);

// ============================================================================
// 전역 파이프라인: received message (네트워크 수신 → 메시지 처리)
// ============================================================================

// 수신 메시지 큐 보호용 뮤텍스
extern std::mutex recv_m;

// 수신 메시지 큐(문자열 JSON 원문)
extern std::queue<std::string> recv_q;

// 큐에 들어온 메시지 개수를 나타내는 세마포어
// - producer: push 후 release()
// - consumer: acquire()로 대기 후 pop
extern std::counting_semaphore<MAX_COUNT> recv_sem;

// ============================================================================
// 전역 파이프라인: metric (메트릭 생산 → 큐 → 전송)
// ============================================================================

// 메트릭 카운터(예: 키 입력 횟수)
// - 여러 스레드(훅 콜백/프로듀서 등)에서 접근하므로 atomic
extern std::atomic<uint64_t> g_keystrokes;

// metric_q 보호용 뮤텍스
extern std::mutex metric_m;

// metric_q 아이템 개수 세마포어
extern std::counting_semaphore<MAX_COUNT> metric_sem;

// metric payload 큐(JSON object)
extern std::queue<json::object> metric_q;

// 메트릭 수집 on/off 플래그(START/STOP 명령으로 토글)
extern std::atomic<bool> metric_running;

// 메트릭 집계 윈도우(ms). 동적으로 바꿀 수 있는 설정값
extern std::atomic<int> g_window_ms; // default safe

// ---------------------------------------------------------------------------
// ClockMapper:
// - steady_clock 기반 tick을 epoch(ms)로 변환하기 위한 매핑 도구
// - steady_clock은 시스템 시간 변경에 영향을 안 받기 때문에 주기 작업에 안정적
// - epoch로 보내려면 기준점을 잡아 system_clock으로 투영하는 방식이 필요함
// ---------------------------------------------------------------------------
struct ClockMapper {
    std::chrono::steady_clock::time_point steady_base;
    std::chrono::system_clock::time_point sys_base;

    ClockMapper();

    // steady time_point를 epoch ms로 변환
    int64_t to_epoch_ms(std::chrono::steady_clock::time_point tp) const;
};
