#include "pch.hpp"
#include "connection.h"
class MessageHandler {
private:
	std::jthread t_;
	std::shared_ptr<Connection> conn_;
public:
	MessageHandler(std::shared_ptr<Connection> conn);
	~MessageHandler();
	void start();
	void stop();
	void loop(std::stop_token st);
};