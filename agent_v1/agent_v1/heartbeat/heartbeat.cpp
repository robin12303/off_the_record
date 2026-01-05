#include "heartbeat.h"

// tick: 주기적으로 HEARTBEAT 메시지를 보내는 "스케줄러" 역할
// - asio::steady_timer를 period_ 간격으로 재설정(expires_after)하고
// - async_wait 콜백에서 send 후 자기 자신을 다시 호출하여 반복한다.
//
// ⚠️ 중요: stop_token만으로는 async_wait을 즉시 깨우지 못한다.
//          stop() 시 timer_.cancel() 같은 취소가 없으면,
//          이미 걸린 wait 콜백이 실행될 수 있고, stop 이후에도 send가 1번 더 나갈 수 있다.
void Heartbeat::tick(std::stop_token st)
{
    // stop이 요청되지 않았을 때만 다음 타이머를 건다
    // (하지만 stop이 요청된 후에도 이미 등록된 async_wait은 실행될 수 있음)
    if (!st.stop_requested()) {

        // 다음 실행 시간 설정 (현재 시점 + period_)
        timer_.expires_after(period_);

        // async_wait: 타이머 만료 시 io_context에서 콜백 실행
        // - self=shared_from_this(): 콜백 실행 동안 객체 생존 보장(필수 패턴)
        // - st 캡처: stop 상태 확인에 사용 가능(같은 stop_source를 바라봄)
        timer_.async_wait([self = shared_from_this(), st](boost::system::error_code ec) {
            // ec가 있으면(취소 등) 그냥 종료
            if (ec) return;

            // ⚠️ stop이 요청되었더라도, timer를 cancel하지 않으면 여기까지 올 수 있음.
            // 원하면 여기서도 stop 확인 후 send를 스킵하는 방어를 넣을 수 있음.
            self->conn_->send(json::serialize(self->j));

            // 다음 주기 예약(재귀처럼 보이지만 콜백 기반 반복)
            self->tick(st);
            });
    }
}

Heartbeat::Heartbeat(
    const std::string uuid,              // ⚠️ 값 복사됨. const std::string& 로 받는 게 보통 더 낫다.
    asio::io_context& ioc,
    std::shared_ptr<Connection> conn,
    std::chrono::seconds period)
    :
    uuid_(uuid),

    // timer_를 strand에 묶음:
    // - 같은 strand에서 실행되도록 해서, 타이머 핸들러/작업이 직렬화되게 함
    // - 다만 "timer_에 대한 시작 호출"을 여러 스레드에서 동시에 하면 여전히 위험할 수 있음
    timer_(asio::make_strand(ioc)),

    // Connection 공유 포인터 저장
    conn_(std::move(conn)),

    // heartbeat 주기 저장
    period_(period)
{
    // 시스템 정보 수집(getSpec()) 결과를 sys_info_json에 넣음
    // ⚠️ getSpec()을 여러 번 호출하면 비용이 커질 수 있고,
    //    호출 사이에 값이 달라질 수 있음(드물지만). 보통은 한 번만 호출해서 재사용.
    sys_info_json["cpuName"] = getSpec().cpu_name;
    sys_info_json["gpuName"] = getSpec().gpu_name;
    sys_info_json["ramTotalMb"] = getSpec().ram;
    sys_info_json["osName"] = getSpec().os_name;
    sys_info_json["osVersion"] = getSpec().os_version;
    sys_info_json["machineUuid"] = uuid_;
    sys_info_json["hostName"] = getSpec().host_name;

    // payload를 문자열 JSON으로 직렬화
    auto payload = json::serialize(sys_info_json);

    // ⚠️ 아래 parse 및 o는 현재 코드에서 사용되지 않는다(불필요한 작업)
    //    디버그/검증 목적이 아니라면 제거 가능.
    json::value v = json::parse(payload);
    const json::object& o = v.as_object();

    // HEARTBEAT 메시지의 고정 필드 구성
    j["prefix"] = "HEARTBEAT";
    j["commandId"] = "N/A";
    j["taskType"] = "HEARTBEAT";

    // payload는 "JSON 문자열"로 넣고 있음.
    // (서버에서 다시 parse 해야 함)
    // 서버 프로토콜이 허용한다면 j["payload"] = sys_info_json; 처럼 object로 넣는 쪽이 더 깔끔함.
    j["payload"] = payload;

    // 이 에이전트의 uuid
    j["machineUuid"] = uuid_;
}

Heartbeat::~Heartbeat()
{
    // 소멸 시 정리
    // ⚠️ stop()이 timer_.cancel()을 하지 않으면,
    //    이미 등록된 async_wait 콜백이 살아있을 수 있음.
    stop();
}

void Heartbeat::start()
{
    // 이미 실행 중이면 중복 시작 방지
    if (t_.joinable()) return;

    // 별도 스레드에서 tick(st) 호출
    // ⚠️ tick()은 timer_.async_wait을 걸기 때문에 실제 콜백 실행은 ioc.run() 스레드에서 일어남.
    //    시작 자체는 굳이 별도 jthread가 없어도 ioc에 post해서 시작할 수도 있음.
    t_ = std::jthread([this](std::stop_token st) {
        tick(st);
        });
}

void Heartbeat::stop()
{
    if (t_.joinable()) {
        // stop 요청
        t_.request_stop();

        // ⚠️ 여기에서 timer_.cancel()이 없으면,
        //    대기 중인 async_wait이 계속 남아 콜백이 실행될 수 있음.
        //    (ec=operation_aborted로 빠지게 만들려면 cancel이 필요)
        //
        // 예) timer_.cancel();

        // 기존 jthread 소멸(join)
        t_ = std::jthread();
    }
}
