#pragma once
#include <boost/asio.hpp>
#include <boost/beast.hpp> 
#include <boost/json.hpp> 
#include "Windows.h"
#include <semaphore>
#include <atomic> 
#include <mutex> 
#include <iostream> 
#include <string>
#include <atomic>
#include <thread>
#include <chrono>
#include "key_converter.h" 
#include "spec.h"
#include <queue>
namespace json = boost::json;
// 전역 공유 세마포어 (선언)


extern std::counting_semaphore<INT_MAX> sem;
extern std::mutex m;
extern std::queue<json::object> q;
extern std::atomic<bool> run_key_event;

 


 