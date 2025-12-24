#include "heartbeat.h"
 
void Heartbeat::tick(std::stop_token st)
{
	if (!st.stop_requested()){
		timer_.expires_after(period_);
		timer_.async_wait([self = shared_from_this(), st](boost::system::error_code ec) {
			if (ec) return;
			self->conn_->send(json::serialize(self->j));

			self->tick(st);
			});
	}
}

Heartbeat::Heartbeat(
	const std::string uuid,
	asio::io_context& ioc, 
	std::shared_ptr<Connection> conn, 
	std::chrono::seconds period)
	:
	uuid_(uuid),
	timer_(asio::make_strand(ioc)),
	conn_(std::move(conn)),
	period_(period)
{ 
	sys_info_json["cpuName"] = getSpec().cpu_name;
	sys_info_json["gpuName"] = getSpec().gpu_name;
	sys_info_json["ramTotalMb"] = getSpec().ram;
	sys_info_json["osName"] = getSpec().os_name;
	sys_info_json["osVersion"] = getSpec().os_version;
	sys_info_json["machineUuid"] = uuid_;
	sys_info_json["hostName"] = getSpec().host_name;

	auto payload = json::serialize(sys_info_json);
	json::value v = json::parse(payload);
	const json::object& o = v.as_object();

	j["prefix"] = "HEARTBEAT";
	j["commandId"] = "N/A";
	j["taskType"] = "HEARTBEAT";
	j["payload"] = payload;
	j["machineUuid"] = uuid_;

}
Heartbeat::~Heartbeat()
{
	stop();
}

void Heartbeat::start()
{
	if (t_.joinable()) return;

	t_ = std::jthread([this](std::stop_token st) {
		tick(st);
		});
}

void Heartbeat::stop()
{
	if (t_.joinable()) {
		t_.request_stop();
		t_ = std::jthread();
	}
}
