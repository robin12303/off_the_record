#pragma once
#include "pch.hpp"

class Connection : public std::enable_shared_from_this<Connection> {
public:
	explicit Connection(asio::io_context& ioc);
	
	void start(std::string host, std::string port, std::string target);
	void send(std::string msg);


	websocket::stream<tcp::socket>& ws();
	asio::any_io_executor get_executor();

private:
	void do_write();
	void do_read();
	void fail(const char* what, beast::error_code ec);

	asio::strand<asio::any_io_executor> strand_;
	tcp::resolver resolver_;
	websocket::stream<tcp::socket> ws_;
	beast::flat_buffer inbuf_;

	std::string host_, port_, target_;
	std::deque<std::string> outq_;
	bool open_ = false;


};