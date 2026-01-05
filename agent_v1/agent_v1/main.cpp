#include "pch.hpp"
#include "metric_consumer.h"
#include "metric_producer.h"
#include "producer.h"
#include "received_message_handler.h"
#include "heartbeat.h"

// =========================
// Defaults (서버 접속 기본값)
// =========================
static constexpr const char* DEFAULT_HOST = "localhost";
static constexpr const char* DEFAULT_PORT = "8080";
static constexpr const char* DEFAULT_TARGET = "/ws/agent";

// Boost.Asio I/O 이벤트 루프 컨텍스트
// - async_* 작업들이 이 io_context에서 실행된다.
boost::asio::io_context ioc;

// work_guard:
// - ioc.run()이 "할 일 없으니 종료"해버리지 않도록 붙잡아두는 장치.
// - guard가 살아있는 한 ioc.run()은 이벤트를 계속 기다린다.
auto guard = boost::asio::make_work_guard(ioc);

// 동의(Consent) 확인 함수들
static bool ShowConsentDialogAndConfirm();
static bool EnsureConsent();

int main(int argc, char* argv[])
{
    // 프로그램 실행 전 사용자 동의 확인
    // - 동의 없으면 즉시 종료(의도적으로 실행을 막기)
    if (!EnsureConsent()) {
        std::cout << "프로그램이 종료됩니다." << "\n";
        return 0;
    }
    else {
        // 실행 인자에서 host/port/target 받기 (없으면 기본값)
        std::string host = (argc > 1) ? argv[1] : DEFAULT_HOST;
        std::string port = (argc > 2) ? argv[2] : DEFAULT_PORT;
        std::string target = (argc > 3) ? argv[3] : DEFAULT_TARGET;

        // 설치 ID(에이전트 UUID) 로드/생성
        // - 재실행해도 동일 UUID 유지 목적
        std::string uuid = LoadOrCreateInstallId();

        std::cout << "Connecting to host=" << host
            << " port=" << port
            << " target=" << target << "\n"
            << "UUID: " << uuid << "\n";

        // ⚠️ 주의: 전역 guard가 이미 있는데, 여기서 다시 guard를 새로 만들고 있음.
        // 여기 auto guard는 "전역 guard"를 가리는(섀도잉) 지역 변수다.
        // 기능상 큰 문제는 없을 수 있지만, 혼란을 줄이려면 하나만 유지하는 게 좋음.
        auto guard = boost::asio::make_work_guard(ioc);

        // io_context를 별도 스레드에서 run()
        // - 네트워크 async 작업 콜백들이 이 스레드에서 실행된다.
        std::jthread ioc_thread([&] {
            try {
                ioc.run();
                std::cout << "I/O context finished.\n";
            }
            catch (const std::exception& e) {
                std::cerr << "I/O context error: " << e.what() << "\n";
            }
            });

        // 네트워크 연결 객체(웹소켓 등) 생성
        auto conn = std::make_shared<Connection>(ioc);

        // Heartbeat: 주기적으로 상태/헬스체크 전송(여기서는 5초 주기)
        using namespace std::chrono_literals;
        auto heartbeat = std::make_shared<Heartbeat>(uuid, ioc, conn, 5s);

        // 수신 메시지 처리기: recv_q/recv_sem 기반으로 명령을 파싱하고 응답
        auto messageHandler = std::make_shared<MessageHandler>(uuid, conn);

        // MetricConsumer: metric_q/metric_sem 기반으로 메트릭을 꺼내 전송
        auto metrifc_consumer = std::make_shared<MetricConsumer>(uuid, conn);

        // MetricProducer: 일정 주기마다 g_keystrokes 등을 읽어 metric_q에 push
        // ⚠️ 여기만 shared_ptr이 아니라 값 객체로 생성됨(설계상 문제는 아님)
        auto metric_producer = MetricProducer();

        // Producer: WinAPI 키보드 훅 등 이벤트를 받아 g_keystrokes 증가 같은 역할
        auto producer = Producer();

        // 스레드/루프 시작 순서:
        // 1) consumer/handler/heartbeat 스레드 시작
        // 2) metric_producer/producer 시작
        // 3) 네트워크 연결 시작
        metrifc_consumer->start();
        messageHandler->start();
        heartbeat->start();
        metric_producer.start();
        producer.start();

        // 서버 연결 시작 (async connect/handshake/read/write 등)
        conn->start(host, port, target);

        // -------------------------
        // Windows 메시지 루프
        // -------------------------
        // WH_KEYBOARD_LL 같은 훅은 메시지 루프가 있어야 안정적으로 동작하는 경우가 많음.
        // GetMessage는 메시지가 올 때까지 블로킹하며, WM_QUIT을 받으면 0 리턴.
        MSG msg;
        while (GetMessage(&msg, nullptr, 0, 0) > 0)
        {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }

        // 여기 도달하면 메시지 루프 종료(보통 WM_QUIT)
        // ⚠️ 종료 시 각 컴포넌트 stop()을 호출해 정리하는 로직을 넣는 게 보통 더 안전함.
        return 0;
    }
}

// ---------------------------------------------------------------------------
// Consent UI
// ---------------------------------------------------------------------------

// 사용자 동의 다이얼로그 표시
// - MessageBoxW로 동의(Y/N) 받음
static bool ShowConsentDialogAndConfirm()
{
    // ⚠️ 문구는 프로젝트 성격에 맞게 조절 가능
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

    // YES를 눌렀을 때만 true
    if (r == IDYES)
    {
        return true;
    }
    return false;
}

// 동의 체크 래퍼(현재는 그냥 다이얼로그를 바로 띄움)
// - 확장 가능: 예) 한번 동의하면 파일/레지스트리에 기록해서 다음 실행엔 스킵 등
bool EnsureConsent()
{
    return ShowConsentDialogAndConfirm();
}
