#include "pch.hpp"
#include "connection.h"
class MessageHandler {
private:
	std::jthread t_;
	std::shared_ptr<Connection> conn_;
	const std::string uuid_;
public:
	MessageHandler(const std::string& uuid, std::shared_ptr<Connection> conn);
	~MessageHandler();
	void start();
	void stop();
	void loop(std::stop_token st);
};