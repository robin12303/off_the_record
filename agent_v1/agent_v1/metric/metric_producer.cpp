#include "metric_producer.h"

void MetricProducer::loop(std::stop_token st)
{
    using namespace std::chrono;

    ClockMapper mapper;

    // 첫 윈도우 크기 (ms) 가져오기 (최소/최대는 네가 원하는 정책으로)
    int w = g_window_ms.load(std::memory_order_relaxed);
    if (w < 1000) w = 1000;          // 예: 최소 1초
    if (w > 60000) w = 60000;        // 예: 최대 1분

    auto window = milliseconds(w);
    auto next_tick = steady_clock::now() + window;

    while (!st.stop_requested()) {
        std::this_thread::sleep_until(next_tick);

        // 이번 윈도우 결과: (콜백이 올린 카운트를 읽으면서 0으로 초기화)
        auto c = g_keystrokes.exchange(0, std::memory_order_relaxed); 
        // 이 윈도우가 "끝난" 시각(틱 경계)을 epoch ms로 변환
        int64_t window_end_ms = mapper.to_epoch_ms(next_tick);
        if (metric_running) {
            // 타임스템프
            SYSTEMTIME st;
            GetLocalTime(&st);
            std::string timestamp = FormatTime(st);

            // JSON은 락 밖에서 만들고, 공유 데이터에 넣을 때만 락 잡는 게 덜 막힘
            json::object j;
            j["windowMs"] = w;
            j["windowEndMs"] = window_end_ms;
            j["keystrokes"] = static_cast<std::int64_t>(c); // boost::json이면 int64로 넣는 게 안전
            j["timeStamp"] = timestamp;
            {
                std::lock_guard<std::mutex> lk(metric_m);
                metric_q.push(std::move(j));
            }
            metric_sem.release();
        }

      

        // 다음 틱을 위해 최신 window_ms를 다시 읽어서 반영 (프론트에서 바꿀 수 있다 했지?)
        int new_w = g_window_ms.load(std::memory_order_relaxed);
        if (new_w < 1000) new_w = 1000;
        if (new_w > 60000) new_w = 60000;

        w = new_w;
        window = milliseconds(w);

        // “박자 유지” 핵심: now()+window로 재설정하지 말고, 누적으로 next_tick += window
        next_tick += window;

        // 처리 지연으로 틱을 놓쳤으면 따라잡기
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
	stop();
}

void MetricProducer::start()
{ 
    if (t_.joinable()) return;

    t_ = std::jthread([this](std::stop_token st) {
        loop(st);
        });
}

void MetricProducer::stop()
{
    metric_running = 0;
    if (t_.joinable()) {
        t_.request_stop();
        t_ = std::jthread();
    }
}
