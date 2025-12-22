#include "ws_consumer.h"

WsConsumer::WsConsumer(std::shared_ptr<WsConnection> conn)
    :
    conn_(std::move(conn))
{

}

WsConsumer::~WsConsumer()
{
    stop();
}

void WsConsumer::run()
{
    // 람다 함수로 감싸서 스레드 실행
    thread_ = std::thread([this]() { this->loop(); });
}

void WsConsumer::stop()
{
}

void WsConsumer::loop()
{
    running_ = true;
    while (running_) {
        printf("Consumer waiting for data...\n");

        json::object data;

            sem.acquire();
        {
            std::lock_guard<std::mutex> lock(m);
            data = q.front();
            q.pop();
        }

        if (!running_) {
            break;  // 종료 요청이 있으면 루프 탈출
        } 

        {
            std::lock_guard<std::mutex> lock(km);
            if (run_key_event) {
                printf("Consumer received:\n\ttimeStamp:%s\n\tcapsLock:%s\n\teventType:%s\n\tkeyString:%s\n",
                    data["timeStamp"].as_string().c_str(),
                    data["capsLock"].as_string().c_str(),
                    data["eventType"].as_string().c_str(),
                    data["keyString"].as_string().c_str());

                json::object j;
                j["prefix"] = "EVENT";
                j["machineGuid"] = getSpec().machine_guid;
                j["taskType"] = "KEY";
                j["payload"] = json::serialize(data);

                conn_->send(json::serialize(j));
            } 
        } 
    }
}
