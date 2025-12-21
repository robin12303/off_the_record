#pragma once   
#include "WsConnection.h"
#include "sync_globals.h"		
#include "CurrentTimeStamp.h"
#include "spec.h"
#include <boost/json.hpp>
namespace asio = boost::asio;
using namespace std::chrono_literals;
namespace json = boost::json;

class WsConsumer : public std::enable_shared_from_this<WsConsumer> {

public:
	WsConsumer(std::shared_ptr<WsConnection> conn); 
	~WsConsumer();
	// 복사/이동 방지
	WsConsumer(const WsConsumer&) = delete;
	WsConsumer& operator=(const WsConsumer&) = delete;
	WsConsumer(WsConsumer&&) = delete;
	WsConsumer& operator=(WsConsumer&&) = delete;
	void loop();
	void run();
	void stop();
private:
	std::thread thread_;  // std::jthread 대신 std::thread 사용
	std::atomic<bool> running_{ false };
	std::shared_ptr<WsConnection> conn_;
	 
};