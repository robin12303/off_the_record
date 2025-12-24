#include "metric_consumer.h"

void MetricConsumer::loop(std::stop_token st)
{
    while (!st.stop_requested()) {
        std::cout << "MetricConsumer: wating for metric_sem..." << "\n";
        metric_sem.acquire();
        if (st.stop_requested()) break; // stop 대응

        json::object payload;

        {
            std::lock_guard<std::mutex> lk(metric_m); 
            payload = std::move(metric_q.front());
            metric_q.pop();
        }

        if (payload["keystrokes"].as_int64() >= 0)
        {

            json::object resp;
            resp["prefix"] = "EVENT";                 // 하나로 통일 추천
            resp["machineUuid"] = uuid_;
            resp["payload"] = json::serialize(payload);
            resp["taskType"] = "METRIC";

            std::string msg = json::serialize(resp);  // 임시 문자열 수명 문제 방지
            conn_->send(std::move(msg));              // move 가능하면 사용
        }
    }
}

MetricConsumer::MetricConsumer(const std::string& uuid,std::shared_ptr<Connection> conn)
	: conn_(conn), uuid_(uuid)
{
}

MetricConsumer::~MetricConsumer()
{
	stop();
}

void MetricConsumer::start()
{
    if (t_.joinable()) return;

    t_ = std::jthread([this](std::stop_token st) {
        loop(st);
        });
}

void MetricConsumer::stop()
{
    if (t_.joinable()) {
        t_.request_stop();
        t_ = std::jthread();
    }
}
