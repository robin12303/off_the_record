#include "received_message_handler.h"

// MessageHandler는 "수신된 메시지 큐(recv_q)"를 소비(consume)해서
// 명령을 파싱하고, 상태(예: metric_running)를 변경한 뒤 응답을 전송하는 역할.
MessageHandler::MessageHandler(const std::string& uuid, std::shared_ptr<Connection> conn)
    : uuid_(uuid), conn_(std::move(conn)) // uuid_는 이 에이전트 식별자, conn_은 C2 연결(전송용)
{
}

MessageHandler::~MessageHandler()
{
    // 주의: stop() 호출 없이 파괴되면 t_ 정리가 애매해질 수 있음.
    // (jthread는 소멸 시 join을 수행하지만, acquire에서 영원히 대기하면 종료가 지연될 수 있음)
}

void MessageHandler::start()
{
    // 이미 스레드가 돌고 있으면 중복 시작 방지
    if (t_.joinable()) return;

    // std::jthread: 스레드 종료 요청(stop_token) 지원 + 소멸 시 join
    // loop(st)를 별도 스레드에서 실행하여 메시지 처리 루프를 돌린다.
    t_ = std::jthread([this](std::stop_token st) { loop(st); });
}

void MessageHandler::stop()
{
    // 실행 중이면 stop 요청을 보내고 스레드를 정리한다.
    // ⚠️ 주의: loop 내부가 recv_sem.acquire()에서 블로킹 중이면
    // stop_requested()를 확인할 기회가 없어 종료가 지연/정지될 수 있다.
    if (t_.joinable()) {
        t_.request_stop();

        // 새로운 jthread로 대입하면 기존 jthread가 소멸되며 join 시도.
        // acquire 블로킹이 풀리지 않으면 여기서 join이 오래 걸리거나 멈출 수 있음.
        t_ = std::jthread();
    }
}

void MessageHandler::loop(std::stop_token st)
{
    // stop 요청이 오기 전까지 반복 처리
    while (!st.stop_requested()) {
        std::cout << "wating recv... " << "\n";

        // recv_sem은 "큐에 메시지가 들어왔다"는 신호 역할.
        // 메시지가 없으면 여기서 블로킹됨.
        // (비동기 시스템에서도 워커 스레드가 idle일 때 blocking wait 하는 건 흔함)
        recv_sem.acquire();

        // 큐에서 메시지 1개 꺼내기
        std::string msg = "null";
        {
            // recv_q는 producer(수신 스레드 등)와 consumer(이 스레드)가 공유하므로 mutex로 보호
            std::lock_guard<std::mutex> lk(recv_m);
            msg = recv_q.front();
            recv_q.pop();
        }

        std::cout << "[MessageHandler] received: " << msg << "\n";

        // 메시지(JSON) 파싱
        // ⚠️ boost::json::parse는 예외를 던질 수 있는데, 현재 코드는 error_code 방식으로 처리하려는 듯 보이나
        // ec를 parse에 전달하지 않고 있어서 ec는 항상 "성공"처럼 남을 가능성이 큼.
        // (즉, invalid JSON이면 예외로 프로그램이 죽을 수 있음)
        boost::system::error_code ec;
        json::object data = json::parse(msg).as_object();

        // ec가 세팅되었을 때 오류 처리(현 상태로는 제대로 동작 안 할 수 있음)
        if (ec) {
            std::cout << ec.message() << "\n";
        }
        else {
            // 공통 필드 추출
            std::string prefix = std::string(data["prefix"].as_string());     // 명령 종류(예: HEARTBEAT, METRICS)
            std::string commandId = std::string(data["commandId"].as_string());  // 명령 식별자
            std::string machineUuid = uuid_;                                      // 이 에이전트 식별자
            std::string taskType = std::string(data["taskType"].as_string());   // START / STOP 등

            // 로컬 타임스탬프 생성
            SYSTEMTIME st;
            GetLocalTime(&st);

            // 응답 JSON 구성
            json::object resp;
            resp["prefix"] = prefix;
            resp["commandId"] = commandId;
            resp["machineUuid"] = machineUuid;
            resp["taskType"] = taskType;

            // timestamp는 만들어두었지만 resp에 넣지는 않았음(추가 가능)
            std::string timestamp = FormatTime(st);

            // prefix별 명령 처리
            if (prefix == "HEARTBEAT") {
                // TO-DO: heartbeat 응답 로직(예: 상태/버전/업타임 등)
            }
            else if (prefix == "METRICS") {
                // METRICS 명령: metric_running 플래그로 생산자/소비자 동작을 켜고 끈다고 가정

                if (taskType == "START") {
                    if (metric_running) {
                        // 이미 켜져있으면 중복 시작 방지
                        resp["payload"] = "ALREADY_RUNNING";
                    }
                    else {
                        // 실제 시작은 다른 스레드가 metric_running을 보고 수행(여기는 상태만 변경)
                        metric_running = true;
                        resp["payload"] = "OK";
                    }
                }
                else if (taskType == "STOP") {
                    if (!metric_running) {
                        // 이미 꺼져있으면 중복 중단 방지
                        resp["payload"] = "ALREADY_STOPPED";
                    }
                    else {
                        // 실제 중단도 다른 스레드가 처리(여기는 상태만 변경)
                        metric_running = false;
                        resp["payload"] = "OK";
                    }
                }
                else {
                    // 알 수 없는 taskType
                    resp["payload"] = "RESPONSE_ERROR";
                }

                // 응답 전송
                // conn_->send가 내부적으로 async_write 큐잉이면 여기서 블로킹 거의 없음.
                // 동기 send면 네트워크 상태에 따라 블로킹될 수 있음.
                conn_->send(json::serialize(resp));
            }
        }
    }
}
