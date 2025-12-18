#pragma once
#include <boost/asio.hpp>
#include <boost/beast.hpp>
#include <boost/json.hpp>
#include <deque>
#include <string>
#include <memory>
#include <iostream>
#include "sync_globals.h"
namespace json = boost::json;
namespace asio = boost::asio;
namespace beast = boost::beast;
namespace websocket = beast::websocket;
using tcp = asio::ip::tcp;

class WsConnection : public std::enable_shared_from_this<WsConnection> {
public:
	explicit WsConnection(asio::io_context& ioc);
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