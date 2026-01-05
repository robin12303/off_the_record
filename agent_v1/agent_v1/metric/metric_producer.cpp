#include "metric_producer.h"

void MetricProducer::loop(std::stop_token st)
{
    using namespace std::chrono;

    // ClockMapper:
    // steady_clock(단조 증가, 시스템 시간 변경 영향 없음) 기반 tick을
    // epoch(ms)로 변환하기 위해 기준점을 잡아 매핑해주는 도구.
    ClockMapper mapper;

    // 첫 윈도우 크기(ms)를 읽음
    // memory_order_relaxed: "값만" 빠르게 읽고, 다른 변수와의 동기화 의미는 최소화.
    // 여기서는 단순 설정값이므로 보통 OK.
    int w = g_window_ms.load(std::memory_order_relaxed);

    // 윈도우 크기 최소/최대 정책(너가 정한 안전 범위)
    if (w < 1000) w = 1000;          // 예: 최소 1초
    if (w > 60000) w = 60000;        // 예: 최대 1분

    // 윈도우 기간과 다음 tick 시각(steady_clock 기준)
    auto window = milliseconds(w);
    auto next_tick = steady_clock::now() + window;

    // stop 요청이 오기 전까지 반복
    while (!st.stop_requested()) {

        // 다음 tick까지 대기 (블로킹)
        // ⚠️ 주의: request_stop()은 sleep_until을 "즉시" 깨우지 못함.
        // 즉 stop() 호출해도 여기서 다음 tick까지 기다릴 수 있음(최대 window).
        std::this_thread::sleep_until(next_tick);

        // 이번 윈도우 동안 누적된 카운트를 읽고(반환), 동시에 0으로 초기화
        // exchange는 원자적으로 "읽기+초기화"라서 카운터 수집에 딱 맞음.
        auto c = g_keystrokes.exchange(0, std::memory_order_relaxed);

        // 이 윈도우가 끝난 시각(next_tick)을 epoch ms로 변환
        // (로깅/서버 전송용으로 steady 기반 시간을 실시간(epoch)으로 바꾸는 과정)
        int64_t window_end_ms = mapper.to_epoch_ms(next_tick);

        // 수집이 켜져 있을 때만 metric payload를 큐에 넣음
        // metric_running은 atomic이어야 안전함.
        if (metric_running) {

            // 타임스탬프(로컬 시간 문자열) 생성
            SYSTEMTIME st;
            GetLocalTime(&st);
            std::string timestamp = FormatTime(st);

            // JSON payload 구성
            // - windowMs: 집계 구간
            // - windowEndMs: 구간 종료 시각(epoch ms)
            // - keystrokes: 해당 구간 카운트
            // - timeStamp: 사람이 보기 쉬운 로컬 포맷 문자열
            //
            // ⚠️ 팁: JSON 생성은 락 밖에서 하고,
            //       공유 큐(metric_q)에 push할 때만 락 잡는 게 병목이 적음.
            json::object j;
            j["windowMs"] = w;
            j["windowEndMs"] = window_end_ms;
            j["keystrokes"] = static_cast<std::int64_t>(c); // boost::json은 int64로 넣는 게 안전
            j["timeStamp"] = timestamp;

            // 공유 큐에 push (producer-consumer 패턴)
            {
                std::lock_guard<std::mutex> lk(metric_m);
                metric_q.push(std::move(j));
            }

            // 소비자에게 "아이템 1개 생김" 신호
            metric_sem.release();
        }

        // 다음 tick 전에 최신 window_ms를 다시 읽어서 동적으로 반영
        // (실시간으로 window 크기 변경 가능하게)
        int new_w = g_window_ms.load(std::memory_order_relaxed);
        if (new_w < 1000) new_w = 1000;
        if (new_w > 60000) new_w = 60000;

        w = new_w;
        window = milliseconds(w);

        // 박자 유지 핵심:
        // now()+window 로 재설정하면 드리프트(밀림)가 누적되기 쉬움.
        // next_tick += window 처럼 "누적"해야 일정한 리듬 유지.
        next_tick += window;

        // 처리 지연으로 tick을 놓쳤으면 따라잡기
        // (예: sleep에서 깨어났는데 이미 next_tick이 과거가 됐을 때)
        auto now = steady_clock::now();
        while (next_tick <= now) {
            next_tick += window;
        }
    }
}

MetricProducer::MetricProducer()
{
}

MetricProducer::~MetricProducer()
{
    // 소멸 시 스레드 정리
    // ⚠️ loop가 sleep_until 중이면 stop()이 최대 window까지 지연될 수 있음.
    stop();
}

void MetricProducer::start()
{
    // 이미 실행 중이면 중복 시작 방지
    if (t_.joinable()) return;

    // loop를 별도 스레드에서 실행
    // std::jthread는 stop_token을 제공하고, 소멸 시 join 수행
    t_ = std::jthread([this](std::stop_token st) {
        loop(st);
        });
}

void MetricProducer::stop()
{
    // 수집 플래그 끔 (의미: "더 이상 큐에 넣지 마라")
    // ⚠️ 이건 "스레드 종료"와 별개임. loop 스레드는 request_stop까지 필요.
    metric_running = 0;

    if (t_.joinable()) {
        // stop 요청
        t_.request_stop();

        // 기존 jthread를 파괴(= join 시도)하기 위해 빈 jthread로 대입
        // ⚠️ loop가 sleep_until에 걸려있으면 join이 그만큼 지연될 수 있음.
        t_ = std::jthread();
    }
}
