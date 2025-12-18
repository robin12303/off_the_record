#pragma once   
#include "sync_globals.h"  
 

namespace json = boost::json;

class Consumer {
private: 
    std::thread thread_;  // std::jthread 대신 std::thread 사용
    std::atomic<bool> running_{ false };

public:
    Consumer();
    ~Consumer();

    void loop();
    void run();
    void stop();

    // 복사/이동 방지
    Consumer(const Consumer&) = delete;
    Consumer& operator=(const Consumer&) = delete;
    Consumer(Consumer&&) = delete;
    Consumer& operator=(Consumer&&) = delete;
};