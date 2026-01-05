#include "metric_consumer.h"

// MetricConsumer 역할:
// - metric_sem / metric_q를 소비(consume)해서
// - payload(json)를 꺼내고
// - Connection을 통해 서버(상대)로 전송한다.
void MetricConsumer::loop(std::stop_token st)
{
    // stop 요청이 오기 전까지 반복
    while (!st.stop_requested()) {

        // 디버그 로그: 매번 찍으면 출력이 병목이 될 수 있음(필요할 때만)
        std::cout << "MetricConsumer: wating for metric_sem..." << "\n";

        // "metric_q에 아이템이 들어올 때까지" 블로킹 대기
        // producer가 metric_sem.release()를 호출하면 깨어나서 진행.
        metric_sem.acquire();

        // 깨어난 직후 stop이 들어왔으면 종료
        // (acquire에서 깨웠는데 stop이면 더 처리하지 않기 위함)
        if (st.stop_requested()) break; // stop 대응

        json::object payload;

        {
            // metric_q는 producer/consumer가 공유하므로 mutex로 보호
            // 여기서는 front를 꺼내고 pop 하는 최소 범위만 락 잡음
            std::lock_guard<std::mutex> lk(metric_m);

            // queue의 front를 가져와 local payload에 이동(move)
            // payload는 loop 밖에서도 사용할 것이므로 락 밖에서 처리.
            payload = std::move(metric_q.front());
            metric_q.pop();
        }

        // 필터링 로직:
        // payload["keystrokes"]가 음수면(예: 센티널) 전송하지 않는다.
        // ⚠️ 이 조건은 "왜 음수가 오나?"라는 정책이 명확해야 유지보수하기 쉬움.
        if (payload["keystrokes"].as_int64() >= 0)
        {
            // 전송 메시지 구성
            // - prefix: "EVENT"로 통일(서버 라우팅 기준)
            // - machineUuid: 에이전트 식별자
            // - payload: metric payload를 문자열로 직렬화해서 담음
            // - taskType: "METRIC"
            //
            // ⚠️ payload를 문자열로 넣으면 서버에서는 다시 JSON parse가 필요함.
            //    payload를 object로 그대로 넣는 구조도 고려 가능:
            //    resp["payload"] = payload; (가능하면 이게 더 깔끔)
            json::object resp;
            resp["prefix"] = "EVENT";                 // 하나로 통일 추천
            resp["machineUuid"] = uuid_;
            resp["payload"] = json::serialize(payload);
            resp["taskType"] = "METRIC";

            // 직렬화 결과를 임시 문자열로 받으면 수명/참조 문제를 피하기 좋음
            std::string msg = json::serialize(resp);

            // Connection::send가 내부에서 큐잉(async_write)한다면 여기서 블로킹이 거의 없음.
            // move를 사용하면 복사를 줄일 수 있음(가능한 인터페이스라면)
            conn_->send(std::move(msg));
        }
    }
}

MetricConsumer::MetricConsumer(const std::string& uuid, std::shared_ptr<Connection> conn)
    : conn_(conn), uuid_(uuid)
{
}

MetricConsumer::~MetricConsumer()
{
    // 소멸 시 스레드 정리
    // ⚠️ loop가 metric_sem.acquire()에서 대기 중이면
    // stop()만으로 즉시 깨어나지 못해 join이 지연될 수 있음.
    stop();
}

void MetricConsumer::start()
{
    // 이미 실행 중이면 중복 시작 방지
    if (t_.joinable()) return;

    // loop를 별도 스레드에서 실행
    // jthread는 stop_token을 제공하고 소멸 시 join함
    t_ = std::jthread([this](std::stop_token st) {
        loop(st);
        });
}

void MetricConsumer::stop()
{
    if (t_.joinable()) {
        // stop 요청
        t_.request_stop();

        // 기존 jthread를 파괴(join)하기 위해 빈 jthread로 대입
        // ⚠️ loop가 metric_sem.acquire()에서 "영원히" 기다리면
        // 여기서 join이 멈출 수 있음.
        t_ = std::jthread();
    }
}
