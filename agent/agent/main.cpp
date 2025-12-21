// main.cpp


 

#include "sync_globals.h"
#include "ws_heartbeat.h"
#include "producer.h"
#include "ws_consumer.h"
#include <iostream>
#include <string>
#include <thread>
#include <memory>
#include <chrono>
#include <filesystem>
#include <fstream> 
#include <ShlObj.h>   // SHGetKnownFolderPath
#include <objbase.h>  // CoTaskMemFree
namespace json = boost::json;
using namespace std::chrono_literals;

// =========================
// Defaults
// =========================
static constexpr const char* DEFAULT_HOST = "localhost";
static constexpr const char* DEFAULT_PORT = "8080";
static constexpr const char* DEFAULT_TARGET = "/ws/agent";

// =========================
// Consent (EULA / Warning)
// =========================

// 문구/정책 바꾸면 버전 올리세요 (v1 -> v2)
// 버전이 바뀌면 다시 동의 받게 됩니다.
static constexpr const wchar_t* CONSENT_VERSION = L"v1";

static std::filesystem::path GetConsentPath()
{
    PWSTR localAppData = nullptr;
    HRESULT hr = SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, nullptr, &localAppData);
    if (FAILED(hr) || !localAppData)
    {
        // 실패 시 임시 폴더 fallback
        return std::filesystem::temp_directory_path() / "off_the_record_consent_v1.txt";
    }

    std::filesystem::path p(localAppData);
    CoTaskMemFree(localAppData);

    p /= "off_the_record";
    std::error_code ec;
    std::filesystem::create_directories(p, ec);

    p /= (std::wstring(L"consent_") + CONSENT_VERSION + L".txt");
    return p;
}

static bool HasStoredConsent()
{
    try
    {
        auto path = GetConsentPath();
        std::ifstream in(path);
        if (!in.is_open()) return false;

        std::string line;
        std::getline(in, line);
        return line == "ACCEPTED";
    }
    catch (...)
    {
        return false;
    }
}

static void StoreConsent()
{
    try
    {
        auto path = GetConsentPath();
        std::ofstream out(path, std::ios::trunc);
        if (!out.is_open()) return;

        // 최소한만 저장 (민감정보 금지)
        out << "ACCEPTED\n";
        out << "version=" << "v1" << "\n";
        out << "note=local consent for learning/test purpose\n";
    }
    catch (...)
    {
        // 저장 실패해도 진행은 가능하게(필수는 아님)
    }
}

static bool ShowConsentDialogAndConfirm()
{
    // ⚠️ 문구는 너 프로젝트 성격에 맞게 더 강하게/약하게 조절 가능
    std::wstring msg =
        L"[WARNING / CONSENT REQUIRED]\n\n"
        L"This project may collect system events (e.g., keyboard input) on Windows.\n"
        L"It is intended for learning/testing ONLY.\n\n"
        L"- Do NOT use without the user's explicit consent.\n"
        L"- Do NOT collect/transmit passwords or personal data.\n"
        L"- Stop immediately if you do not agree.\n\n"
        L"Do you understand and agree to proceed?";

    int r = MessageBoxW(
        nullptr,
        msg.c_str(),
        L"off_the_record - Consent",
        MB_ICONWARNING | MB_YESNO | MB_DEFBUTTON2 | MB_SYSTEMMODAL
    );

    if (r == IDYES)
    {
        StoreConsent();
        return true;
    }
    return false;
}

// 동의 체크: 이미 동의했으면 통과, 아니면 팝업
static bool EnsureConsent() {
    return ShowConsentDialogAndConfirm(); // HasStoredConsent/StoreConsent 사용 안 함
}

// =========================
// Helper
// =========================
void RunIoContext(boost::asio::io_context& ioc)
{
    ioc.run();
}

// =========================
// main
// =========================
int main(int argc, char* argv[])
{
    // ✅ 동의 먼저. 동의 없으면 수집/전송/연결 자체를 시작하지 않음.
    if (!EnsureConsent())
    {
        std::cout << "Consent not granted. Exiting.\n";
        return 0;
    }

    std::string host = (argc > 1) ? argv[1] : DEFAULT_HOST;
    std::string port = (argc > 2) ? argv[2] : DEFAULT_PORT;
    std::string target = (argc > 3) ? argv[3] : DEFAULT_TARGET;

    std::cout << "Connecting to host=" << host
        << " port=" << port
        << " target=" << target << "\n";

    // sys_info (동의 이후에만 수집)
    auto sys_info = getSpec();

    json::object sys_info_json;
    sys_info_json["cpuName"] = sys_info.cpu_name;
    sys_info_json["gpuName"] = sys_info.gpu_name;
    sys_info_json["ramTotalMb"] = sys_info.ram;
    sys_info_json["osName"] = sys_info.os_name;
    sys_info_json["osVersion"] = sys_info.os_version;
    sys_info_json["machineGuid"] = sys_info.machine_guid;
    sys_info_json["hostName"] = sys_info.host_name;

    auto payload = json::serialize(sys_info_json);

    std::cout << "osName: " << sys_info.os_name << "\n";

    boost::asio::io_context ioc;

    auto conn = std::make_shared<WsConnection>(ioc);
    conn->start(host, port, target);

    Producer producer;

    // I/O 컨텍스트를 별도 스레드에서 실행
    std::thread io_thread([&ioc]() {
        try
        {
            ioc.run();
            std::cout << "I/O context finished.\n";
        }
        catch (const std::exception& e)
        {
            std::cerr << "I/O context error: " << e.what() << "\n";
        }
        });

    auto ws_heartbeat = std::make_shared<WsHeartbeat>(ioc, conn, payload, 5s);
    auto ws_consumer = std::make_shared<WsConsumer>(conn);

    ws_heartbeat->start();
    ws_consumer->run();

    producer.start();

    // Windows 메시지 루프
    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0) > 0)
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    // 정리
    producer.Stop();
    ws_consumer->stop();

    // I/O 컨텍스트 정리
    ioc.stop();
    if (io_thread.joinable())
    {
        io_thread.join();
    }

    std::cout << "Application exited.\n";
    return 0;
}
