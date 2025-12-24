#include "pch.hpp"
#include "connection.h"
class MetricConsumer : public std::enable_shared_from_this<MetricConsumer> {
private:
	std::jthread t_;
	std::shared_ptr<Connection> conn_;
	const std::string& uuid_;
	void loop(std::stop_token st);
public:
	MetricConsumer(const std::string& uuid,std::shared_ptr<Connection> conn);
	~MetricConsumer();
	// 복사/이동 방지

	MetricConsumer(const MetricConsumer&) = delete;
	MetricConsumer& operator=(const MetricConsumer&) = delete;
	MetricConsumer(MetricConsumer&&) = delete;
	MetricConsumer& operator=(MetricConsumer&&) = delete;

	void start();
	void stop();
};