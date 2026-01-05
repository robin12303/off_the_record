#pragma once
#include "pch.hpp"

// MetricProducer:
// - 일정 주기(윈도우)마다 현재까지 누적된 메트릭(예: g_keystrokes)을 읽어서
// - metric_q(공유 큐)에 payload를 push하고 metric_sem.release()로 소비자에게 알리는 생산자(producer) 워커.
// - 내부적으로 std::jthread를 사용해 별도 스레드에서 loop()를 실행한다.
//
// 설계 포인트:
// - loop()는 보통 sleep_until / 주기 타이밍을 사용하므로 stop() 호출 시 즉시 멈추지 않을 수 있음.
// - stop_token은 “중단 의사”만 전달하고, sleep_until 같은 대기를 즉시 깨우지는 못할 수 있다.

class MetricProducer {
private:
    // 생산자 워커 스레드
    // - start()에서 생성, stop()/소멸에서 정리
    std::jthread t_;

    // 메인 루프:
    // - stop_token을 확인하며 주기적으로 실행
    // - 카운터/지표를 수집해 metric_q에 push
    // - metric_sem.release()로 소비자에게 알림
    void loop(std::stop_token st);

public:
    MetricProducer();
    ~MetricProducer();

    // 복사/이동 방지:
    // - 스레드 소유 객체는 복사/이동을 허용하면 수명/동기화가 꼬이기 쉬워서 금지
    MetricProducer(const MetricProducer&) = delete;
    MetricProducer& operator=(const MetricProducer&) = delete;
    MetricProducer(MetricProducer&&) = delete;
    MetricProducer& operator=(MetricProducer&&) = delete;

    // 생산자 시작:
    // - 이미 실행 중이면 무시
    // - jthread로 loop() 실행
    void start();

    // 생산자 중단:
    // - request_stop()로 중단 요청 후 join 유도
    // ⚠️ loop 내부가 sleep_until 같은 블로킹 대기 중이면
    //   stop()이 최대 주기(window)만큼 지연될 수 있음.
    void stop();
};
