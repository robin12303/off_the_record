#include "pch.hpp"
#include "connection.h"

class Heartbeat 
	: 
	public std::enable_shared_from_this<Heartbeat>
{
private:
	std::chrono::seconds period_;
	asio::steady_timer timer_;
	std::jthread t_;
	std::shared_ptr<Connection> conn_;
	json::object j;
	json::object sys_info_json; 
	const std::string uuid_;
	void tick(std::stop_token st);
public:

	Heartbeat(
		const std::string uuid,
		asio::io_context& ioc,
		std::shared_ptr<Connection> conn,
		std::chrono::seconds period
	);
	~Heartbeat();
	// 복사/이동 방지

	Heartbeat(const Heartbeat&) = delete;
	Heartbeat& operator=(const Heartbeat&) = delete;
	Heartbeat(Heartbeat&&) = delete;
	Heartbeat& operator=(Heartbeat&&) = delete;

	void start();
	void stop();
};