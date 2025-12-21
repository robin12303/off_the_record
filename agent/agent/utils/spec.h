#pragma once 
#define WIN32_LEAN_AND_MEAN
#define _WIN32_DCOM
#include <Windows.h>
#include <wbemidl.h>
#include <comdef.h>
#include <wrl/client.h>

#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <stdexcept>
#include <algorithm>

#pragma comment(lib, "wbemuuid.lib")

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
std::wstring GetMachineGuid();

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
