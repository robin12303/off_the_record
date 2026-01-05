#pragma once
#include "pch.hpp"

// Connection:
// - Boost.Asio + Beast(WebSocket) 기반 비동기 연결/송수신 래퍼
// - start(): resolve/connect/handshake 후 read 루프 시작
// - send(): thread-safe하게 strand_로 직렬화하여 outq_에 쌓고 async_write로 순차 전송
//
// 설계 포인트:
// - enable_shared_from_this: 비동기 콜백에서 self 생존 보장(shared_from_this() 캡처)
// - strand_: ws_ / resolver_ / outq_ / open_ 같은 공유 상태 접근을 직렬화하여 데이터 레이스 방지

class Connection : public std::enable_shared_from_this<Connection> {
public:
	// io_context executor 위에 strand를 만들고,
	// resolver/ws를 그 strand에 바인딩하여 콜백이 직렬 실행되게 한다.
	explicit Connection(asio::io_context& ioc);

	// 서버 접속 시작:
	// 1) async_resolve(host,port)
	// 2) async_connect
	// 3) async_handshake(target)
	// 4) do_read() 루프 시작 + send()로 쌓인 outq_가 있으면 do_write() 시작
	void start(std::string host, std::string port, std::string target);

	// 메시지 전송 요청:
	// - 호출 스레드가 어디든 안전하게 strand_에 post
	// - outq_에 push하고, open_ 상태 & 큐가 0->1 된 순간에만 do_write 시작
	// - 실제 전송은 async_write로 수행(호출자 블로킹 없음)
	void send(std::string msg);

	// websocket stream 접근자 (외부에서 필요 시 사용)
	// ⚠️ 외부에서 ws_를 직접 만지면 strand 규칙이 깨질 수 있으니 주의.
	websocket::stream<tcp::socket>& ws();

	// 이 Connection이 동작하는 executor 제공
	// (외부에서 post/dispatch 등을 걸 때 사용 가능)
	asio::any_io_executor get_executor();

private:
	// outq_의 front를 async_write로 전송하고,
	// 완료되면 pop_front 후 남은 항목을 이어서 전송(순차 처리)
	void do_write();

	// async_read 루프:
	// - 메시지 수신 -> 문자열 변환 -> recv_q에 push -> recv_sem.release()
	// - 다시 do_read() 호출로 계속 수신
	void do_read();

	// 에러 처리(현재는 로그만)
	// 보통 여기서 open_=false 설정, 재연결 로직, outq_ 정책 등을 붙인다.
	void fail(const char* what, beast::error_code ec);

	// strand:
	// - 이 executor 위에서 실행되는 핸들러들이 서로 겹치지 않게 직렬화
	asio::strand<asio::any_io_executor> strand_;

	// DNS resolver (strand에 바인딩되어 비동기 콜백이 직렬 실행됨)
	tcp::resolver resolver_;

	// WebSocket stream (TCP socket + WebSocket 계층)
	websocket::stream<tcp::socket> ws_;

	// 수신 버퍼 (async_read가 여기에 데이터를 쌓음)
	beast::flat_buffer inbuf_;

	// 접속 정보
	std::string host_, port_, target_;

	// 송신 대기 큐:
	// - send()가 push
	// - do_write()가 front를 전송하고 pop_front
	// ⚠️ 연결이 안 열릴 때도 계속 쌓일 수 있으니 큐 제한 정책 고려 가능
	std::deque<std::string> outq_;

	// handshake 완료 여부
	// ⚠️ open_은 strand 안에서만 읽/쓰도록 하는 게 안전함(atomic으로 두기보다 규칙으로 막기)
	bool open_ = false;
};
