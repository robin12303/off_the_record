#pragma once
#include "pch.hpp"
#include "connection.h"

// MetricConsumer:
// - metric_q / metric_sem(전역/공유 큐)에서 메트릭 payload를 꺼내서
// - Connection을 통해 서버로 전송하는 소비자(consumer) 워커.
//
// 설계 포인트:
// - std::jthread로 별도 스레드에서 loop() 실행 (stop_token으로 중단 요청)
// - enable_shared_from_this: 비동기 콜백/지연 실행에서 객체 생존을 보장하고 싶을 때 사용
//   (단, 반드시 shared_ptr로 생성/관리되어야 shared_from_this()가 안전함)

class MetricConsumer : public std::enable_shared_from_this<MetricConsumer> {
private:
    // 소비자 워커 스레드
    // - start()에서 생성, stop()/소멸에서 정리
    std::jthread t_;

    // 서버 전송에 사용할 연결 객체(WebSocket)
    std::shared_ptr<Connection> conn_;

    // 에이전트 UUID
    // ⚠️ 매우 중요:
    //   const std::string& 는 "참조"라서, 이 MetricConsumer보다 uuid 원본이 먼저 죽으면
    //   uuid_가 댕글링 레퍼런스(Use-After-Free)로 터진다.
    //   안전하게 가려면 보통 `std::string uuid_;`로 "값 복사" 보관이 정석.
    const std::string& uuid_;

    // 메인 루프:
    // - metric_sem.acquire()로 아이템 대기
    // - metric_q에서 pop
    // - 메시지 구성 후 conn_->send() 호출
    void loop(std::stop_token st);

public:
    // uuid: 머신/에이전트 식별자
    // conn: 전송에 사용할 Connection
    MetricConsumer(const std::string& uuid, std::shared_ptr<Connection> conn);

    ~MetricConsumer();

    // 복사/이동 방지:
    // - 스레드/연결 같은 자원은 복사/이동 시 수명/동기화가 꼬이기 쉬워서 금지
    MetricConsumer(const MetricConsumer&) = delete;
    MetricConsumer& operator=(const MetricConsumer&) = delete;
    MetricConsumer(MetricConsumer&&) = delete;
    MetricConsumer& operator=(MetricConsumer&&) = delete;

    // 워커 시작:
    // - 이미 실행 중이면 무시
    // - jthread로 loop() 실행
    void start();

    // 워커 중단:
    // - request_stop() 후 join(소멸) 유도
    // ⚠️ loop가 metric_sem.acquire()에서 블로킹 중이면,
    //   stop_token만으로는 즉시 깨어나지 못해 종료가 지연/정지될 수 있음.
    //   (종료 시 metric_sem.release()로 깨우거나, try_acquire_for(...)로 폴링하는 설계가 더 안전)
    void stop();
};
