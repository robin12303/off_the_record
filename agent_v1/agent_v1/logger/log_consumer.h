#pragma once
#include "pch.hpp"
#include "connection.h"
class LogConsumer : public std::enable_shared_from_this<LogConsumer> {
private:
	std::jthread t_;
	std::shared_ptr<Connection> conn_;
	const std::string uuid_;
	void loop(std::stop_token st);
public:

	LogConsumer(const std::string& uuid, std::shared_ptr<Connection> conn);
	~LogConsumer();
	// 복사/이동 방지

	LogConsumer(const LogConsumer&) = delete;
	LogConsumer& operator=(const LogConsumer&) = delete;
	LogConsumer(LogConsumer&&) = delete;
	LogConsumer& operator=(LogConsumer&&) = delete;
	 
	void start(); 
	void stop();


};