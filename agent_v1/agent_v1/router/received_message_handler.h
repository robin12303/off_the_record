#pragma once
#include "pch.hpp"
#include "connection.h"

// MessageHandler:
// - Connection::do_read()가 recv_q에 넣은 "수신 메시지"를 소비(consume)하는 워커
// - recv_sem.acquire()로 메시지 도착을 기다렸다가
// - recv_q에서 pop → JSON parse → 명령(prefix/taskType 등) 처리
// - 필요 시 상태(metric_running 등)를 변경하고 conn_->send()로 응답 전송
//
// 설계 포인트:
// - std::jthread로 별도 스레드에서 loop() 실행 (stop_token 기반 중단)
// - conn_은 메시지 응답 전송에 사용
// - uuid_는 이 에이전트 식별자(응답 메시지에 포함)

class MessageHandler {
private:
    // 메시지 처리 워커 스레드
    std::jthread t_;

    // 서버로 응답을 보내기 위한 연결 객체
    std::shared_ptr<Connection> conn_;

    // 이 에이전트 UUID (값으로 보관: 참조보다 수명 안전)
    const std::string uuid_;

public:
    // uuid: 에이전트 식별자
    // conn: 응답 전송에 사용할 Connection
    MessageHandler(const std::string& uuid, std::shared_ptr<Connection> conn);

    ~MessageHandler();

    // 워커 시작:
    // - 이미 실행 중이면 중복 시작 방지
    // - jthread로 loop() 실행
    void start();

    // 워커 중단:
    // - request_stop() 후 join 유도
    // ⚠️ loop가 recv_sem.acquire()에서 블로킹 중이면
    //   stop_token만으로는 즉시 깨어나지 못해 stop이 지연/정지될 수 있음.
    //   (종료 시 recv_sem.release()로 깨우거나 try_acquire_for(...)로 타임아웃 대기가 더 안전)
    void stop();

    // 메인 루프:
    // - recv_sem.acquire()로 메시지 도착 대기
    // - recv_q에서 pop
    // - 명령 처리 및 필요 시 응답 전송
    void loop(std::stop_token st);
};
