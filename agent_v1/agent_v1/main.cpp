#include "pch.hpp"
#include "metric_consumer.h"
#include "metric_producer.h"
#include "producer.h"
#include "received_message_handler.h"
#include "heartbeat.h"

// =========================
// Defaults
// =========================
static constexpr const char* DEFAULT_HOST = "localhost";
static constexpr const char* DEFAULT_PORT = "8080";
static constexpr const char* DEFAULT_TARGET = "/ws/agent";
boost::asio::io_context ioc;
auto guard = boost::asio::make_work_guard(ioc);
static bool ShowConsentDialogAndConfirm();
static bool EnsureConsent();
int main(int argc, char* argv[])
{  
    if (!EnsureConsent()) {
        std::cout << "프로그램이 종료됩니다." << "\n";
        return 0;
    }
    else {
        std::string host = (argc > 1) ? argv[1] : DEFAULT_HOST;
        std::string port = (argc > 2) ? argv[2] : DEFAULT_PORT;
        std::string target = (argc > 3) ? argv[3] : DEFAULT_TARGET;
        std::string uuid = LoadOrCreateInstallId();
        std::cout << "Connecting to host=" << host
            << " port=" << port
            << " target=" << target << "\n"
            << "UUID: " << uuid << "\n";

        auto guard = boost::asio::make_work_guard(ioc);
        std::jthread ioc_thread([&] {
            try {
                ioc.run();
                std::cout << "I/O context finished.\n";
            }
            catch (const std::exception& e) {
                std::cerr << "I/O context error: " << e.what() << "\n";
            }
            });
        auto conn = std::make_shared<Connection>(ioc);
        auto heartbeat = std::make_shared<Heartbeat>(uuid,ioc, conn, 5s);
        auto messageHandler = std::make_shared<MessageHandler>(uuid,conn);
        auto metrifc_consumer = std::make_shared<MetricConsumer>(uuid, conn);

        auto metric_producer = MetricProducer();
        auto producer = Producer();
       
        metrifc_consumer->start();
        messageHandler->start();
        heartbeat->start();
        metric_producer.start();
        producer.start();
        conn->start(host, port, target);
        // Windows 메시지 루프
        MSG msg;
        while (GetMessage(&msg, nullptr, 0, 0) > 0)
        {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
        return 0;
    }
    

}

// 프로그램 실행: <Ctrl+F5> 또는 [디버그] > [디버깅하지 않고 시작] 메뉴
// 프로그램 디버그: <F5> 키 또는 [디버그] > [디버깅 시작] 메뉴

// 시작을 위한 팁: 
//   1. [솔루션 탐색기] 창을 사용하여 파일을 추가/관리합니다.
//   2. [팀 탐색기] 창을 사용하여 소스 제어에 연결합니다.
//   3. [출력] 창을 사용하여 빌드 출력 및 기타 메시지를 확인합니다.
//   4. [오류 목록] 창을 사용하여 오류를 봅니다.
//   5. [프로젝트] > [새 항목 추가]로 이동하여 새 코드 파일을 만들거나, [프로젝트] > [기존 항목 추가]로 이동하여 기존 코드 파일을 프로젝트에 추가합니다.
//   6. 나중에 이 프로젝트를 다시 열려면 [파일] > [열기] > [프로젝트]로 이동하고 .sln 파일을 선택합니다.

static bool ShowConsentDialogAndConfirm()
{
    // ⚠️ 문구는 너 프로젝트 성격에 맞게 더 강하게/약하게 조절 가능
    std::wstring msg =
        L"[경고 / 사용자 동의 필요]\n\n"
        L"이 프로그램은 다음 정보를 수집할 수 있습니다:\n"
        L" - 키 입력 내용(문자/키 값) 수집 없음\n"
        L" - 키 입력 횟수(스트로크 카운트)만 집계\n"
        L" - CPU/GPU 모델명 등 하드웨어 식별 정보\n\n"
        L"동의 없이 실행/배포/사용하면 안 됩니다.\n"
        L"동의하지 않으면 즉시 종료하십시오.\n\n"
        L"이 내용을 읽고 이해했으며, 동의하십니까?";



    int r = MessageBoxW(
        nullptr,
        msg.c_str(),
        L"off_the_record - Consent",
        MB_ICONWARNING | MB_YESNO | MB_DEFBUTTON2 | MB_SYSTEMMODAL
    );

    if (r == IDYES)
    { 
        return true;
    }
    return false;
}

bool EnsureConsent()
{
    return ShowConsentDialogAndConfirm();
}
