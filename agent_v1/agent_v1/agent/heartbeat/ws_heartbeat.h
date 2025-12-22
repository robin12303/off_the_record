#include "WsConnection.h"
#include "spec.h"
#include "CurrentTimeStamp.h"
#include <boost/json.hpp>
namespace asio = boost::asio;
using namespace std::chrono_literals;
namespace json = boost::json;
class WsHeartbeat : public std::enable_shared_from_this<WsHeartbeat> {
public:
	WsHeartbeat(
		asio::io_context& ioc, 
		std::shared_ptr<WsConnection> conn,
		std::string spec_str,
		std::chrono::seconds period);

	void start();
private:
	void tick();
	json::object j;
	asio::steady_timer timer_;
	std::shared_ptr<WsConnection> conn_; 
	std::chrono::seconds period_;
	 
};