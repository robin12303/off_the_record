#include "ws_heartbeat.h"  


WsHeartbeat::WsHeartbeat(asio::io_context& ioc, 
    std::shared_ptr<WsConnection> conn, 
    std::string spec_str,
    std::chrono::seconds period)
    : timer_(asio::make_strand(ioc)),
    conn_(std::move(conn)), 
    period_(period) {
    
    j["prefix"] = "HEARTBEAT";
    j["commandId"] = "N/A";
    j["taskType"] = "HEARTBEAT";
    j["payload"] = spec_str;
    boost::system::error_code ec;
    json::value v = json::parse(spec_str);      //  문자열 전체를 JSON으로 "만듦"(파싱)
    const json::object& o = v.as_object();
    j["machineGuid"] = o.at("machineGuid").as_string();
} 

void WsHeartbeat::start()
{
	tick();
}

void WsHeartbeat::tick()
{
    timer_.expires_after(period_);
    timer_.async_wait([self = shared_from_this()](boost::system::error_code ec) {
        if (ec) return;

        json::object jj = self->j;                // 멤버 복사
        jj["timestamp"] = getCurrentTimestamp();  // 지금 시각
        self->conn_->send(json::serialize(jj));

        self->tick();
        });
}
