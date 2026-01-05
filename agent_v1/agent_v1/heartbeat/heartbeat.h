#pragma once
#include "pch.hpp"
#include "connection.h"

// Heartbeat:
// - 일정 주기(period_)마다 "HEARTBEAT" 메시지를 서버로 전송하는 컴포넌트
// - 내부적으로 asio::steady_timer + async_wait를 사용해 타이머 콜백 기반으로 반복 전송
// - shared_from_this()를 사용하므로 반드시 std::shared_ptr로 관리되어야 안전함.
//
// 주의(설계 포인트):
// - stop_token만으로는 async_wait을 즉시 깨우지 못할 수 있으므로,
//   stop()에서 timer_.cancel() 같은 취소 처리가 필요할 수 있음(구현에서).
// - t_ 스레드는 tick()을 시작만 걸고, 실제 콜백 실행은 io_context(run) 스레드에서 일어남.

class Heartbeat : public std::enable_shared_from_this<Heartbeat>
{
private:
    // heartbeat 전송 주기 (예: 5초)
    std::chrono::seconds period_;

    // 비동기 타이머
    // - expires_after(period_) 후 async_wait 콜백 실행
    asio::steady_timer timer_;

    // start()/stop()를 위한 실행 스레드
    // - tick() 시작 트리거 역할
    // - std::jthread이므로 stop_token 지원 + 소멸 시 join
    std::jthread t_;

    // 전송에 사용할 Connection(WebSocket 등)
    std::shared_ptr<Connection> conn_;

    // 실제로 전송할 HEARTBEAT 메시지(JSON)
    // - prefix/commandId/taskType/machineUuid/payload 등을 담는 구조
    json::object j;

    // 시스템 정보 스냅샷(JSON)
    // - CPU/GPU/RAM/OS/hostname 등
    json::object sys_info_json;

    // 이 에이전트(머신) UUID
    // - 한번 정해지면 변하지 않도록 const
    const std::string uuid_;

    // 타이머를 재설정하고 async_wait로 자신을 다시 호출하는 반복 루프
    // - stop_token으로 종료 의사를 확인
    void tick(std::stop_token st);

public:
    // uuid: 에이전트 식별자
    // ioc: 타이머/비동기 콜백이 실행될 io_context
    // conn: 메시지 전송에 사용할 연결 객체
    // period: heartbeat 주기
    Heartbeat(
        const std::string uuid,          // ⚠️ 값 복사됨(필요하면 const std::string&로 받는 것도 가능)
        asio::io_context& ioc,
        std::shared_ptr<Connection> conn,
        std::chrono::seconds period
    );

    ~Heartbeat();

    // 복사/이동 방지:
    // - timer/스레드/connection 같은 자원은 복사/이동이 꼬이기 쉬우므로 금지
    Heartbeat(const Heartbeat&) = delete;
    Heartbeat& operator=(const Heartbeat&) = delete;
    Heartbeat(Heartbeat&&) = delete;
    Heartbeat& operator=(Heartbeat&&) = delete;

    // heartbeat 시작:
    // - 내부 스레드(t_)를 만들어 tick()을 시작시킴
    void start();

    // heartbeat 중단:
    // - t_에 stop 요청
    // - 필요시 timer_.cancel()로 대기 중 async_wait을 깨워야 깔끔하게 종료 가능
    void stop();
};
