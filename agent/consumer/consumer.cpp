#include "consumer.h"

Consumer::Consumer() 
{
}

Consumer::~Consumer()
{
    stop();
}

void Consumer::loop()
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

        // auto data = st_.getData();


        printf("Consumer received:\n\ttimeStamp:%s\n\tcapsLock:%s\n\teventType:%s\n\tkeyString:%s\n",
            data["timeStamp"].as_string().c_str(),
            data["capsLock"].as_string().c_str(),
            data["eventType"].as_string().c_str(),
            data["keyString"].as_string().c_str());
    }

    std::cout << "Consumer stopped.\n";
}

void Consumer::run()
{
    // 람다 함수로 감싸서 스레드 실행
    thread_ = std::thread([this]() { this->loop(); });
}

void Consumer::stop()
{
    if (!running_) return;

    running_ = false;

    // 세마포어를 해제하여 loop()가 깨어나도록 함
    sem.release();

    // 스레드가 실행 중이면 종료 대기
    if (thread_.joinable()) {
        thread_.join();
    }
}