#pragma once
#include "pch.hpp"
class MetricProducer {
private:
	std::jthread t_;

	void loop(std::stop_token st);
public:
	MetricProducer();
	~MetricProducer();
	// 복사/이동 방지

	MetricProducer(const MetricProducer&) = delete;
	MetricProducer& operator=(const MetricProducer&) = delete;
	MetricProducer(MetricProducer&&) = delete;
	MetricProducer& operator=(MetricProducer&&) = delete;

	void start();
	void stop();
};